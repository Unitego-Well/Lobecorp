package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.render.IPhotonFXCollector;
import com.lowdragmc.photon.client.render.PhotonStage;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.InstancedJob;
import com.lowdragmc.photon.client.render.PhotonBufferCache;
import com.lowdragmc.photon.client.postfx.runtime.PostEffectStack;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.performance.PhotonPerformanceGpu;
import org.unitego.lobecorp.client.photon.performance.PhotonSubmissionDiagnostics;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import org.unitego.lobecorp.client.photon.render.PhotonIrisPipelineAccess;

@Mixin(PhotonWorldRenderState.class)
public class PhotonWorldRenderStateMixin {
	@WrapMethod(method = "drain(Lcom/lowdragmc/photon/client/render/IPhotonFXCollector;Lcom/lowdragmc/photon/client/render/PhotonStage;)V")
	private static void lobecorp$captureDrain(IPhotonFXCollector collector, PhotonStage stage, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.DRAW)) {
			original.call(collector, stage);
			return;
		}
		long scope = PhotonPerformanceTraceUtil.begin(Stage.DRAW, 0);
		long gpu = PhotonPerformanceGpu.begin(stage.name(), 0);
		boolean completed = false;
		try {
			original.call(collector, stage);
			completed = true;
		} finally {
			PhotonPerformanceGpu.end(gpu, completed);
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "drain(Ljava/util/List;ZLcom/lowdragmc/photon/client/postfx/runtime/PostEffectStack;ZZZ)V")
	private static void lobecorp$captureBatchOwners(List<DrawJob> jobs, boolean bloom, PostEffectStack stack,
	                                                boolean last, boolean layer, boolean opaque, Operation<Void> original) {
		var diagnostic = PhotonSubmissionDiagnostics.beginDrain();
		boolean completed = false;

		try {
			if (!PhotonPerformanceTraceUtil.enabled(Stage.DRAW)) {
				original.call(jobs, bloom, stack, last, layer, opaque);
				completed = true;
				return;
			}

			PhotonPerformanceOwnerUtil.beginDraws(jobs);

			try {
				original.call(jobs, bloom, stack, last, layer, opaque);
				completed = true;
			} finally {
				PhotonPerformanceOwnerUtil.endDraws();
			}
		} finally {
			if (diagnostic != null) {
				PhotonSubmissionDiagnostics.endDrain(diagnostic, completed);
			}
		}
	}

	@WrapMethod(method = "drawRun")
	private static void lobecorp$captureMergedBatch(PhotonBufferCache cache, @Coerce Object run, RenderPipeline pipeline,
	                                                GpuTextureView color, GpuTextureView depth,
	                                                GpuTextureView sceneColor, GpuTextureView sceneDepth,
	                                                Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.DRAW)) {
			runDraw(pipeline, () -> original.call(cache, run, pipeline, color, depth, sceneColor, sceneDepth));
			return;
		}
		int owner = PhotonPerformanceOwnerUtil.drawOwner(((PhotonDrawRunAccessor) run).lobecorp$renderType());
		String pass = "merged_batch:" + pipeline;
		int node = PhotonPerformanceTraceUtil.namedNode(owner, owner == 0 ? "shared_batch" : "object_batch",
				pass, pass, pipeline.toString());
		long scope = PhotonPerformanceTraceUtil.begin(Stage.DRAW, node);
		long gpu = PhotonPerformanceGpu.begin(pass, node, owner == 0);
		boolean completed = false;
		try {
			runDraw(pipeline, () -> original.call(cache, run, pipeline, color, depth, sceneColor, sceneDepth));
			completed = true;
		} finally {
			PhotonPerformanceGpu.end(gpu, completed);
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "drawInstancedJob")
	private static void lobecorp$captureInstancedBatch(InstancedJob job, RenderPipeline pipeline,
	                                                   GpuTextureView color, GpuTextureView depth,
	                                                   GpuTextureView sceneColor, GpuTextureView sceneDepth,
	                                                   Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.DRAW)) {
			runDraw(pipeline, () -> original.call(job, pipeline, color, depth, sceneColor, sceneDepth));
			return;
		}
		int owner = PhotonPerformanceOwnerUtil.jobOwner(job);
		String pass = "instanced_batch:" + pipeline;
		int node = PhotonPerformanceTraceUtil.namedNode(owner, owner == 0 ? "shared_batch" : "object_batch",
				pass, pass, pipeline.toString());
		long scope = PhotonPerformanceTraceUtil.begin(Stage.DRAW, node);
		long gpu = PhotonPerformanceGpu.begin(pass, node, owner == 0);
		boolean completed = false;
		try {
			runDraw(pipeline, () -> original.call(job, pipeline, color, depth, sceneColor, sceneDepth));
			completed = true;
		} finally {
			PhotonPerformanceGpu.end(gpu, completed);
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@Unique
	private static void runDraw(RenderPipeline pipeline, Runnable draw) {
		if (PhotonIrisPipelineAccess.isPhotonPipeline(pipeline)) {
			PhotonIrisPipelineAccess.useNativePipeline(draw);
		} else {
			draw.run();
		}
	}
}
