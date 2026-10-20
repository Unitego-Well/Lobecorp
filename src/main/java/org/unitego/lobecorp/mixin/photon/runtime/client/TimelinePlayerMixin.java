package org.unitego.lobecorp.mixin.photon.runtime.client;

import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.unitego.lobecorp.client.photon.playback.PhotonActivationOverrideAccess;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
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
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.playback.PhotonChildWindows;
import org.unitego.lobecorp.client.photon.playback.PhotonTimelineStateAccess;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildTrack;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import org.unitego.lobecorp.client.photon.playback.PhotonActivationTrackAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedGates;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions;

import javax.annotation.Nullable;


@Mixin(TimelinePlayer.class)
public abstract class TimelinePlayerMixin implements PhotonTimelineStateAccess {
	@Shadow
	@Final
	private FXRuntime runtime;
	@Shadow
	private double lastEvalTime;
	@Shadow
	@Final
	private Timeline timeline;
	@Shadow
	private boolean recording;
	@Shadow
	@Final
	private Set<UUID> lastControlled;

	@Override
	public boolean lobecorp$recording() {
		return recording;
	}

	@Unique
	private @Nullable PhotonChildWindows lobecorp$children;
	@Unique
	private @Nullable PhotonNamedGates lobecorp$activation;

	@WrapOperation(method = "evaluate", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/timeline/Timeline;leafTracks(Z)Ljava/util/List;"))
	private List<Track> lobecorp$nativeIntervals(Timeline timeline, boolean muted, Operation<List<Track>> original) {
		var leaves = original.call(timeline, muted);
		if (leaves.stream().noneMatch(track -> track instanceof ActivatorTrack && !lobecorp$interval(track))) {
			return leaves;
		}

		return leaves.stream().filter(track -> !(track instanceof ActivatorTrack) || lobecorp$interval(track)).toList();
	}

	@Unique
	private static boolean lobecorp$interval(Track track) {
		var options = ((PhotonActivationTrackAccess) track).lobecorp$activationOptions();
		return options.startControl() && options.endControl();
	}

	@Inject(method = "resetSubtree", at = @At("HEAD"), cancellable = true)
	private void lobecorp$protectNamedChild(FXObject object, long seed, CallbackInfo ci) {
		var owner = ((PhotonActivationOverrideAccess) object).lobecorp$activationOwner();
		if (owner instanceof PhotonNamedGates gates && gates.named()) {
			ci.cancel();
		}
	}

	@Inject(method = "evaluate", at = @At("HEAD"))
	private void lobecorp$releaseActivation(double time, CallbackInfo ci) {
		if (lobecorp$activation != null) {
			lobecorp$activation.begin();
		}
	}

	@Inject(method = "begin", at = @At("HEAD"))
	private void lobecorp$restartChildren(CallbackInfo ci) {
		lobecorp$clearActivation();
		if (lobecorp$children != null) {
			lobecorp$children.close();
			lobecorp$children = null;
		}
	}
















	@Inject(method = "evaluate", at = @At("RETURN"))
	private void lobecorp$previewParameters(double time, CallbackInfo ci) {
		var leaves = timeline.leafTracks(false);
		boolean extended = !recording && leaves.stream().anyMatch(track -> track instanceof ActivatorTrack && !track.mute()
				&& !((PhotonActivationTrackAccess) track).lobecorp$activationOptions().equals(PhotonActivationOptions.NATIVE));

		if (extended) {
			if (lobecorp$activation == null) {
				lobecorp$activation = new PhotonNamedGates(runtime, false);
			}

			lobecorp$activation.collect(leaves, FXRuntime.ROOT_UUID, 0, time, false);
			lobecorp$activation.commit();
		} else {
			lobecorp$clearActivation();
		}

		var named = ((PhotonNamedAccess) runtime).lobecorp$namedRuntime();
		if (named != null) {
			named.captureNativeState(lastControlled, lobecorp$activation);
		}

		if (lobecorp$children != null || leaves.stream().anyMatch(PhotonChildTrack.class::isInstance)) {
			if (lobecorp$children == null) {
				lobecorp$children = new PhotonChildWindows(runtime, null);
			}

			lobecorp$children.evaluate(leaves, time);
		}

		PhotonModelClipUtil.apply(this, runtime, timeline, time, PhotonModelClipUtil.SCENE_PRIORITY, 0);
		PhotonEditorTools.applyPreview(runtime);
	}

	@Inject(method = "frame", at = @At("RETURN"))
	private void lobecorp$previewFrameParameters(float partialTicks, CallbackInfo ci) {
		if (recording) {
			return;
		}

		if (lobecorp$children != null) {
			lobecorp$children.frame(lastEvalTime + partialTicks);
		}

		PhotonModelClipUtil.apply(this, runtime, timeline, lastEvalTime + partialTicks, PhotonModelClipUtil.SCENE_PRIORITY, 0);
		PhotonEditorTools.applyPreview(runtime);
	}

	@Inject(method = "stop", at = @At("RETURN"))
	private void lobecorp$clearModelPlayback(CallbackInfo ci) {
		lobecorp$clearActivation();
		if (lobecorp$children != null) {
			lobecorp$children.close();
			lobecorp$children = null;
		}

		PhotonModelClipUtil.clear(this, runtime);
	}

	@Unique
	private void lobecorp$clearActivation() {
		if (lobecorp$activation != null) {
			lobecorp$activation.clear();
			lobecorp$activation = null;
		}
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
