package org.unitego.lobecorp.mixin.photon.runtime.client;

import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;


@Mixin(TimelinePlayer.class)
public abstract class TimelinePlayerMixin {
	@Shadow
	@Final
	private FXRuntime runtime;
	@Shadow
	private double lastEvalTime;
















	@Inject(method = {"evaluate", "frame"}, at = @At("RETURN"))
	private void lobecorp$previewParameters(CallbackInfo ci) {
		PhotonEditorTools.applyPreview(runtime);
	}

	@WrapMethod(method = "evaluate")
	private void lobecorp$captureEvaluation(double time, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.ANIMATION)) {
			original.call(time);
			return;
		}
		int node = PhotonPerformanceOwnerUtil.player((TimelinePlayer) (Object) this, runtime);
		long scope = PhotonPerformanceTraceUtil.begin(Stage.ANIMATION, node, time);
		boolean completed = false;
		try {
			original.call(time);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "frame")
	private void lobecorp$captureFrame(float partialTicks, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.ANIMATION_FRAME)) {
			original.call(partialTicks);
			return;
		}
		double time = lastEvalTime + partialTicks;
		int node = PhotonPerformanceOwnerUtil.player((TimelinePlayer) (Object) this, runtime);
		long scope = PhotonPerformanceTraceUtil.begin(Stage.ANIMATION_FRAME, node, time);
		boolean completed = false;
		try {
			original.call(partialTicks);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

}
