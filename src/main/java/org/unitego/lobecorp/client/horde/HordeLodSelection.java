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
import org.joml.Matrix4fc;
import org.unitego.lobecorp.Lobecorp;

import java.util.OptionalInt;
import java.util.Arrays;
import java.util.Objects;
import java.nio.ByteBuffer;

/// 最终运动位置上的全局距离排序与材质紧凑索引；不回读 CPU。
public class HordeLodSelection implements AutoCloseable {

	public static final int CAPACITY = 128;
	public static final int MAX_DISTANCE = 128;
	public static final int DISTANCE_STEP = 16;
	private static final int PARAMETER_BYTES = 48;
	private static final int VISIBILITY_BYTES = 96;
	private static final RenderPipeline CANDIDATES = pipeline("horde_lod_candidates", "HordeMotion", "HordeDepthEven", "HordeDepthOdd");
	private static final RenderPipeline SORT = pipeline("horde_lod_sort", "HordeLodSource");
	private static final RenderPipeline MERGE = pipeline("horde_lod_merge", "HordeLodSource");
	private static final RenderPipeline CLASSIFY = pipeline("horde_lod_classify", "HordeMotion", "HordeLodSource");
	private static final RenderPipeline COMPACT = pipeline("horde_lod_compact", "HordeLodSource", "HordeLodRanges");
	// 数组持有两个排序目标，统一由本实例close释放。
	@SuppressWarnings("resource")
	private final Target[] sorting = {new Target(), new Target()};
	private final Target selected = new Target();
	private final Target compact = new Target();
	private final HordeDepthPyramid depthPyramid = new HordeDepthPyramid();
	private GpuBuffer parameters;
	private ByteBuffer parameterUpload;
	private GpuBuffer visibilityParameters;
	private ByteBuffer visibilityUpload;
	private HordeDataTexture materialRanges;
	private int[] previousRanges;
	private int activeCandidates;

	public static boolean precompile(int step) {
		if (step == 5) {
			return HordeDepthPyramid.precompile();
		}

		var pipeline = switch (step) {
			case 0 -> CANDIDATES;
			case 1 -> SORT;
			case 2 -> MERGE;
			case 3 -> CLASSIFY;
			case 4 -> COMPACT;
			default -> throw new IllegalArgumentException("群体LOD预热步骤无效");
		};
		return RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
	}

	private static RenderPipeline pipeline(String shader, String... samplers) {
		var builder = RenderPipeline.builder()
				.withLocation(Lobecorp.id("pipeline/" + shader))
				.withVertexShader(Lobecorp.id("core/horde_motion"))
				.withFragmentShader(Lobecorp.id("core/" + shader))
				.withUniform("HordeLodParameters", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
				.withCull(false).withColorTargetState(ColorTargetState.DEFAULT);
		for (var sampler : samplers) {
			builder.withSampler(sampler);
		}
		if (shader.equals("horde_lod_candidates")) {
			builder.withUniform("HordeVisibility", UniformType.UNIFORM_BUFFER);
			builder.withUniform("Projection", UniformType.UNIFORM_BUFFER);
		}
		return builder.build();
	}

	public void select(GpuTextureView motion, int count, int materials, int[] ranges,
	                   Matrix4fc modelView, float radius, GpuTextureView depth, boolean reverse) {
		int capacity = Math.max(CAPACITY, Integer.highestOneBit(Math.max(1, count - 1)) * 2);
		int width = Math.min(1024, RenderSystem.getDevice().getMaxTextureSize());
		int height = Math.ceilDiv(Math.multiplyExact(capacity, 2), width);
		sorting[0].ensure(width, height);
		sorting[1].ensure(width, height);
		selected.ensure(width, Math.ceilDiv(capacity, width));
		compact.ensure(CAPACITY, Math.max(1, materials));
		if (parameters == null) {
			parameters = RenderSystem.getDevice().createBuffer(() -> "Horde LOD parameters",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, PARAMETER_BYTES);
			parameterUpload = MemoryUtil.memCalloc(PARAMETER_BYTES);
			visibilityParameters = RenderSystem.getDevice().createBuffer(() -> "Horde visibility parameters",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, VISIBILITY_BYTES);
			visibilityUpload = MemoryUtil.memAlloc(VISIBILITY_BYTES);
		}
		depthPyramid.prepare(depth, reverse);
		visibilityUpload.clear();
		modelView.get(visibilityUpload);
		visibilityUpload.position(64);
		visibilityUpload.putFloat(radius).putFloat(reverse ? 1 : 0)
				.putFloat(RenderSystem.getDevice().isZZeroToOne() ? 1 : 0).putFloat(depthPyramid.levels());
		visibilityUpload.putFloat(depth.getWidth(0)).putFloat(depth.getHeight(0)).putFloat(0).putFloat(0).flip();
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(visibilityParameters.slice(), visibilityUpload);
		HordeRenderMetrics.add(HordeRenderMetrics.Counter.UPLOAD_BYTES, VISIBILITY_BYTES);
		write(width, capacity, 0, count, HordeGpuRenderer.motionValues());
		render(CANDIDATES, sorting[0], motion, null);
		int current = 0;
		// 先各128候选排序，再逐层只合并两表的最近128；总像素工作量随候选线性增长。
		for (int stage = 2; stage <= CAPACITY; stage *= 2) {
			for (int step = stage / 2; step > 0; step /= 2) {
				write(width, capacity, stage == CAPACITY ? 0 : stage, step, 0);
				render(SORT, sorting[1 - current], sorting[current].view, null);
				current = 1 - current;
			}
		}
		for (int remaining = capacity / 2; remaining >= CAPACITY; remaining /= 2) {
			write(width, remaining, 0, 0, 0);
			render(MERGE, sorting[1 - current], sorting[current].view, null);
			current = 1 - current;
			for (int step = CAPACITY / 2; step > 0; step /= 2) {
				write(width, remaining, 0, step, 0);
				render(SORT, sorting[1 - current], sorting[current].view, null);
				current = 1 - current;
			}
		}
		write(width, count, 0, 0, HordeGpuRenderer.motionValues());
		render(CLASSIFY, selected, motion, sorting[current].view);
		if (!Arrays.equals(previousRanges, ranges)) {
			if (materialRanges != null) {
				materialRanges.close();
			}
			materialRanges = new HordeDataTexture("Horde LOD material ranges", ranges);
			previousRanges = ranges.clone();
		}
		write(CAPACITY, materials, ranges.length / 3, 0, 0);
		render(COMPACT, compact, sorting[current].view, materialRanges.texture().getTextureView());
	}

	private void write(int a, int b, int c, int d, int motionStride) {
		activeCandidates = b;
		parameterUpload.clear();
		parameterUpload.putInt(a).putInt(b).putInt(c).putInt(d)
				.putInt(0).putInt(motionStride).putInt(0).putInt(0).position(0);
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(), parameterUpload);
		HordeRenderMetrics.add(HordeRenderMetrics.Counter.UPLOAD_BYTES, PARAMETER_BYTES);
	}

	private void render(RenderPipeline pipeline, Target output, GpuTextureView first, GpuTextureView second) {
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Horde global camera LOD", output.view, OptionalInt.empty())) {
			HordeIrisShadows.setNativePipeline(pass, pipeline);
			pass.setUniform("HordeLodParameters", parameters);
			int rows = pipeline == SORT || pipeline == MERGE
					? Math.min(output.height, Math.ceilDiv(activeCandidates * 2, output.width)) : output.height;
			pass.setViewport(0, 0, output.width, rows);
			if (pipeline == CANDIDATES || pipeline == CLASSIFY) {
				pass.bindTexture("HordeMotion", first, nearest);
			} else {
				pass.bindTexture("HordeLodSource", first, nearest);
			}
			if (pipeline == CANDIDATES) {
				pass.setUniform("HordeVisibility", visibilityParameters.slice());
				pass.setUniform("Projection", Objects.requireNonNull(RenderSystem.getProjectionMatrixBuffer()));
				pass.bindTexture("HordeDepthEven", depthPyramid.view(0), nearest);
				pass.bindTexture("HordeDepthOdd", depthPyramid.view(1), nearest);
			}
			if (pipeline == CLASSIFY) {
				pass.bindTexture("HordeLodSource", second, nearest);
			} else if (pipeline == COMPACT) {
				pass.bindTexture("HordeLodRanges", second, nearest);
			}
			pass.draw(0, 3);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
		}
	}

	public GpuTextureView selected() {
		return selected.view;
	}

	public GpuTextureView compact() {
		return compact.view;
	}

	@Override
	public void close() {
		for (var target : sorting) {
			target.close();
		}
		selected.close();
		compact.close();
		if (parameters != null) {
			parameters.close();
			parameters = null;
		}
		if (parameterUpload != null) {
			MemoryUtil.memFree(parameterUpload);
			parameterUpload = null;
		}
		if (materialRanges != null) {
			materialRanges.close();
			materialRanges = null;
		}
		previousRanges = null;
		depthPyramid.close();
		if (visibilityParameters != null) {
			visibilityParameters.close();
			visibilityParameters = null;
		}
		if (visibilityUpload != null) {
			MemoryUtil.memFree(visibilityUpload);
			visibilityUpload = null;
		}
	}

	private static class Target implements AutoCloseable {
		private GpuTexture texture;
		private GpuTextureView view;
		private int width;
		private int height;

		private void ensure(int nextWidth, int nextHeight) {
			if (texture != null && width == nextWidth && height == nextHeight) {
				return;
			}
			close();
			width = nextWidth;
			height = nextHeight;
			texture = RenderSystem.getDevice().createTexture(() -> "Horde LOD index table",
					GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
					TextureFormat.RGBA8, width, height, 1, 1);
			view = RenderSystem.getDevice().createTextureView(texture);
		}

		@Override
		public void close() {
			if (view != null) {
				view.close();
				view = null;
			}
			if (texture != null) {
				texture.close();
				texture = null;
			}
		}
	}
}
