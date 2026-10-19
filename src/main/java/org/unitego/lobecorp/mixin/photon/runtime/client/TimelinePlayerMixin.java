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
import org.unitego.lobecorp.util.photon.model.PhotonModelClipUtil;
import com.lowdragmc.photon.client.fx.timeline.Timeline;


@Mixin(TimelinePlayer.class)
public abstract class TimelinePlayerMixin {
	@Shadow
	@Final
	private FXRuntime runtime;
	@Shadow
	private double lastEvalTime;
	@Shadow
	@Final
	private Timeline timeline;
















	@Inject(method = "evaluate", at = @At("RETURN"))
	private void lobecorp$previewParameters(double time, CallbackInfo ci) {
		PhotonModelClipUtil.apply(this, runtime, timeline, time, PhotonModelClipUtil.SCENE_PRIORITY, 0);
		PhotonEditorTools.applyPreview(runtime);
	}

	@Inject(method = "frame", at = @At("RETURN"))
	private void lobecorp$previewFrameParameters(float partialTicks, CallbackInfo ci) {
		PhotonModelClipUtil.apply(this, runtime, timeline, lastEvalTime + partialTicks, PhotonModelClipUtil.SCENE_PRIORITY, 0);
		PhotonEditorTools.applyPreview(runtime);
	}

	@Inject(method = "stop", at = @At("RETURN"))
	private void lobecorp$clearModelPlayback(CallbackInfo ci) {
		PhotonModelClipUtil.clear(this, runtime);
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
