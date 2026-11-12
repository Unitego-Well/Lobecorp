package org.unitego.lobecorp.mixin.photon.render.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.render.IPhotonFXCollector.BakeTask;
import com.lowdragmc.photon.client.render.PhotonFXRenderState;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import org.unitego.lobecorp.util.photon.render.PhotonOcclusionUtil;
import org.unitego.lobecorp.util.photon.render.PhotonViewDepthSortUtil;

import java.util.List;


@Mixin(Emitter.class)
public abstract class EmitterMixin {


	@Inject(method = "bakeGroup(Lcom/lowdragmc/photon/client/render/PhotonViewSettings;Ljava/util/List;Lnet/minecraft/client/Camera;FLcom/lowdragmc/photon/client/gameobject/emitter/data/RendererSetting$Runtime;Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;Lcom/lowdragmc/photon/client/gameobject/emitter/Emitter$GeometryBaker;Lcom/lowdragmc/photon/client/gameobject/emitter/Emitter$CpuRecordSource;)V", at = @At("HEAD"))
	private void lobecorp$startCpuBatch(CallbackInfo ci, @Local(argsOnly = true) List<DrawJob> out,
	                                    @Share("lobecorp$compatStart") LocalIntRef start) {
		start.set(out.size());
	}

	@Inject(method = "bakeInstancedGroup", at = @At("HEAD"))
	private void lobecorp$startInstanceBatch(CallbackInfoReturnable<Boolean> ci, @Local(argsOnly = true) List<DrawJob> out,
	                                         @Share("lobecorp$compatStart") LocalIntRef start) {
		start.set(out.size());
	}

	@Inject(method = "bakeGroup(Lcom/lowdragmc/photon/client/render/PhotonViewSettings;Ljava/util/List;Lnet/minecraft/client/Camera;FLcom/lowdragmc/photon/client/gameobject/emitter/data/RendererSetting$Runtime;Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;Lcom/lowdragmc/photon/client/gameobject/emitter/Emitter$GeometryBaker;Lcom/lowdragmc/photon/client/gameobject/emitter/Emitter$CpuRecordSource;)V", at = @At("RETURN"))
	private void lobecorp$markCpuBatch(CallbackInfo ci, @Local(argsOnly = true) List<DrawJob> out,
	                                   @Local(argsOnly = true) Camera camera, @Local(argsOnly = true) RendererSetting.Runtime renderer,
	                                   @Share("lobecorp$compatStart") LocalIntRef start) {
		PhotonOcclusionUtil.mark(out, start.get(), renderer);
		PhotonViewDepthSortUtil.mark(out, start.get(), camera, ((Emitter) (Object) this).transform().position(), renderer);
	}

	@Inject(method = "bakeInstancedGroup", at = @At("RETURN"))
	private void lobecorp$markInstanceBatch(CallbackInfoReturnable<Boolean> ci, @Local(argsOnly = true) List<DrawJob> out,
	                                        @Local(argsOnly = true) Camera camera, @Local(argsOnly = true) RendererSetting.Runtime renderer,
	                                        @Share("lobecorp$compatStart") LocalIntRef start) {
		PhotonOcclusionUtil.mark(out, start.get(), renderer);
		PhotonViewDepthSortUtil.mark(out, start.get(), camera, ((Emitter) (Object) this).transform().position(), renderer);
	}

	@WrapOperation(method = "extractBatches", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/render/PhotonFXRenderState;defer(Lcom/lowdragmc/photon/client/render/IPhotonFXCollector$BakeTask;)V"))
	private void lobecorp$captureDeferredBake(PhotonFXRenderState state, BakeTask task, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.GEOMETRY)) {
			original.call(state, task);
			return;
		}
		original.call(state, (BakeTask) (settings, output) -> {
			var emitter = (Emitter) (Object) this;
			long scope = PhotonPerformanceTraceUtil.begin(Stage.GEOMETRY, PhotonPerformanceOwnerUtil.object(emitter));
			int start = output.size();
			boolean completed = false;
			try {
				task.bake(settings, output);
				completed = true;
			} finally {
				if (PhotonPerformanceTraceUtil.active()) {
					PhotonPerformanceOwnerUtil.baked(emitter, output, start);
				}
				PhotonPerformanceTraceUtil.end(scope, completed);
			}
		});
	}
}
