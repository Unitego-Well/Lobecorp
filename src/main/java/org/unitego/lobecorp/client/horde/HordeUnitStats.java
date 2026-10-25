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
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalInt;
import java.util.Arrays;

/// GPU归约穿透最大值与拒绝数；只异步读取两个标量，未完成结果保持未知。
public class HordeUnitStats implements AutoCloseable {
	private static final int READBACKS = 4;
	private static final int MAX_PASSES = 16;
	private static final int FAN_IN = 16;
	private static final int PARAMETER_BYTES = 16;
	private static final RenderPipeline REDUCE = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_unit_stats"))
			.withVertexShader(Lobecorp.id("core/horde_motion"))
			.withFragmentShader(Lobecorp.id("core/horde_unit_stats"))
			.withSampler("StatsSource").withUniform("HordeUnitStatsParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false)
			.withColorTargetState(ColorTargetState.DEFAULT).build();
	private final GpuTexture[] targets = new GpuTexture[2];
	private final GpuTextureView[] views = new GpuTextureView[2];
	private final Readback[] readbacks = new Readback[READBACKS];
	private GpuBuffer parameters;
	private ByteBuffer upload;
	private int stride;
	private int width;
	private int height;
	private boolean closed;

	public HordeUnitStats() {
		Arrays.setAll(readbacks, _ -> new Readback());
	}

	public static boolean precompile() {
		return RenderSystem.getDevice().precompilePipeline(REDUCE).isValid();
	}

	public void capture(HordeUnitState state, long renderFrame, long drawSerial) {
		HordeRenderMetrics.expectConstraint(renderFrame);
		if (closed) {
			HordeRenderMetrics.constraintResult(renderFrame, Double.NaN, -1);
			return;
		}
		Readback slot = null;
		for (var candidate : readbacks) {
			if (!candidate.pending) {
				slot = candidate;
				break;
			}
		}
		if (slot == null) {
			HordeRenderMetrics.constraintResult(renderFrame, Double.NaN, -1);
			return;
		}
		long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.UNIT_STATS);
		try {
			int source = Math.multiplyExact(state.width(), state.height()) / HordeUnitState.VALUES;
			ensure(source);
			int count = source;
			int passes = 0;
			upload.clear();
			while (count > 1 || passes == 0) {
				if (passes >= MAX_PASSES) {
					throw new IllegalStateException("单位统计归约层数超过预算");
				}
				upload.position(passes * stride);
				upload.putInt(count).putInt(width).putInt((int) drawSerial).putInt(passes == 0 ? 1 : 0);
				count = Math.ceilDiv(count, FAN_IN);
				passes++;
			}
			int bytes = passes * stride;
			upload.position(0).limit(bytes);
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			encoder.writeToBuffer(parameters.slice(0, bytes), upload);
			var sourceView = state.current();
			count = source;
			for (int passIndex = 0; passIndex < passes; passIndex++) {
				int target = passIndex & 1;
				try (var pass = encoder.createRenderPass(() -> "Horde constraint statistics", views[target], OptionalInt.empty())) {
					pass.setPipeline(REDUCE);
					pass.setViewport(0, 0, width, height);
					pass.setUniform("HordeUnitStatsParameters", parameters.slice((long) passIndex * stride, PARAMETER_BYTES));
					pass.bindTexture("StatsSource", sourceView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
					pass.draw(0, 3);
					HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
					HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
				}
				sourceView = views[target];
				count = Math.ceilDiv(count, FAN_IN);
			}
			if (slot.buffer == null) {
				slot.buffer = RenderSystem.getDevice().createBuffer(() -> "Horde constraint scalar readback",
						GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 8);
			}
			var readback = slot;
			readback.pending = true;
			encoder.copyTextureToBuffer(targets[(passes - 1) & 1], readback.buffer, 0, () -> {
				try {
					if (closed || readback.buffer == null || readback.buffer.isClosed()) {
						return;
					}
					try (var mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(readback.buffer, true, false)) {
						var data = mapped.data().order(ByteOrder.LITTLE_ENDIAN);
						float maximum = data.getFloat(0);
						float rejected = data.getFloat(4);
						HordeRenderMetrics.constraintResult(renderFrame, maximum,
								Float.isFinite(rejected) && rejected >= 0 && rejected <= source
										&& rejected == Math.floor(rejected) ? (long) rejected : -1);
					}
				} catch (RuntimeException failure) {
					HordeRenderMetrics.constraintResult(renderFrame, Double.NaN, -1);
				} finally {
					readback.pending = false;
				}
			}, 0, 0, 0, 2, 1);
		} catch (RuntimeException | OutOfMemoryError failure) {
			slot.pending = false;
			HordeRenderMetrics.constraintResult(renderFrame, Double.NaN, -1);
			Lobecorp.LOGGER.debug("单位约束统计暂不可用，保留未知结果", failure);
		} finally {
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.UNIT_STATS, started);
		}
	}

	private void ensure(int cells) {
		var device = RenderSystem.getDevice();
		int words = Math.multiplyExact(Math.ceilDiv(cells, FAN_IN), 2);
		int targetWidth = Math.min(1024, device.getMaxTextureSize());
		int rows = Math.ceilDiv(words, targetWidth);
		if (targets[0] == null || width != targetWidth || height < rows) {
			int targetHeight = Integer.highestOneBit(Math.max(1, rows - 1)) * 2;
			if (targetHeight > device.getMaxTextureSize()) {
				throw new IllegalArgumentException("单位统计纹理超过GPU尺寸");
			}
			releaseTargets();
			try {
				for (int index = 0; index < 2; index++) {
					targets[index] = device.createTexture(() -> "Horde constraint reduction",
							GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
							TextureFormat.RGBA8, targetWidth, targetHeight, 1, 1);
					views[index] = device.createTextureView(targets[index]);
				}
			} catch (RuntimeException | OutOfMemoryError failure) {
				releaseTargets();
				throw failure;
			}
			width = targetWidth;
			height = targetHeight;
		}
		if (parameters == null) {
			stride = Math.ceilDiv(PARAMETER_BYTES, device.getUniformOffsetAlignment()) * device.getUniformOffsetAlignment();
			int bytes = Math.multiplyExact(stride, MAX_PASSES);
			var memory = MemoryUtil.memAlloc(bytes);
			try {
				parameters = device.createBuffer(() -> "Horde constraint reduction parameters",
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, bytes);
				upload = memory;
			} catch (RuntimeException | OutOfMemoryError failure) {
				MemoryUtil.memFree(memory);
				throw failure;
			}
		}
	}

	private void releaseTargets() {
		for (int index = 0; index < 2; index++) {
			if (views[index] != null) {
				views[index].close();
			}
			if (targets[index] != null) {
				targets[index].close();
			}
			views[index] = null;
			targets[index] = null;
		}
	}

	@Override
	public void close() {
		closed = true;
		releaseTargets();
		if (parameters != null) {
			parameters.close();
		}
		parameters = null;
		if (upload != null) {
			MemoryUtil.memFree(upload);
		}
		upload = null;
		for (var slot : readbacks) {
			if (slot.buffer != null) {
				slot.buffer.close();
			}
			slot.buffer = null;
			slot.pending = false;
		}
	}

	private static class Readback {
		private GpuBuffer buffer;
		private boolean pending;
	}
}
