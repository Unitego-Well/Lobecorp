package org.unitego.lobecorp.mixin.photon.render.client;

import com.lowdragmc.photon.client.render.PhotonFXRenderState;
import com.lowdragmc.photon.client.render.IPhotonFXCollector.BakeTask;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;




@Mixin(Emitter.class)
public abstract class EmitterMixin {



















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
