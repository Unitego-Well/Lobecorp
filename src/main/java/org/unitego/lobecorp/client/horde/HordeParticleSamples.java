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
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/// 渲染线程拥有GPU资源；tick线程只消费不可变的最近帧真实单位脚点，绝不外推为当前精确位置。
public class HordeParticleSamples implements AutoCloseable {
	// 固定256个候选，每点9字；只回读9KiB，不回读整页单位历史。
	private static final int CAPACITY = 256;
	private static final int WORDS = 9;
	public static final int READBACK_BYTES = CAPACITY * WORDS * Integer.BYTES;
	private static final int PARAMETER_BYTES = 64;
	private static final long MAX_AGE_NANOS = 100_000_000L;
	private static final ConcurrentHashMap<UUID, HordeParticleSamples> ACTIVE = new ConcurrentHashMap<>();
	private static final AtomicLong GENERATION = new AtomicLong();
	private static final RenderPipeline GATHER = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_particle_samples"))
			.withVertexShader(Lobecorp.id("core/horde_motion"))
			.withFragmentShader(Lobecorp.id("core/horde_particle_samples"))
			.withSampler("SampleUnits").withSampler("SampleSelection")
			.withUniform("HordeParticleSampleParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false)
			.withColorTargetState(ColorTargetState.DEFAULT).build();
	private final Readback[] readbacks = {new Readback(), new Readback(), new Readback()};
	private GpuTexture selection;
	private GpuTexture result;
	private GpuTextureView selectionView;
	private GpuTextureView resultView;
	private GpuBuffer parameters;
	private ByteBuffer upload;
	private int stride;
	private volatile boolean closed;
	private volatile @Nullable Snapshot snapshot;
	private HordeClient.ClientEvent owner;
	private Object world;
	private long lastCapture;

	/// 位序为行列偏移(-1,-1)、(-1,0)、(-1,1)、(0,-1)、(0,1)、(1,-1)、(1,0)、(1,1)。
	/// 缺邻仅表示边界候选，也可能是未绘制邻居；出生前仍须核对当前分割和其他浪潮。
	public record Sample(long row, int column, Vec3 ground, int occupancyMask, double station) {
		public static Sample read(ByteBuffer data, int at, Vec3 origin) {
			long row = Integer.toUnsignedLong(data.getInt(at + 12)) | (long) data.getInt(at + 16) << 32;
			var point = new Vec3(data.getFloat(at), data.getFloat(at + 4), data.getFloat(at + 8)).add(origin);
			return new Sample(row, data.getInt(at + 20), point, data.getInt(at + 24), data.getFloat(at + 32));
		}

		public boolean inWave(double tail, double head) {
			return Double.isFinite(station) && Double.isFinite(tail) && Double.isFinite(head)
					&& station >= tail && station <= head;
		}

		public boolean interior() {
			return occupancyMask == 255;
		}

		public boolean boundary() {
			return !interior();
		}
	}

	public record Snapshot(UUID eventId, double eventTime, long drawSerial, long generation,
	                       long capturedNanos, List<Sample> samples) {
		public Snapshot {
			samples = List.copyOf(samples);
		}

		public boolean fresh(double currentTime, long nowNanos) {
			double age = currentTime - eventTime;
			long elapsed = nowNanos - capturedNanos;
			return Double.isFinite(age) && age >= 0 && age <= 1 && elapsed >= 0 && elapsed <= MAX_AGE_NANOS;
		}
	}

	public static boolean precompile() {
		return RenderSystem.getDevice().precompilePipeline(GATHER).isValid();
	}

	/// 返回至多一tick旧且不晚于调用方事件时钟的快照；无证据、卸载或重载均返回null。
	public static @Nullable Snapshot latest(HordeClient.ClientEvent event, double eventTime) {
		var source = ACTIVE.get(event.id);
		if (source == null || source.closed || source.owner != event
				|| source.world != Minecraft.getInstance().level) {
			return null;
		}
		var value = source.snapshot;
		return value != null && value.fresh(eventTime, System.nanoTime())
				&& value.generation() == GENERATION.get() ? value : null;
	}

	public static void invalidate() {
		GENERATION.incrementAndGet();
		ACTIVE.clear();
	}

	/// 只在所有绘制组推进完单位历史后调用。分桶遍历全部有效GPU页，轮换选点不等于穷尽局部集合。
	public void capture(HordeUnitState units, HordeClient.ClientEvent event, double time, long serial, long referenceRow) {
		RenderSystem.assertOnRenderThread();
		long now = System.nanoTime();
		if (closed || units.width() == 0 || now - lastCapture < 25_000_000L) {
			return;
		}
		Readback free = null;
		for (var slot : readbacks) {
			if (!slot.pending) {
				free = slot;
				break;
			}
		}
		if (free == null) {
			return;
		}
		var slot = free;
		try {
			ensure();
			owner = event;
			world = Minecraft.getInstance().level;
			ACTIVE.put(event.id, this);
			long generation = GENERATION.get();
			Object capturedWorld = world;
			var camera = Minecraft.getInstance().gameRenderer.getMainCamera().position().subtract(event.origin);
			upload.clear();
			for (int pass = 0; pass < 2; pass++) {
				upload.position(pass * stride);
				upload.putInt(units.width() * units.height() / HordeUnitState.VALUES)
						.putInt((int) serial).putInt(pass).putInt(CAPACITY);
				upload.putFloat((float) camera.x).putFloat((float) camera.y).putFloat((float) camera.z)
						.putFloat((float) (Math.max(event.rules.rowSpacing(), event.rules.columnSpacing()) * 1.8));
				upload.putInt(units.ringMask()).putInt(units.columnTiles()).putInt(units.width() / (16 * HordeUnitState.VALUES))
						.putFloat((float) event.length);
				// 参考站程先用double消去大行号，GPU只把局部整数行差转float。
				upload.putInt((int) referenceRow).putInt((int) (referenceRow >>> 32))
						.putFloat((float) (time * event.speed - referenceRow * event.rules.rowSpacing()))
						.putFloat((float) event.rules.rowSpacing());
			}
			upload.position(0).limit(stride + PARAMETER_BYTES);
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.writeToBuffer(parameters.slice(0, upload.remaining()), upload);
			var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
			for (int index = 0; index < 2; index++) {
				try (var pass = encoder.createRenderPass(() -> "Horde local actual unit samples",
						index == 0 ? selectionView : resultView, OptionalInt.empty())) {
					pass.setPipeline(GATHER);
					pass.setViewport(0, 0, CAPACITY * (index == 0 ? 1 : WORDS), 1);
					pass.setUniform("HordeParticleSampleParameters", parameters.slice((long) index * stride, PARAMETER_BYTES));
					pass.bindTexture("SampleUnits", units.current(), nearest);
					// 选择pass不读取该绑定，避免采样与附件绑定同一纹理。
					pass.bindTexture("SampleSelection", index == 0 ? units.current() : selectionView, nearest);
					pass.draw(0, 3);
				}
			}
			if (slot.buffer == null) {
				slot.buffer = RenderSystem.getDevice().createBuffer(() -> "Horde local particle sample readback",
						GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, READBACK_BYTES);
			}
			slot.pending = true;
			lastCapture = now;
			encoder.copyTextureToBuffer(result, slot.buffer, 0,
					() -> complete(slot, event, time, serial, generation, now, capturedWorld),
					0, 0, 0, CAPACITY * WORDS, 1);
		} catch (RuntimeException | OutOfMemoryError failure) {
			slot.pending = false;
			snapshot = null;
			Lobecorp.LOGGER.debug("群体实际单位粒子采样暂不可用", failure);
		}
	}

	private void complete(Readback slot, HordeClient.ClientEvent event, double time, long serial,
	                      long generation, long now, Object capturedWorld) {
		try {
			if (closed || generation != GENERATION.get() || ACTIVE.get(event.id) != this
					|| capturedWorld != Minecraft.getInstance().level || slot.buffer == null || slot.buffer.isClosed()
					|| System.nanoTime() - now > MAX_AGE_NANOS) {
				return;
			}
			var samples = new ArrayList<Sample>();
			try (var mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(slot.buffer, true, false)) {
				var data = mapped.data().order(ByteOrder.LITTLE_ENDIAN);
				for (int index = 0; index < CAPACITY; index++) {
					int at = index * WORDS * 4;
					if (data.getInt(at + 28) == 0) {
						continue;
					}
					var sample = Sample.read(data, at, event.origin);
					var point = sample.ground();
					if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
							|| !sample.inWave(0, event.length) || sample.column() < 0 || sample.column() >= event.columns) {
						continue;
					}
					samples.add(sample);
				}
			}
			var previous = snapshot;
			if (previous == null || previous.drawSerial() < serial) {
				snapshot = new Snapshot(event.id, time, serial, generation, now, samples);
			}
		} catch (RuntimeException failure) {
			snapshot = null;
			Lobecorp.LOGGER.debug("群体实际单位粒子回读暂不可用", failure);
		} finally {
			slot.pending = false;
		}
	}

	private void ensure() {
		if (parameters != null) {
			return;
		}
		var device = RenderSystem.getDevice();
		try {
			selection = device.createTexture(() -> "Horde particle selection", GpuTexture.USAGE_RENDER_ATTACHMENT
					| GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, CAPACITY, 1, 1, 1);
			selectionView = device.createTextureView(selection);
			result = device.createTexture(() -> "Horde particle samples", GpuTexture.USAGE_RENDER_ATTACHMENT
					| GpuTexture.USAGE_COPY_SRC, TextureFormat.RGBA8, CAPACITY * WORDS, 1, 1, 1);
			resultView = device.createTextureView(result);
			stride = Math.ceilDiv(PARAMETER_BYTES, device.getUniformOffsetAlignment()) * device.getUniformOffsetAlignment();
			parameters = device.createBuffer(() -> "Horde particle sample parameters",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, stride * 2L);
			upload = MemoryUtil.memAlloc(stride * 2);
		} catch (RuntimeException | OutOfMemoryError failure) {
			release();
			throw failure;
		}
	}

	@Override
	public void close() {
		RenderSystem.assertOnRenderThread();
		closed = true;
		snapshot = null;
		if (owner != null) {
			ACTIVE.remove(owner.id, this);
		}
		release();
		for (var slot : readbacks) {
			if (slot.buffer != null) {
				slot.buffer.close();
			}
			slot.buffer = null;
			slot.pending = false;
		}
	}

	private void release() {
		if (selectionView != null) {
			selectionView.close();
		}
		if (resultView != null) {
			resultView.close();
		}
		if (selection != null) {
			selection.close();
		}
		if (result != null) {
			result.close();
		}
		if (parameters != null) {
			parameters.close();
		}
		if (upload != null) {
			MemoryUtil.memFree(upload);
		}
		selectionView = resultView = null;
		selection = result = null;
		parameters = null;
		upload = null;
	}

	private static class Readback {
		private GpuBuffer buffer;
		private boolean pending;
	}
}
