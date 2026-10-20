package org.unitego.lobecorp.util.photon.render;

import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.render.PhotonMaterialUniforms;
import com.lowdragmc.photon.client.render.PhotonRenderTypes;
import com.lowdragmc.photon.client.render.PhotonRenderTypes.PhotonDrawInfo;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.InstancedJob;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.Job;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.unitego.lobecorp.client.photon.render.PhotonOcclusionAccess;
import org.unitego.lobecorp.config.photon.PhotonOcclusionConfig;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class PhotonOcclusionUtil {
	/// 所有受支持的粒子材质都需要此原生纹理采样器。
	private static final String TEXTURE_SAMPLER = "Sampler0";
	/// 渲染器变体的调试名称；实际缓存按对象身份区分。
	private static final String RENDER_LABEL = "lobecorp_photon_occlusion";
	/// 每帧按对象身份记录，避免将属于不同发射器但值相同的记录混为一项。
	private static final Map<DrawJob, Entry> MARKS = new IdentityHashMap<>();
	/// 独立渲染类型防止原生合批混合已开启和未开启遮挡的发射器。
	private static final Map<RenderType, RenderType> WRAPPERS = new IdentityHashMap<>();
	/// 包装渲染类型到原生渲染类型的反向缓存，用于还原原始绘制来源。
	private static final Map<RenderType, RenderType> SOURCES = new IdentityHashMap<>();

	public static void mark(List<DrawJob> jobs, int start, RendererSetting.Runtime renderer) {
		if (!(renderer instanceof PhotonOcclusionAccess access) || !access.lobecorp$isOcclusionEnabled()) {
			return;
		}

		float cutoff = access.lobecorp$getOcclusionAlphaCutoff();

		for (int index = start; index < jobs.size(); index++) {
			DrawJob original = jobs.get(index);
			DrawJob replacement = original;
			RenderPipeline pipeline = null;

			if (original instanceof Job job) {
				var info = PhotonRenderTypes.drawInfo(job.renderType());

				if (!supports(job, info)) {
					continue;
				}

				pipeline = info.programs().main();
				replacement = new Job(wrapper(job.renderType(), info), job.mesh(), job.buffer(),
						job.stage(), job.orderInLayer(), job.distanceSq(), job.mask());
			} else if (original instanceof InstancedJob job) {
				if (!supports(job)) {
					continue;
				}

				pipeline = job.programs().main();
				replacement = new InstancedJob(new PhotonDrawInfo.Programs(PhotonOcclusionPipelineUtil.color(pipeline)),
						job.geometry(), job.bindings(), job.positionOffset(), job.blendEquation(),
						job.stage(), job.orderInLayer(), job.distanceSq(), job.variant(), job.mask());
			}

			if (pipeline == null) {
				continue;
			}

			jobs.set(index, replacement);
			MARKS.put(replacement, new Entry(PhotonOcclusionPipelineUtil.depth(pipeline), cutoff));
		}
	}

	/// 原生绑定持有自定义 Uniform 缓冲，检查时不得将其关闭。
	@SuppressWarnings("resource")
	private static boolean supports(Job job, @Nullable PhotonDrawInfo info) {
		return info != null && PhotonOcclusionPipelineUtil.supports(info.programs().main())
				&& info.bindings().graph() == null && info.bindings().customUniforms() == null
				&& info.bindings().textures().containsKey(TEXTURE_SAMPLER)
				&& PhotonMaterialUniforms.sliceFor(job.renderType()) != null;
	}

	private static boolean supports(InstancedJob job) {
		return PhotonOcclusionPipelineUtil.supports(job.programs().main())
				&& job.bindings().graph() == null && job.bindings().customSlice() == null
				&& job.bindings().textures().containsKey(TEXTURE_SAMPLER);
	}

	private static RenderType wrapper(RenderType original, PhotonDrawInfo info) {
		var cached = WRAPPERS.get(original);

		if (cached != null) {
			return cached;
		}

		var pipeline = PhotonOcclusionPipelineUtil.color(info.programs().main());
		var setup = RenderSetup.builder(pipeline).useLightmap();
		info.bindings().textures().forEach(setup::withTexture);
		var wrapper = RenderType.create(RENDER_LABEL, setup.createRenderSetup());
		PhotonRenderTypes.registerDrawInfo(wrapper, new PhotonDrawInfo(
				new PhotonDrawInfo.Programs(pipeline), info.bindings(), info.instanced()));
		WRAPPERS.put(original, wrapper);
		SOURCES.put(wrapper, original);
		return wrapper;
	}

	public static boolean isMarked(DrawJob job) {
		return MARKS.containsKey(job);
	}

	public static float cutoff(DrawJob job) {
		var entry = MARKS.get(job);
		return entry == null ? PhotonOcclusionConfig.DEFAULT_ALPHA_CUTOFF : entry.cutoff();
	}

	public static RenderType source(RenderType renderType) {
		return SOURCES.getOrDefault(renderType, renderType);
	}

	@Nullable
	public static RenderPipeline depthPipeline(DrawJob job) {
		var entry = MARKS.get(job);
		return entry == null ? null : entry.pipeline();
	}

	public static void clearFrame() {
		MARKS.clear();
	}

	public static void clear() {
		clearFrame();
		WRAPPERS.values().forEach(PhotonRenderTypes::dropCustomShader);
		WRAPPERS.clear();
		SOURCES.clear();
		PhotonOcclusionPipelineUtil.clear();
	}

	private record Entry(RenderPipeline pipeline, float cutoff) {
	}
}
