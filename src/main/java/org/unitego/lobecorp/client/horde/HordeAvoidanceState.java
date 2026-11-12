package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import org.unitego.lobecorp.config.HordePathVariant;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/// 每事件独立的活动行窗；CPU 只计算整窗范围，逐单位积分与重叠行迁移由 GPU 完成。
public class HordeAvoidanceState implements AutoCloseable {
	public static final int WORDS = 4;
	private static final int ROW_WIDTH = 1024;
	private static final int WINDOW_BYTES = 32;
	private static final RenderPipeline UPDATE = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_avoidance"))
			.withVertexShader(Lobecorp.id("core/horde_motion"))
			.withFragmentShader(Lobecorp.id("core/horde_avoidance"))
			.withSampler("HordeRoute").withSampler("HordeTerrain").withSampler("HordeSplit")
			.withSampler("AvoidancePrevious").withSampler("HordeNearTerrain")
			.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
			.withUniform("AvoidanceWindow", UniformType.UNIFORM_BUFFER)
			.withUniform("HordeNearTerrainParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
	private static final RenderPipeline UPDATE_LINES = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_avoidance_lines"))
			.withVertexShader(Lobecorp.id("core/horde_avoidance_lines"))
			.withFragmentShader(Lobecorp.id("core/horde_avoidance_lines"))
			.withSampler("HordeRoute").withSampler("HordeTerrain").withSampler("HordeSplit")
			.withSampler("AvoidancePrevious").withSampler("HordeNearTerrain")
			.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
			.withUniform("AvoidanceWindow", UniformType.UNIFORM_BUFFER)
			.withUniform("HordeNearTerrainParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.DEBUG_LINES)
			.withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
	private GpuTexture read;
	private GpuTexture write;
	private GpuTextureView readView;
	private GpuTextureView writeView;
	private GpuBuffer window;
	private ByteBuffer memory;
	private long firstRow;
	private long previousFirstRow;
	private int rows;
	private int previousRows;
	private int columns;
	private int writeWidth;
	private int writeHeight;
	private double preparedTime = Double.NaN;
	private double updatedTime = Double.NaN;

	public static boolean precompile() {
		return RenderSystem.getDevice().precompilePipeline(HordePathVariant.avoidanceKernel() ? UPDATE_LINES : UPDATE).isValid();
	}

	/// 与 Batch 抽取一致，线路末端不重复提交恰好落在上一段末端的单位。
	public static long firstRow(double time, double speed, double length, double spacing) {
		return Math.max(0, (long) Math.floor((time * speed - length) / spacing) + 1);
	}

	public static long lastRow(double time, double speed, long stop, double spacing) {
		return (long) Math.floor(Math.min(stop, time) * speed / spacing);
	}

	/// 仅按整窗首行求偏移；超出 int 时不可能与当前 GPU 窗重叠，交给行 tag 拒绝。
	public static int windowShift(long first, long previousFirst) {
		return Math.clamp(first - previousFirst, Integer.MIN_VALUE, Integer.MAX_VALUE);
	}

	public void prepare(HordeClient.ClientHorde event, double time) {
		if (!Double.isNaN(preparedTime) && time <= preparedTime) {
			return;
		}

		if (columns != 0 && columns != event.columns) {
			throw new IllegalStateException("群体固定列数变化需要新的事件资源");
		}

		long first = firstRow(time, event.speed, event.length, event.rowSpacing());
		long last = lastRow(time, event.speed, event.stop, event.rowSpacing());
		int count = last < first ? 0 : Math.toIntExact(last - first + 1);
		int capacityRows = Math.max(count, Math.toIntExact((long) Math.ceil(event.length / event.rowSpacing()) + 1));
		ensureWrite(event.columns, capacityRows);
		firstRow = first;
		rows = count;
		columns = event.columns;
		preparedTime = time;
	}

	private void ensureWrite(int columnCount, int rowCount) {
		long words = Math.multiplyExact(Math.multiplyExact((long) columnCount, rowCount), WORDS);
		var device = RenderSystem.getDevice();
		int limit = device.getMaxTextureSize() / WORDS * WORDS;
		if (limit < WORDS) {
			throw new IllegalStateException("设备纹理宽度无法容纳完整避让状态");
		}
		int width = Math.min(ROW_WIDTH, limit);
		while (Math.ceilDiv(words, width) > limit && width < limit) {
			width = (int) Math.min((long) width * 2, limit);
		}

		int height = Math.toIntExact(Math.ceilDiv(words, width));
		if (height > limit) {
			throw new IllegalArgumentException("群体避让状态超过设备纹理容量，不截断活动单位");
		}

		if (write != null && writeWidth == width && writeHeight >= height) {
			return;
		}

		var replacement = device.createTexture(() -> "Horde avoidance destination",
				GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
				TextureFormat.RGBA8, width, Math.max(1, height), 1, 1);
		GpuTextureView replacementView = null;
		try {
			replacementView = device.createTextureView(replacement);
			device.createCommandEncoder().clearColorTexture(replacement, 0);
		} catch (RuntimeException exception) {
			if (replacementView != null) {
				replacementView.close();
			}
			replacement.close();
			throw exception;
		}

		if (writeView != null) {
			writeView.close();
			write.close();
		}
		write = replacement;
		writeView = replacementView;
		writeWidth = width;
		writeHeight = Math.max(1, height);
		if (read == null) {
			// 首次使用需要独立的零纹理；后续扩容始终保留旧读纹理供 GPU 迁移。
			try {
				read = device.createTexture(() -> "Horde avoidance previous",
						GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
						TextureFormat.RGBA8, width, Math.max(1, height), 1, 1);
				readView = device.createTextureView(read);
				device.createCommandEncoder().clearColorTexture(read, 0);
				window = device.createBuffer(() -> "Horde avoidance window",
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, WINDOW_BYTES);
				memory = MemoryUtil.memAlloc(WINDOW_BYTES);
			} catch (RuntimeException exception) {
				close();
				throw exception;
			}
		}
	}

	public void update(double time, GpuBufferSlice batchParameters, GpuTextureView route,
	                   GpuTextureView terrain, GpuTextureView split) {
		if (time <= updatedTime || rows <= 0) {
			return;
		}

		HordeNearTerrain.prepare();
		int relative = windowShift(firstRow, previousFirstRow);
		memory.clear();
		memory.putInt(relative).putInt(previousRows).putInt(columns).putInt(rows)
				.putFloat(Double.isNaN(updatedTime) ? (float) time : (float) updatedTime)
				.putFloat(Double.isNaN(updatedTime) ? 0 : (float) Math.max(0, time - updatedTime))
				.putFloat(writeWidth).putFloat(writeHeight).flip();
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.writeToBuffer(window.slice(), memory);
		HordeRenderMetrics.add(HordeRenderMetrics.Counter.UPLOAD_BYTES, WINDOW_BYTES);
		var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		try (var pass = encoder.createRenderPass(() -> "Horde avoidance integration", writeView, OptionalInt.empty())) {
			HordeIrisShadows.setNativePipeline(pass, HordePathVariant.avoidanceKernel() ? UPDATE_LINES : UPDATE);
			pass.setViewport(0, 0, writeWidth, writeHeight);
			pass.setUniform("HordeParameters", batchParameters);
			pass.setUniform("AvoidanceWindow", window.slice());
			pass.setUniform("HordeNearTerrainParameters", HordeNearTerrain.parameters());
			pass.bindTexture("HordeNearTerrain", HordeNearTerrain.view(), sampler);
			pass.bindTexture("HordeRoute", route, sampler);
			pass.bindTexture("HordeTerrain", terrain, sampler);
			pass.bindTexture("HordeSplit", split, sampler);
			pass.bindTexture("AvoidancePrevious", readView, sampler);
			pass.draw(0, HordePathVariant.avoidanceKernel() ? Math.multiplyExact(Math.multiplyExact(rows, columns), 2) : 3);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
		}

		var oldRead = read;
		var oldView = readView;
		int oldWidth = read.getWidth(0);
		int oldHeight = read.getHeight(0);
		read = write;
		readView = writeView;
		write = oldRead;
		writeView = oldView;
		writeWidth = oldWidth;
		writeHeight = oldHeight;
		previousFirstRow = firstRow;
		previousRows = rows;
		updatedTime = time;
	}

	public long firstRow() {
		return firstRow;
	}

	public int rows() {
		return rows;
	}

	/// 只统计本事件两张 RGBA8 状态纹理；不做 GPU 回读。
	public long textureBytes() {
		long bytes = read == null ? 0 : (long) read.getWidth(0) * read.getHeight(0) * 4;
		return bytes + (write == null ? 0 : (long) write.getWidth(0) * write.getHeight(0) * 4);
	}

	public GpuTextureView view() {
		return readView;
	}

	@Override
	public void close() {
		if (readView != null) {
			readView.close();
			readView = null;
		}
		if (writeView != null) {
			writeView.close();
			writeView = null;
		}
		if (read != null) {
			read.close();
			read = null;
		}
		if (write != null) {
			write.close();
			write = null;
		}
		if (window != null) {
			window.close();
			window = null;
		}
		if (memory != null) {
			MemoryUtil.memFree(memory);
			memory = null;
		}
		previousRows = 0;
		preparedTime = Double.NaN;
		updatedTime = Double.NaN;
	}
}
