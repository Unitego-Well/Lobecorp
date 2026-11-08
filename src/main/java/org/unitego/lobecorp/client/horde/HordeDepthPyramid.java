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
import java.util.OptionalInt;

/// 将场景深度归并为保守远深度；奇偶层分属两张纹理，避免采样当前附件。
public class HordeDepthPyramid implements AutoCloseable {

	private static final int PARAMETER_BYTES = 16;
	private static final RenderPipeline REDUCE = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_lod_depth"))
			.withVertexShader(Lobecorp.id("core/horde_motion"))
			.withFragmentShader(Lobecorp.id("core/horde_lod_depth"))
			.withSampler("HordeDepthSource")
			.withUniform("HordeDepthParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
	private final GpuTexture[] textures = new GpuTexture[2];
	private final GpuTextureView[] views = new GpuTextureView[2];
	private GpuTextureView[] attachments;
	private GpuBuffer parameters;
	private ByteBuffer upload;
	private int width;
	private int height;
	private int levels;

	public static boolean precompile() {
		return RenderSystem.getDevice().precompilePipeline(REDUCE).isValid();
	}

	public void prepare(GpuTextureView sceneDepth, boolean reverse) {
		// 当前OpenGL后端逐级直接右移宽高；正方形保证完整mip链的末级仍为1×1。
		int side = nextPowerOfTwo(Math.ceilDiv(Math.max(sceneDepth.getWidth(0), sceneDepth.getHeight(0)), 2));
		ensure(side, side);
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		for (int level = 0; level < levels; level++) {
			boolean raw = level == 0;
			int parity = level & 1;
			upload.clear();
			upload.putInt(raw ? sceneDepth.baseMipLevel() : level - 1)
					.putInt(raw ? 1 : 0).putInt(reverse ? 1 : 0).putInt(0).flip();
			encoder.writeToBuffer(parameters.slice(), upload);
			try (var pass = encoder.createRenderPass(() -> "Horde conservative depth pyramid",
					attachments[level], OptionalInt.empty())) {
				HordeIrisShadows.setNativePipeline(pass, REDUCE);
				pass.setUniform("HordeDepthParameters", parameters);
				pass.bindTexture("HordeDepthSource", raw ? sceneDepth : views[1 - parity], nearest);
				pass.setViewport(0, 0, Math.max(1, width >> level), Math.max(1, height >> level));
				pass.draw(0, 3);
			}
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.UPLOAD_BYTES, PARAMETER_BYTES);
		}
	}

	private static int nextPowerOfTwo(int value) {
		return value <= 1 ? 1 : Integer.highestOneBit(value - 1) * 2;
	}

	// 跨帧纹理由本实例数组持有，重建和close统一释放。
	@SuppressWarnings("resource")
	private void ensure(int nextWidth, int nextHeight) {
		if (parameters != null && width == nextWidth && height == nextHeight) {
			return;
		}
		close();
		width = nextWidth;
		height = nextHeight;
		levels = Integer.numberOfTrailingZeros(Integer.highestOneBit(Math.max(width, height))) + 1;
		attachments = new GpuTextureView[levels];
		var device = RenderSystem.getDevice();
		try {
			for (int parity = 0; parity < textures.length; parity++) {
				textures[parity] = device.createTexture(() -> "Horde conservative depth levels",
						GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
						TextureFormat.RGBA8, width, height, 1, levels);
				views[parity] = device.createTextureView(textures[parity], 0, levels);
			}
			for (int level = 0; level < levels; level++) {
				attachments[level] = device.createTextureView(textures[level & 1], level, 1);
			}
			parameters = device.createBuffer(() -> "Horde depth reduction parameters",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, PARAMETER_BYTES);
			upload = MemoryUtil.memAlloc(PARAMETER_BYTES);
		} catch (RuntimeException | OutOfMemoryError failure) {
			close();
			throw failure;
		}
	}

	/// 返回借用的全层视图；只读取与parity相同奇偶性的层。
	public GpuTextureView view(int parity) {
		return views[parity];
	}

	public int levels() {
		return levels;
	}

	@Override
	public void close() {
		if (attachments != null) {
			for (var attachment : attachments) {
				if (attachment != null) {
					attachment.close();
				}
			}
			attachments = null;
		}
		for (int parity = 0; parity < textures.length; parity++) {
			if (views[parity] != null) {
				views[parity].close();
				views[parity] = null;
			}
			if (textures[parity] != null) {
				textures[parity].close();
				textures[parity] = null;
			}
		}
		if (parameters != null) {
			parameters.close();
			parameters = null;
		}
		if (upload != null) {
			MemoryUtil.memFree(upload);
			upload = null;
		}
		width = 0;
		height = 0;
		levels = 0;
	}
}
