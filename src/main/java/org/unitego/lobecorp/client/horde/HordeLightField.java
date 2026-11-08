package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.LightLayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.Set;

/// 按原版方块坐标上传区段光场；GPU 在最终单位位置采样，CPU 不查询逐个单位。
public class HordeLightField implements AutoCloseable {
	private static final int TILE_WIDTH = 64;
	private static final int TILE_HEIGHT = 16;
	private static final int TILES_PER_ROW = 4;
	private static final int UPDATE_TICKS = 5;
	private static final byte[] EMPTY = new byte[0];
	/// 非当前批次的光照槽位保留 120 个绘制周期，供后续批次复用。
	private static final int RETAIN_FRAMES = 120;
	private final Long2ObjectOpenHashMap<Slot> slots = new Long2ObjectOpenHashMap<>();
	private final HordeLightCoverage coverage = new HordeLightCoverage();
	private static final int MAX_LOOKUPS = 8;
	private final LinkedHashMap<LongSet, Lookup> lookups = new LinkedHashMap<>(16, 0.75f, true);
	private final List<HordeClient.Block> inputBlocks = new ArrayList<>();
	private final Set<HordeClient.Block> distinctBlocks = new HashSet<>();
	private long slotVersion;
	private final List<Integer> free = new ArrayList<>();
	private DynamicTexture atlas;
	private HordeDataTexture lookup;
	private int capacity;
	private int nextSlot;
	private int mask;
	private long frame;
	private boolean mappingDirty = true;
	private final LongSet required = new LongOpenHashSet();
	private LongSet previous = LongSets.emptySet();
	private List<HordeClient.Block> cachedInputs = List.of();
	private List<HordeClient.Block> cachedBlocks = List.of();
	private List<HordeClient.Block> preparedBlocks = List.of();
	private long[] orderedSections = LongArrays.EMPTY_ARRAY;
	private long preparedTick = Long.MIN_VALUE;
	private long nextEvictionFrame = Long.MAX_VALUE;

	public void prepare(List<HordeClient.Batch> batches) {
		prepare(batches, HordeClient.Batch::block);
	}

	public <T> void prepare(List<T> batches, Function<T, HordeClient.Block> blockOf) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		frame++;
		inputBlocks.clear();
		for (var batch : batches) {
			inputBlocks.add(blockOf.apply(batch));
		}

		boolean inputsChanged = !inputBlocks.equals(cachedInputs);
		if (inputsChanged) {
			cachedInputs = List.copyOf(inputBlocks);
			distinctBlocks.clear();
			var blocks = new ArrayList<HordeClient.Block>();
			for (var block : inputBlocks) {
				if (distinctBlocks.add(block)) {
					blocks.add(block);
				}
			}

			cachedBlocks = List.copyOf(blocks);
		}

		long tick = level.getGameTime();
		boolean refresh = preparedTick != tick || !cachedBlocks.equals(preparedBlocks);
		var required = refresh ? requiredSections(cachedBlocks) : previous;
		int maximumSlots = RenderSystem.getDevice().getMaxTextureSize() / TILE_HEIGHT * TILES_PER_ROW;
		if (required.size() > maximumSlots) {
			throw new IllegalArgumentException("群体单批光照区段超过设备纹理容量");
		}

		preparedTick = tick;
		preparedBlocks = cachedBlocks;
		boolean sectionsChanged = !required.equals(previous);
		if (sectionsChanged) {
			previous = LongSets.unmodifiable(new LongOpenHashSet(required));
			orderedSections = previous.toLongArray();
			LongArrays.quickSort(orderedSections, HordeLightField::compareSections);
			mappingDirty = true;
		}

		if (sectionsChanged || frame >= nextEvictionFrame || slots.size() > maximumSlots) {
			updateSlots(required, maximumSlots);
		}

		ensureAtlas(Math.max(1, nextSlot));
		if (mappingDirty) {
			rebuildLookup();
		}

		var block = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
		var sky = level.getLightEngine().getLayerListener(LightLayer.SKY);
		for (long section : orderedSections) {
			var slot = slots.get(section);
			slot.seen = frame;
			if (tick - slot.updated >= UPDATE_TICKS) {
				updateSection(section, slot, block, sky);
				slot.updated = tick;
			}
		}
	}

	private void updateSlots(LongSet required, int maximumSlots) {
		int additions = 0;
		for (var iterator = required.iterator(); iterator.hasNext(); ) {
			if (!slots.containsKey(iterator.nextLong())) {
				additions++;
			}
		}

		boolean needsSlots = slots.size() + additions > maximumSlots;
		nextEvictionFrame = Long.MAX_VALUE;
		var iterator = slots.long2ObjectEntrySet().fastIterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (required.contains(entry.getLongKey())) {
				continue;
			}

			var slot = entry.getValue();
			if (needsSlots || frame - slot.seen > RETAIN_FRAMES) {
				free.add(slot.index);
				iterator.remove();
				slotVersion++;
				mappingDirty = true;
			} else {
				nextEvictionFrame = Math.min(nextEvictionFrame, slot.seen + RETAIN_FRAMES + 1);
			}
		}

		for (long section : orderedSections) {
			if (slots.containsKey(section)) {
				continue;
			}

			int index = free.isEmpty() ? nextSlot++ : free.removeLast();
			slots.put(section, new Slot(index));
			slotVersion++;
			mappingDirty = true;
		}
	}

	private static int compareSections(long first, long second) {
		int x = Integer.compare(SectionPos.x(first), SectionPos.x(second));
		if (x != 0) {
			return x;
		}

		int y = Integer.compare(SectionPos.y(first), SectionPos.y(second));
		return y != 0 ? y : Integer.compare(SectionPos.z(first), SectionPos.z(second));
	}

	private LongSet requiredSections(List<HordeClient.Block> blocks) {
		var level = Minecraft.getInstance().level;
		required.clear();
		if (level == null) {
			return required;
		}
		long tick = level.getGameTime();
		long buildsBefore = coverage.builds();
		long queriesBefore = coverage.queries();
		for (var block : blocks) {
			for (long key : coverage.candidates(block.bounds(), level.getLightEngine().getMinLightSection(),
					level.getLightEngine().getMaxLightSection(), tick)) {
				if (coverage.loaded(key, tick, column -> level.getChunkSource().getChunkNow(
						SectionPos.x(column), SectionPos.z(column)) != null)) {
					required.add(key);
				}
			}
		}
		HordeRenderMetrics.add(HordeRenderMetrics.Counter.LIGHT_COVERAGE_BUILDS, coverage.builds() - buildsBefore);
		HordeRenderMetrics.add(HordeRenderMetrics.Counter.LIGHT_COLUMN_QUERIES, coverage.queries() - queriesBefore);
		return required;
	}

	private void updateSection(long section, Slot slot, LayerLightEventListener block, LayerLightEventListener sky) {
		var position = SectionPos.of(section);
		var blockLayer = block.getDataLayerData(position);
		var skyLayer = sky.getDataLayerData(position);
		byte[] blockData = blockLayer == null ? EMPTY : blockLayer.getData();
		byte[] skyData;
		if (skyLayer != null) {
			skyData = skyLayer.getData();
		} else {
			// 原版缺省天空光沿竖列从上方区段取得，不能把没有 DataLayer 当成零。
			skyData = slot.skyFallback;
			var probe = new BlockPos.MutableBlockPos();
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					probe.set(SectionPos.x(section) * 16 + x, SectionPos.y(section) * 16, SectionPos.z(section) * 16 + z);
					skyData[z * 16 + x] = (byte) sky.getLightValue(probe);
				}
			}
		}
		if (slot.uploaded && Arrays.equals(slot.block, blockData) && Arrays.equals(slot.sky, skyData)) {
			return;
		}

		int tileX = slot.index % TILES_PER_ROW * TILE_WIDTH;
		int tileY = slot.index / TILES_PER_ROW * TILE_HEIGHT;
		var pixels = atlas.getPixels();
		for (int pixel = 0; pixel < 1024; pixel++) {
			int rgba = 0;
			for (int channel = 0; channel < 4; channel++) {
				int index = pixel * 4 + channel;
				int blockLight = nibble(blockData, index);
				int skyLight = skyData.length == 256 ? skyData[index & 255] : nibble(skyData, index);
				rgba |= (blockLight | skyLight << 4) << (channel * 8);
			}
			pixels.setPixelABGR(tileX + pixel % TILE_WIDTH, tileY + pixel / TILE_WIDTH, rgba);
		}
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(atlas.getTexture(), pixels,
				0, 0, tileX, tileY, TILE_WIDTH, TILE_HEIGHT, tileX, tileY);
		slot.block = blockData.clone();
		slot.sky = skyData.clone();
		slot.uploaded = true;
	}

	private static int nibble(byte[] values, int index) {
		return values.length == 0 ? 0 : values[index >>> 1] >>> ((index & 1) * 4) & 15;
	}

	private void ensureAtlas(int count) {
		if (atlas != null && count <= capacity) {
			return;
		}
		int maximumSlots = RenderSystem.getDevice().getMaxTextureSize() / TILE_HEIGHT * TILES_PER_ROW;
		int size = Math.min(maximumSlots, Integer.highestOneBit(Math.max(1, count - 1)) << 1);
		int height = Math.ceilDiv(size, TILES_PER_ROW) * TILE_HEIGHT;
		if (height > RenderSystem.getDevice().getMaxTextureSize()) {
			throw new IllegalArgumentException("群体光照区段超过设备纹理容量");
		}
		var replacement = new DynamicTexture("Horde block and sky light", TILE_WIDTH * TILES_PER_ROW, height, true);
		if (atlas != null) {
			// 槽位坐标在扩容时不变，保留已有光值，避免全部区段再次查询天空光。
			var oldPixels = atlas.getPixels();
			oldPixels.copyRect(replacement.getPixels(), 0, 0, 0, 0,
					oldPixels.getWidth(), oldPixels.getHeight(), false, false);
			replacement.upload();
			atlas.close();
		}
		atlas = replacement;
		capacity = size;
	}

	private void rebuildLookup() {
		var cached = lookups.get(previous);
		if (cached != null && cached.version() == slotVersion) {
			lookup = cached.texture();
			mask = cached.mask();
			mappingDirty = false;
			return;
		}
		int count = 1;
		while (count < Math.max(2, previous.size() * 2)) {
			count <<= 1;
		}
		mask = count - 1;
		var data = new float[count * 4];
		for (long section : orderedSections) {
			var slot = slots.get(section);
			int x = SectionPos.x(section);
			int y = SectionPos.y(section);
			int z = SectionPos.z(section);
			int at = hash(x, y, z) & mask;
			while (data[at * 4 + 3] != 0) {
				at = (at + 1) & mask;
			}
			data[at * 4] = x;
			data[at * 4 + 1] = y;
			data[at * 4 + 2] = z;
			data[at * 4 + 3] = slot.index + 1;
		}
		var replacement = new HordeDataTexture("Horde light section index", data);
		var old = lookups.put(previous, new Lookup(replacement, mask, slotVersion));
		if (old != null) {
			old.texture().close();
		}
		while (lookups.size() > MAX_LOOKUPS) {
			var iterator = lookups.entrySet().iterator();
			iterator.next().getValue().texture().close();
			iterator.remove();
		}
		lookup = replacement;
		mappingDirty = false;
	}

	private static int hash(int x, int y, int z) {
		return x * 73856093 ^ y * 19349663 ^ z * 83492791;
	}

	public DynamicTexture atlas() {
		return atlas;
	}

	public DynamicTexture lookup() {
		return lookup.texture();
	}

	public int mask() {
		return mask;
	}

	public int activeSections() {
		return previous.size();
	}

	public int sections() {
		return slots.size();
	}

	@Override
	public void close() {
		if (atlas != null) {
			atlas.close();
			atlas = null;
		}
		lookups.values().forEach(cached -> cached.texture().close());
		lookups.clear();
		lookup = null;
		inputBlocks.clear();
		distinctBlocks.clear();
		slotVersion = 0;
		slots.clear();
		coverage.clear();
		free.clear();
		required.clear();
		previous = LongSets.emptySet();
		cachedInputs = List.of();
		cachedBlocks = List.of();
		preparedBlocks = List.of();
		orderedSections = LongArrays.EMPTY_ARRAY;
		preparedTick = Long.MIN_VALUE;
		nextEvictionFrame = Long.MAX_VALUE;
		nextSlot = 0;
		capacity = 0;
		mappingDirty = true;
	}

	private record Lookup(HordeDataTexture texture, int mask, long version) {
	}

	private static class Slot {
		private final int index;
		private long seen;
		private long updated = Long.MIN_VALUE / 2;
        private final byte[] skyFallback = new byte[256];
		private byte[] block;
		private byte[] sky;
		private boolean uploaded;

		private Slot(int index) {
			this.index = index;
		}
	}
}
