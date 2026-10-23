package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 按原版方块坐标上传区段光场；GPU 在最终单位位置采样，CPU 不查询逐个单位。
public class HordeLightField implements AutoCloseable {
	private static final int TILE_WIDTH = 64;
	private static final int TILE_HEIGHT = 16;
	private static final int TILES_PER_ROW = 4;
	private static final int UPDATE_TICKS = 5;
	private static final byte[] EMPTY = new byte[0];
	private final Map<Section, Slot> slots = new HashMap<>();
	private final List<Integer> free = new ArrayList<>();
	private DynamicTexture atlas;
	private HordeDataTexture lookup;
	private int capacity;
	private int nextSlot;
	private int mask;
	private long frame;
	private boolean mappingDirty = true;
	private Set<Section> previous = Set.of();
	private List<HordeClient.Block> cachedBlocks = List.of();
	private List<Section> orderedSections = List.of();
	private long preparedTick = Long.MIN_VALUE;

	public void prepare(List<HordeClient.Batch> batches) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		frame++;
		var blocks = batches.stream().map(HordeClient.Batch::block).distinct().toList();
		boolean refresh = preparedTick != level.getGameTime() || !blocks.equals(cachedBlocks);
		Set<Section> required = refresh ? requiredSections(blocks) : previous;
		int maximumSlots = RenderSystem.getDevice().getMaxTextureSize() / TILE_HEIGHT * TILES_PER_ROW;
		if (required.size() > maximumSlots) {
			throw new IllegalArgumentException("群体单批光照区段超过设备纹理容量");
		}
		cachedBlocks = blocks;
		preparedTick = level.getGameTime();
		long additions = required.stream().filter(section -> !slots.containsKey(section)).count();
		boolean needsSlots = slots.size() + additions > maximumSlots;
		if (!required.equals(previous)) {
			mappingDirty = true;
			previous = Set.copyOf(required);
			orderedSections = required.stream().sorted(Comparator.comparingInt(Section::x).thenComparingInt(Section::y)
					.thenComparingInt(Section::z)).toList();
		}
		var iterator = slots.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (!required.contains(entry.getKey()) && (needsSlots || frame - entry.getValue().seen > 120)) {
				free.add(entry.getValue().index);
				iterator.remove();
				mappingDirty = true;
			}
		}
		var ordered = orderedSections;
		for (var section : ordered) {
			var slot = slots.get(section);
			if (slot == null) {
				int index = free.isEmpty() ? nextSlot++ : free.removeLast();
				slot = new Slot(index);
				slots.put(section, slot);
				mappingDirty = true;
			}
			slot.seen = frame;
		}
		ensureAtlas(Math.max(1, nextSlot));
		if (mappingDirty) {
			rebuildLookup();
		}
		var block = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
		var sky = level.getLightEngine().getLayerListener(LightLayer.SKY);
		for (var section : ordered) {
			var slot = slots.get(section);
			if (level.getGameTime() - slot.updated >= UPDATE_TICKS) {
				updateSection(section, slot, block, sky);
				slot.updated = level.getGameTime();
			}
		}
	}

	private Set<Section> requiredSections(List<HordeClient.Block> blocks) {
		var level = Minecraft.getInstance().level;
		var required = new HashSet<Section>();
		if (level == null) {
			return required;
		}
		for (var block : blocks) {
			AABB bounds = block.bounds();
			int minY = Math.max((int) Math.floor(bounds.minY) >> 4, level.getLightEngine().getMinLightSection());
			int maxY = Math.min((int) Math.floor(bounds.maxY) >> 4, level.getLightEngine().getMaxLightSection() - 1);
			for (int x = (int) Math.floor(bounds.minX) >> 4; x <= (int) Math.floor(bounds.maxX) >> 4; x++) {
				for (int z = (int) Math.floor(bounds.minZ) >> 4; z <= (int) Math.floor(bounds.maxZ) >> 4; z++) {
					if (level.getChunkSource().getChunkNow(x, z) == null) {
						continue;
					}
					for (int y = minY; y <= maxY; y++) {
						required.add(new Section(x, y, z));
					}
				}
			}
		}
		return required;
	}

	private void updateSection(Section section, Slot slot, LayerLightEventListener block, LayerLightEventListener sky) {
		var position = SectionPos.of(section.x(), section.y(), section.z());
		var blockLayer = block.getDataLayerData(position);
		var skyLayer = sky.getDataLayerData(position);
		byte[] blockData = blockLayer == null ? EMPTY : blockLayer.getData();
		byte[] skyData;
		if (skyLayer != null) {
			skyData = skyLayer.getData();
		} else {
			// 原版缺省天空光沿竖列从上方区段取得，不能把没有 DataLayer 当成零。
			skyData = new byte[256];
			var probe = new BlockPos.MutableBlockPos();
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					probe.set(section.x() * 16 + x, section.y() * 16, section.z() * 16 + z);
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
			atlas.close();
		}
		atlas = replacement;
		capacity = size;
		slots.values().forEach(slot -> {
			slot.updated = Long.MIN_VALUE / 2;
			slot.uploaded = false;
		});
	}

	private void rebuildLookup() {
		int count = 1;
		while (count < Math.max(2, previous.size() * 2)) {
			count <<= 1;
		}
		mask = count - 1;
		var data = new float[count * 4];
		for (var entry : slots.entrySet()) {
			if (entry.getValue().seen != frame) {
				continue;
			}
			var section = entry.getKey();
			int at = hash(section.x(), section.y(), section.z()) & mask;
			while (data[at * 4 + 3] != 0) {
				at = (at + 1) & mask;
			}
			data[at * 4] = section.x();
			data[at * 4 + 1] = section.y();
			data[at * 4 + 2] = section.z();
			data[at * 4 + 3] = entry.getValue().index + 1;
		}
		var replacement = new HordeDataTexture("Horde light section index", data);
		if (lookup != null) {
			lookup.close();
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

	public int sections() {
		return slots.size();
	}

	@Override
	public void close() {
		if (atlas != null) {
			atlas.close();
			atlas = null;
		}
		if (lookup != null) {
			lookup.close();
			lookup = null;
		}
		slots.clear();
		free.clear();
		previous = Set.of();
		cachedBlocks = List.of();
		orderedSections = List.of();
		preparedTick = Long.MIN_VALUE;
		nextSlot = 0;
		capacity = 0;
		mappingDirty = true;
	}

	private record Section(int x, int y, int z) {
	}

	private static class Slot {
		private final int index;
		private long seen;
		private long updated = Long.MIN_VALUE / 2;
		private byte[] block;
		private byte[] sky;
		private boolean uploaded;

		private Slot(int index) {
			this.index = index;
		}
	}
}
