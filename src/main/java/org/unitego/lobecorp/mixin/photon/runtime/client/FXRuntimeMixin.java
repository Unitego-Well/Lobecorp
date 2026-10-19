package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;

@Mixin(FXRuntime.class)
public abstract class FXRuntimeMixin {







	@Inject(method = "emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;I)V", at = @At("RETURN"))
	private void lobecorp$previewAfterRestart(CallbackInfo ci) {
		PhotonEditorTools.applyPreview((FXRuntime) (Object) this);
	}


	@Unique
	private long lobecorp$profileGeneration;

	@WrapMethod(method = "emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;I)V")
	private void lobecorp$captureEmission(IEffectExecutor executor, int delay, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.FX_EMIT)) {
			original.call(executor, delay);
			return;
		}
		int node = PhotonPerformanceOwnerUtil.runtime((FXRuntime) (Object) this);
		PhotonPerformanceTraceUtil.generation(node, (int) ++lobecorp$profileGeneration);
		PhotonPerformanceTraceUtil.mark(Stage.FX_EMIT, node, "emit", "", lobecorp$profileGeneration, delay);
		long scope = PhotonPerformanceTraceUtil.begin(Stage.FX_EMIT, node);
		boolean completed = false;
		try {
			original.call(executor, delay);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "destroy")
	private void lobecorp$captureDestruction(boolean force, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.CLEANUP)) {
			original.call(force);
			return;
		}
		long scope = PhotonPerformanceTraceUtil.begin(Stage.CLEANUP, PhotonPerformanceOwnerUtil.runtime((FXRuntime) (Object) this));
		boolean completed = false;
		try {
			original.call(force);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}
}
