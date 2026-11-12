package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.*;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.gui.editor.FXProjectEffectExecutor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonAuthoringPose;
import org.unitego.lobecorp.client.photon.playback.*;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildTrack;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import org.unitego.lobecorp.util.photon.playback.PhotonRuntimePreviewUtil;

import javax.annotation.Nullable;
import java.util.*;


@Mixin(TimelinePlayer.class)
public abstract class TimelinePlayerMixin implements PhotonTimelineStateAccess {
	@Unique
	private final Map<FXObject, PhotonAuthoringPose[]> lobecorp$authoredPoses = new IdentityHashMap<>();
	@Unique
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
	@Unique
	private @Nullable PhotonChildWindows lobecorp$children;
	@Unique
	private @Nullable PhotonNamedGates lobecorp$activation;
	@Unique
	private @Nullable List<PhotonChildTrack> lobecorp$childLeaves;

	@Unique
	private static boolean lobecorp$interval(Track track) {
		var options = ((PhotonActivationTrackAccess) track).lobecorp$activationOptions();
		return options.startControl() && options.endControl();
	}

	@Override
	public PhotonAuthoringPose lobecorp$authoredPose(FXObject object, PhotonAuthoringPose current) {
		var state = lobecorp$authoredPoses.get(object);
		return recording || state == null ? current : current.authored(state[0], state[1]);
	}

	@WrapOperation(method = "applyAnimations", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/timeline/AnimatedProperty;apply(Lcom/lowdragmc/photon/client/gameobject/FXObject;D)V"))
	private void lobecorp$rememberAuthoredPose(AnimatedProperty property, FXObject target, double time, Operation<Void> original) {
		if (recording || !(target.getEffectExecutor() instanceof FXProjectEffectExecutor)) {
			original.call(property, target, time);
			return;
		}
		var current = PhotonAuthoringPose.capture(target);
		var previous = lobecorp$authoredPoses.get(target);
		var base = previous == null ? current : current.authored(previous[0], previous[1]);
		original.call(property, target, time);
		lobecorp$authoredPoses.put(target, new PhotonAuthoringPose[]{base, PhotonAuthoringPose.capture(target)});
	}

	@Override
	public boolean lobecorp$recording() {
		return recording;
	}

	@WrapOperation(method = "evaluate", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/timeline/Timeline;leafTracks(Z)Ljava/util/List;"))
	private List<Track> lobecorp$nativeIntervals(Timeline timeline, boolean muted, Operation<List<Track>> original) {
		var leaves = original.call(timeline, muted);
		if (leaves.stream().noneMatch(track -> track instanceof ActivatorTrack && !lobecorp$interval(track))) {
			return leaves;
		}

		return leaves.stream().filter(track -> !(track instanceof ActivatorTrack) || lobecorp$interval(track)).toList();
	}

	@Inject(method = "resetSubtree", at = @At("HEAD"), cancellable = true)
	private void lobecorp$protectNamedChild(FXObject object, long seed, CallbackInfo ci) {
		var owner = ((PhotonActivationOverrideAccess) object).lobecorp$activationOwner();
		if (owner instanceof PhotonNamedGates gates && gates.named()) {
			ci.cancel();
		}
	}

	@Inject(method = "evaluate(DZ)V", at = @At("HEAD"))
	private void lobecorp$releaseActivation(double time, boolean live, CallbackInfo ci) {
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

		lobecorp$childLeaves = null;
	}


	@Inject(method = "evaluate(DZ)V", at = @At("RETURN"))
	private void lobecorp$previewParameters(double time, boolean live, CallbackInfo ci) {
		if (!live) {
			return;
		}

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

			// mutable scene timeline 无可靠 revision，保留每次真实读取后的过滤。
			lobecorp$childLeaves = leaves.stream().filter(PhotonChildTrack.class::isInstance)
					.map(PhotonChildTrack.class::cast).toList();
			lobecorp$children.evaluate(lobecorp$childLeaves, time);
		}

		PhotonRuntimePreviewUtil.apply(runtime);
	}

	@Inject(method = "frame", at = @At("RETURN"))
	private void lobecorp$previewFrameParameters(float partialTicks, CallbackInfo ci) {
		if (recording) {
			return;
		}

		if (lobecorp$children != null) {
			lobecorp$children.frame(lastEvalTime + partialTicks);
		}

		PhotonRuntimePreviewUtil.apply(runtime);
	}

	@Inject(method = "stop", at = @At("RETURN"))
	private void lobecorp$clearModelPlayback(CallbackInfo ci) {
		if (!recording) {
			lobecorp$authoredPoses.forEach((object, state) -> PhotonAuthoringPose.capture(object).authored(state[0], state[1]).apply(object));
		}
		lobecorp$authoredPoses.clear();
		lobecorp$clearActivation();
		if (lobecorp$children != null) {
			lobecorp$children.close();
			lobecorp$children = null;
		}

		lobecorp$childLeaves = null;
	}

	@Unique
	private void lobecorp$clearActivation() {
		if (lobecorp$activation != null) {
			lobecorp$activation.clear();
			lobecorp$activation = null;
		}
	}

	@WrapMethod(method = "evaluate(DZ)V")
	private void lobecorp$captureEvaluation(double time, boolean live, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.ANIMATION)) {
			original.call(time, live);
			return;
		}
		int node = PhotonPerformanceOwnerUtil.player((TimelinePlayer) (Object) this, runtime);
		long scope = PhotonPerformanceTraceUtil.begin(Stage.ANIMATION, node, time);
		boolean completed = false;
		try {
			original.call(time, live);
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
