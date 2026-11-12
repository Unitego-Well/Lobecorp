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
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.HordePathVariant;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

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
	private static final RenderPipeline SELECTED_SCATTER = pipeline("horde_lod_selected_scatter", "HordeLodSource");
	private static final RenderPipeline COMPACT = pipeline("horde_lod_compact", "HordeLodSource", "HordeLodRanges");
	private static final RenderPipeline COMPACT_SCATTER = pipeline("horde_lod_compact_scatter", "HordeLodSource", "HordeLodRanges");
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
			return !HordePathVariant.sceneOcclusion() || HordeDepthPyramid.precompile();
		}

		var pipeline = switch (step) {
			case 0 -> CANDIDATES;
			case 1 -> SORT;
			case 2 -> MERGE;
			case 3 -> HordeRenderPolicy.selectedScatter() ? SELECTED_SCATTER : CLASSIFY;
			case 4 -> HordeRenderPolicy.selectedScatter() ? COMPACT_SCATTER : COMPACT;
			default -> throw new IllegalArgumentException("群体LOD预热步骤无效");
		};
		return RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
	}

	private static RenderPipeline pipeline(String shader, String... samplers) {
		boolean scatterCompact = shader.equals("horde_lod_compact_scatter");
		String fragment = scatterCompact ? "horde_lod_compact" : shader;
		var builder = RenderPipeline.builder()
				.withLocation(Lobecorp.id("pipeline/" + shader))
				.withVertexShader(Lobecorp.id(shader.equals("horde_lod_selected_scatter") ? "core/horde_lod_selected_scatter" : "core/horde_motion"))
				.withFragmentShader(Lobecorp.id("core/" + fragment))
				.withUniform("HordeLodParameters", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(DefaultVertexFormat.EMPTY, shader.equals("horde_lod_selected_scatter") ? VertexFormat.Mode.POINTS : VertexFormat.Mode.TRIANGLES)
				.withCull(false).withColorTargetState(ColorTargetState.DEFAULT);
		if (scatterCompact) {
			builder.withShaderDefine("HORDE_SELECTED_SCATTER");
		}
		for (var sampler : samplers) {
			builder.withSampler(sampler);
		}
		if (shader.equals("horde_lod_candidates")) {
			if (!HordePathVariant.sceneOcclusion()) {
				builder.withShaderDefine("HORDE_DISABLE_OCCLUSION");
			}
			builder.withUniform("HordeVisibility", UniformType.UNIFORM_BUFFER);
			builder.withUniform("Projection", UniformType.UNIFORM_BUFFER);
		}
		return builder.build();
	}

	public void select(GpuTextureView motion, int count, int materials, int[] ranges,
	                   Matrix4fc modelView, float radius, GpuTextureView depth, boolean reverse) {
		select(motion, count, materials, ranges, modelView, radius, depth, reverse, false);
	}

	public void select(GpuTextureView motion, int count, int materials, int[] ranges,
	                   Matrix4fc modelView, float radius, GpuTextureView depth, boolean reverse,
	                   boolean optimizedMerge) {
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
		if (HordePathVariant.sceneOcclusion()) {
			depthPyramid.prepare(depth, reverse);
		}
		visibilityUpload.clear();
		modelView.get(visibilityUpload);
		visibilityUpload.position(64);
		visibilityUpload.putFloat(radius).putFloat(reverse ? 1 : 0)
				.putFloat(RenderSystem.getDevice().isZZeroToOne() ? 1 : 0).putFloat(HordePathVariant.sceneOcclusion() ? depthPyramid.levels() : 1);
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
			write(width, remaining, optimizedMerge ? 1 : 0, 0, 0);
			render(MERGE, sorting[1 - current], sorting[current].view, null);
			current = 1 - current;
			if (optimizedMerge) {
				continue;
			}
			for (int step = CAPACITY / 2; step > 0; step /= 2) {
				write(width, remaining, 0, step, 0);
				render(SORT, sorting[1 - current], sorting[current].view, null);
				current = 1 - current;
			}
		}
		// 记录候选测试工作量，不代表最终选中人数。
		if (HordeRenderPolicy.selectedScatter()) {
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.LOD_SELECTED_TESTS, CAPACITY);
			write(selected.width, count, selected.height, 0, 0);
			render(SELECTED_SCATTER, selected, sorting[current].view, null);
		} else {
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.LOD_SELECTED_TESTS, count);
			write(width, count, 0, 0, HordeGpuRenderer.motionValues());
			render(CLASSIFY, selected, motion, sorting[current].view);
		}
		if (!Arrays.equals(previousRanges, ranges)) {
			if (HordePathVariant.optimized()) {
				var image = materialRanges == null ? null : materialRanges.texture().getPixels();
				long pixels = image == null ? 0 : (long) image.getWidth() * image.getHeight();
				if (materialRanges == null || ranges.length > pixels) {
					// 按物理像素容量增长；上限附近退回精确长度，避免越过设备高度限制。
					long limit = 1024L * RenderSystem.getDevice().getMaxTextureSize();
					if (ranges.length > limit) {
						throw new IllegalArgumentException("群体数据纹理超过设备尺寸：Horde LOD material ranges");
					}
					long grown = Math.max(ranges.length, pixels + Math.max(1, pixels / 2));
					int allocation = (int) Math.min(Integer.MAX_VALUE, Math.min(limit, grown));
					var next = new HordeDataTexture("Horde LOD material ranges", Arrays.copyOf(ranges, allocation));
					if (materialRanges != null) {
						materialRanges.close();
					}
					materialRanges = next;
				} else {
					materialRanges.update(0, ranges, 0, ranges.length);
				}
			} else {
				if (materialRanges != null) {
					materialRanges.close();
				}
				materialRanges = new HordeDataTexture("Horde LOD material ranges", ranges);
			}
			// Shader 仅消费 LodRange.z 个三元组，缩短后不读取旧尾部。
			if (previousRanges == null || previousRanges.length != ranges.length) {
				previousRanges = ranges.clone();
			} else {
				System.arraycopy(ranges, 0, previousRanges, 0, ranges.length);
			}
		}
		write(CAPACITY, materials, ranges.length / 3, optimizedMerge ? 1 : 0, HordeRenderPolicy.selectedScatter() ? count : 0);
		render(HordeRenderPolicy.selectedScatter() ? COMPACT_SCATTER : COMPACT, compact,
				sorting[current].view, materialRanges.texture().getTextureView());
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
				() -> "Horde global camera LOD", output.view, pipeline == SELECTED_SCATTER ? OptionalInt.of(0) : OptionalInt.empty())) {
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
				if (HordePathVariant.sceneOcclusion()) {
					pass.bindTexture("HordeDepthEven", depthPyramid.view(0), nearest);
					pass.bindTexture("HordeDepthOdd", depthPyramid.view(1), nearest);
				}
			}
			if (pipeline == CLASSIFY) {
				pass.bindTexture("HordeLodSource", second, nearest);
			} else if (pipeline == COMPACT || pipeline == COMPACT_SCATTER) {
				pass.bindTexture("HordeLodRanges", second, nearest);
			}
			pass.draw(0, pipeline == SELECTED_SCATTER ? CAPACITY : 3);
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
			if (texture != null && view != null && width == nextWidth && height == nextHeight) {
				return;
			}
			close();
			var device = RenderSystem.getDevice();
			var nextTexture = device.createTexture(() -> "Horde LOD index table",
					GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT
							| (HordePathVariant.layered() ? GpuTexture.USAGE_COPY_SRC : 0),
					TextureFormat.RGBA8, nextWidth, nextHeight, 1, 1);
			GpuTextureView nextView;
			try {
				nextView = device.createTextureView(nextTexture);
			} catch (RuntimeException | OutOfMemoryError failure) {
				nextTexture.close();
				throw failure;
			}
			// 完整创建成功后再提交，避免下一帧复用半初始化目标。
			texture = nextTexture;
			view = nextView;
			width = nextWidth;
			height = nextHeight;
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
