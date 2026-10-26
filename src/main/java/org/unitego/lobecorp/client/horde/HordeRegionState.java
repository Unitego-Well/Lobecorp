package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/// CPU只提交事件局部目标与稳定身份；中心、半径及速度的持续状态全部留在GPU。
public class HordeRegionState implements AutoCloseable {
	private static final int STATE_WORDS = 13;
	private static final int ROW_WIDTH = 1024;
	private static final int PARAMETER_BYTES = 48;
	private static final float RESPONSE_OMEGA = 10;
	private static final RenderPipeline UPDATE = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_region_update"))
			.withVertexShader(Lobecorp.id("core/horde_motion"))
			.withFragmentShader(Lobecorp.id("core/horde_region_update"))
			.withSampler("RegionTargets").withSampler("RegionPrevious")
			.withUniform("RegionParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
	private final Targets targets = new Targets();
	private HordeDataTexture raw;
	private int rawCapacity;
	private GpuTexture read;
	private GpuTexture write;
	private GpuTextureView readView;
	private GpuTextureView writeView;
	private GpuBuffer parameters;
	private ByteBuffer memory;
	private int width;
	private int height;
	private long version = Long.MIN_VALUE;
	private double lastTime = Double.NaN;

	public static boolean precompile() {
		return RenderSystem.getDevice().precompilePipeline(UPDATE).isValid();
	}

	public void update(HordeClient.ClientEvent event, double time) {
		boolean changed = version != event.regionVersion || targets.expired(time);
		if (changed) {
			targets.update(event.regions, time);
			int[] words = targets.words(event.origin.x, event.origin.y, event.origin.z);
			if (raw == null || words.length > rawCapacity) {
				int size = Math.multiplyExact(Integer.highestOneBit(Math.max(1, words.length - 1)), 2);
				var replacement = new HordeDataTexture("Horde stable region targets", new int[size]);
				if (raw != null) {
					raw.close();
				}
				raw = replacement;
				rawCapacity = size;
			}
			raw.update(0, words, 0, words.length);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.UPLOAD_BYTES, (long) words.length * Integer.BYTES);
			version = event.regionVersion;
		}
		if (read != null && !changed && time == lastTime) {
			return;
		}
		ensureState(Math.max(1, targets.slotCount()));
		memory.clear();
		try {
			int x = (int) Math.floor(event.origin.x);
			int y = (int) Math.floor(event.origin.y);
			int z = (int) Math.floor(event.origin.z);
			memory.putInt(x).putInt(y).putInt(z).putInt(targets.slotCount());
			float delta = Double.isNaN(lastTime) ? 0 : (float) Math.max(0, (time - lastTime) / 20);
			memory.putFloat((float) (event.origin.x - x)).putFloat((float) (event.origin.y - y))
					.putFloat((float) (event.origin.z - z)).putFloat(delta);
			memory.putFloat(RESPONSE_OMEGA).putFloat(width).putFloat(0).putFloat(0).flip();
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.writeToBuffer(parameters.slice(), memory);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.UPLOAD_BYTES, PARAMETER_BYTES);
			long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.REGIONS, targets.slotCount());
			try (var pass = encoder.createRenderPass(() -> "Horde continuous region state", writeView, OptionalInt.empty())) {
				var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
				pass.setPipeline(UPDATE);
				pass.setViewport(0, 0, width, height);
				pass.setUniform("RegionParameters", parameters.slice());
				pass.bindTexture("RegionTargets", raw.texture().getTextureView(), sampler);
				pass.bindTexture("RegionPrevious", readView, sampler);
				pass.draw(0, 3);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.REGIONS, targets.activeCount());
			} finally {
				HordeRenderMetrics.end(HordeRenderMetrics.Stage.REGIONS, started);
			}
			var texture = read;
			read = write;
			write = texture;
			var view = readView;
			readView = writeView;
			writeView = view;
			lastTime = time;
		} finally {
			memory.clear();
		}
	}

	private void ensureState(int count) {
		int targetWidth = Math.min(ROW_WIDTH, RenderSystem.getDevice().getMaxTextureSize());
		int rows = Math.ceilDiv(Math.multiplyExact(count, STATE_WORDS), targetWidth);
		if (read != null && rows <= height) {
			return;
		}
		int limit = RenderSystem.getDevice().getMaxTextureSize();
		if (rows > limit) {
			throw new IllegalArgumentException("群体分割状态超过设备纹理容量");
		}
		int targetHeight = (int) Math.min(limit, (long) Integer.highestOneBit(Math.max(1, rows - 1)) * 2);
		var device = RenderSystem.getDevice();
		int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT
				| GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;
		GpuTexture nextRead = null;
		GpuTexture nextWrite = null;
		GpuTextureView nextReadView = null;
		GpuTextureView nextWriteView = null;
		try {
			nextRead = device.createTexture(() -> "Horde region state read", usage, TextureFormat.RGBA8, targetWidth, targetHeight, 1, 1);
			nextWrite = device.createTexture(() -> "Horde region state write", usage, TextureFormat.RGBA8, targetWidth, targetHeight, 1, 1);
			nextReadView = device.createTextureView(nextRead);
			nextWriteView = device.createTextureView(nextWrite);
			var encoder = device.createCommandEncoder();
			encoder.clearColorTexture(nextRead, 0);
			encoder.clearColorTexture(nextWrite, 0);
			if (read != null) {
				encoder.copyTextureToTexture(read, nextRead, 0, 0, 0, 0, 0, width, height);
			}
			if (parameters == null) {
				parameters = device.createBuffer(() -> "Horde region state parameters",
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, PARAMETER_BYTES);
				memory = MemoryUtil.memAlloc(PARAMETER_BYTES);
			}
		} catch (RuntimeException exception) {
			if (nextReadView != null) {
				nextReadView.close();
			}
			if (nextWriteView != null) {
				nextWriteView.close();
			}
			if (nextRead != null) {
				nextRead.close();
			}
			if (nextWrite != null) {
				nextWrite.close();
			}
			throw exception;
		}
		closeState();
		read = nextRead;
		write = nextWrite;
		readView = nextReadView;
		writeView = nextWriteView;
		width = targetWidth;
		height = targetHeight;
	}

	public HordeDataTexture raw() {
		return raw;
	}

	public GpuTextureView view() {
		return readView;
	}

	public int slotCount() {
		return targets.slotCount();
	}

	public int activeCount() {
		return targets.activeCount();
	}

	private void closeState() {
		if (readView != null) {
			readView.close();
		}
		if (writeView != null) {
			writeView.close();
		}
		if (read != null) {
			read.close();
		}
		if (write != null) {
			write.close();
		}
	}

	@Override
	public void close() {
		closeState();
		if (raw != null) {
			raw.close();
		}
		if (parameters != null) {
			parameters.close();
		}
		if (memory != null) {
			MemoryUtil.memFree(memory);
		}
	}

	/// 保留退场身份两秒；六个局部浮点坐标/尺寸按原始位编码，末字仍为有符号代次。
	public static class Targets {
		private static final double RETIRE_TICKS = 40;
		private final Map<String, Slot> byKey = new HashMap<>();
		private final ArrayList<Slot> slots = new ArrayList<>();
		private final IntArrayList freeSlots = new IntArrayList();
		private double deadline = Double.POSITIVE_INFINITY;
		private int activeCount;
		private int generation;

		public boolean expired(double time) {
			return time >= deadline;
		}

		public void update(Map<String, HordeClient.RegionBox> regions, double time) {
			var iterator = byKey.entrySet().iterator();
			while (iterator.hasNext()) {
				var entry = iterator.next();
				var slot = entry.getValue();
				if (regions.containsKey(entry.getKey())) {
					slot.retireAt = Double.POSITIVE_INFINITY;
				} else if (slot.retireAt == Double.POSITIVE_INFINITY) {
					slot.retireAt = time + RETIRE_TICKS;
				} else if (time >= slot.retireAt) {
					slots.set(slot.index, null);
					freeSlots.add(slot.index);
					iterator.remove();
				}
			}
			for (var entry : regions.entrySet()) {
				var slot = byKey.get(entry.getKey());
				if (slot == null) {
					int index;
					if (freeSlots.isEmpty()) {
						index = slots.size();
						slots.add(null);
					} else {
						index = freeSlots.removeInt(freeSlots.size() - 1);
					}
					if (generation == Integer.MAX_VALUE) {
						throw new IllegalStateException("群体分割身份代次耗尽");
					}
					slot = new Slot(index, ++generation, entry.getValue());
					slots.set(index, slot);
					byKey.put(entry.getKey(), slot);
				}
				slot.box = entry.getValue();
			}
			deadline = Double.POSITIVE_INFINITY;
			for (var slot : byKey.values()) {
				deadline = Math.min(deadline, slot.retireAt);
			}
			activeCount = byKey.size();
		}

		public int slotCount() {
			return slots.size();
		}

		public int activeCount() {
			return activeCount;
		}

		public int[] words() {
			return words(0, 0, 0);
		}

		public int[] words(double originX, double originY, double originZ) {
			int[] words = new int[Math.max(1, slots.size() * 7 + activeCount)];
			int cursor = slots.size() * 7;
			for (var slot : slots) {
				if (slot == null) {
					continue;
				}
				var box = slot.box;
				int at = slot.index * 7;
				words[at] = Float.floatToRawIntBits((float) (box.x() - originX));
				words[at + 1] = Float.floatToRawIntBits((float) (box.y() - originY));
				words[at + 2] = Float.floatToRawIntBits((float) (box.z() - originZ));
				words[at + 3] = Float.floatToRawIntBits((float) box.sizeX());
				words[at + 4] = Float.floatToRawIntBits((float) box.sizeY());
				words[at + 5] = Float.floatToRawIntBits((float) box.sizeZ());
				words[at + 6] = slot.retireAt == Double.POSITIVE_INFINITY ? slot.generation : -slot.generation;
				words[cursor++] = slot.index;
			}
			return words;
		}

		private static class Slot {
			private final int index;
			private final int generation;
			private HordeClient.RegionBox box;
			private double retireAt = Double.POSITIVE_INFINITY;

			private Slot(int index, int generation, HordeClient.RegionBox box) {
				this.index = index;
				this.generation = generation;
				this.box = box;
			}
		}
	}
}
