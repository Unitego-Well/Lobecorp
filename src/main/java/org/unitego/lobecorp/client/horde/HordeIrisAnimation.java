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

import java.util.OptionalInt;

/// GPU一次准备Iris帧位置、法线和切线；之后所有实例复用，不新增CPU顶点处理。
public class HordeIrisAnimation implements AutoCloseable {
	private static final int FRAME_VALUES = 10;
	private static final int ROW_WIDTH = 1024;
	private static final RenderPipeline PREPARE = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_iris_frames"))
			.withVertexShader(Lobecorp.id("core/horde_motion"))
			.withFragmentShader(Lobecorp.id("core/horde_iris_frames"))
			.withSampler("FrameAnimation").withSampler("FrameUvs")
			.withUniform("IrisFrameParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
	private final GpuTexture texture;
	private final GpuTextureView view;

	public HordeIrisAnimation(HordeMeshCache.Model model, HordeDataTexture animation, HordeDataTexture uvs) {
		var device = RenderSystem.getDevice();
		var layout = FrameLayout.plan(model.vertexCount(), model.geometryFrames(), device.getMaxTextureSize());
		texture = device.createTexture(() -> "Horde cached Iris frames",
				GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
				TextureFormat.RGBA8, layout.width(), layout.height(), 1, 1);
		GpuTextureView replacement;
		try {
			replacement = device.createTextureView(texture);
		} catch (RuntimeException exception) {
			texture.close();
			throw exception;
		}
		view = replacement;
		var memory = MemoryUtil.memAlloc(16);
		long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.IRIS_PREPARE, layout.words());
		try {
			memory.putInt(model.vertexCount()).putInt(model.geometryFrames()).putInt(layout.width()).putInt(0).flip();
			try (GpuBuffer parameters = device.createBuffer(() -> "Horde Iris frame parameters", GpuBuffer.USAGE_UNIFORM, memory);
			     var pass = device.createCommandEncoder().createRenderPass(() -> "Horde prepare Iris frames", view, OptionalInt.empty())) {
				var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
				HordeIrisShadows.setNativePipeline(pass, PREPARE);
				pass.setViewport(0, 0, layout.width(), layout.height());
				pass.setUniform("IrisFrameParameters", parameters.slice());
				pass.bindTexture("FrameAnimation", animation.texture().getTextureView(), sampler);
				pass.bindTexture("FrameUvs", uvs.texture().getTextureView(), sampler);
				pass.draw(0, 3);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.IRIS_FRAME_WORDS, layout.words());
			}
		} catch (RuntimeException exception) {
			close();
			throw exception;
		} finally {
			MemoryUtil.memFree(memory);
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.IRIS_PREPARE, started);
		}
	}

	public static boolean precompile() {
		return RenderSystem.getDevice().precompilePipeline(PREPARE).isValid();
	}

	public GpuTextureView view() {
		return view;
	}

	@Override
	public void close() {
		view.close();
		texture.close();
	}

	public record FrameLayout(int width, int height, long words) {
		public static FrameLayout plan(int vertices, int frames, int limit) {
			if (vertices <= 0 || vertices % 4 != 0 || frames <= 0 || limit <= 0) {
				throw new IllegalArgumentException("Iris帧资源需要完整四边形与有效帧数");
			}
			int width = Math.min(ROW_WIDTH, limit);
			long words = Math.multiplyExact(Math.multiplyExact((long) vertices, frames), FRAME_VALUES);
			long rows = Math.ceilDiv(words, width);
			if (rows > limit) {
				throw new IllegalArgumentException("Iris完整动画帧超过设备纹理容量，不截断帧数");
			}
			return new FrameLayout(width, (int) rows, words);
		}
	}
}
