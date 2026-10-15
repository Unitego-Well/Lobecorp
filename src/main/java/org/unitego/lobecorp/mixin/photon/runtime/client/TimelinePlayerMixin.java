package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.Opcodes;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationRuntimeAccess;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPreviewScope;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonChildAnimationPlayer;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPlayerAccess;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.util.photon.model.PhotonModelAnimationTimelineUtil;
import org.unitego.lobecorp.util.photon.runtime.PhotonActivatorTimelineUtil;
import org.unitego.lobecorp.util.photon.runtime.PhotonTimelineTraversalUtil;
import org.unitego.lobecorp.util.photon.runtime.PhotonEmissionPrimeUtil;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;

import java.util.List;

@Mixin(TimelinePlayer.class)
public abstract class TimelinePlayerMixin {
	@Shadow
	@Final
	private FXRuntime runtime;
	@Shadow
	@Final
	private Timeline timeline;
	@Shadow
	private boolean stopped;
	@Unique
	private final PhotonActivatorTimelineUtil lobecorp$activation = new PhotonActivatorTimelineUtil();
	@Unique
	private double lobecorp$previousTraversal = Double.NaN;
	@Unique
	private boolean lobecorp$traversing;

	@WrapMethod(method = "evaluate")
	private void lobecorp$crossedEvents(double time, Operation<Void> original) {
		var access = (PhotonAnimationPlayerAccess) this;
		var current = access.lobecorp$sampleTime(time);
		if (lobecorp$traversing) {
			original.call(time);
			return;
		}

		lobecorp$traversing = true;
		var clock = access.lobecorp$localTime();
		try {
			if (PhotonEditorTools.enabled(runtime, PhotonEditorTextUtil.TIMELINE_EVENT_TRAVERSAL_FEATURE)) {
				for (var boundary : PhotonTimelineTraversalUtil.boundaries(timeline, lobecorp$previousTraversal, current)) {
					access.lobecorp$evaluateAt(boundary);
					PhotonEmissionPrimeUtil.primeSubtree(runtime.getRoot());
				}
			}

			access.lobecorp$localTime(clock);
			original.call(time);
			lobecorp$previousTraversal = current;
		} finally {
			access.lobecorp$localTime(clock);
			lobecorp$traversing = false;
		}
	}

	@ModifyExpressionValue(method = "evaluate", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/timeline/Timeline;leafTracks(Z)Ljava/util/List;"))
	private List<Track> lobecorp$intervalActivation(List<Track> tracks) {
		return tracks.stream().filter(track -> !(track instanceof ActivatorTrack)
				|| PhotonActivatorTimelineUtil.nativeInterval(track)).toList();
	}

	@ModifyExpressionValue(method = "dispatchSignals", at = @At(value = "FIELD",
			target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;dispatchSignals:Z", opcode = Opcodes.GETFIELD))
	private boolean lobecorp$internalReplaySignals(boolean enabled) {
		var animation = ((PhotonAnimationPlayerAccess) this).lobecorp$animation();
		return enabled || PhotonAnimationPreviewScope.active() && animation == null;
	}

	@WrapOperation(method = "dispatchSignals", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/timeline/PhotonSignals;fire(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/nbt/CompoundTag;D)V"))
	private void lobecorp$quietGlobalSignal(IEffectExecutor effect, String channel, String name,
	                                        CompoundTag data, double time, Operation<Void> original) {
		if (lobecorp$externalSignals()) {
			original.call(effect, channel, name, data, time);
		}
	}

	@Unique
	@SuppressWarnings("resource") // 只查询宿主控制器，不接管生命周期。
	private boolean lobecorp$externalSignals() {
		if (PhotonAnimationPreviewScope.active()) {
			return false;
		}

		if (runtime instanceof PhotonAnimationRuntimeAccess access) {
			var animations = access.lobecorp$animations();
			return animations == null || animations.dispatching();
		}

		return true;
	}

	@WrapOperation(method = "dispatchSignals", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/IEffectExecutor;onTimelineSignal(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/nbt/CompoundTag;D)V"))
	@SuppressWarnings("resource") // 回调只借用 FXRuntime 所拥有的控制器。
	private void lobecorp$forwardSignal(IEffectExecutor executor, String channel, String name,
	                                    CompoundTag data, double time, Operation<Void> original) {
		if (runtime instanceof PhotonAnimationRuntimeAccess access) {
			var animations = access.lobecorp$animations();
			if (animations != null) {
				var source = (PhotonAnimationPlayerAccess) this;
				animations.signal(source.lobecorp$animation(), channel, name, data);
				if (!animations.dispatching()) {
					return;
				}
			}
		}

		if (!PhotonAnimationPreviewScope.active()) {
			original.call(executor, channel, name, data, time);
		}
	}

	@Inject(method = "begin", at = @At("HEAD"))
	@SuppressWarnings("DataFlowIssue") // Mixin 合并后 this 是目标 TimelinePlayer。
	private void lobecorp$resetActivation(CallbackInfo ci) {
		lobecorp$previousTraversal = Double.NaN;
		PhotonChildAnimationPlayer.stop((TimelinePlayer) (Object) this);
		PhotonModelAnimationTimelineUtil.stop((TimelinePlayer) (Object) this);
		lobecorp$activation.reset();
	}

	@Invoker("restart")
	protected abstract void lobecorp$restartActivation(FXObject object, Clip clip);

	@Inject(method = "evaluate", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;applyAnimations(D)V"))
	private void lobecorp$activationState(double time, CallbackInfo ci) {
		var exactTime = ((PhotonAnimationPlayerAccess) this).lobecorp$sampleTime(time);
		lobecorp$activation.evaluate(runtime, timeline, exactTime, this::lobecorp$restartActivation);
	}

	@Inject(method = "restart", at = @At("HEAD"), cancellable = true)
	private void lobecorp$guardOwnedRestart(FXObject object, Clip clip, CallbackInfo ci) {
		if (lobecorp$resetOverridden(object)) {
			ci.cancel();
		}
	}

	@Inject(method = "resetSubtree", at = @At("HEAD"), cancellable = true)
	private void lobecorp$guardOwnedSubtree(FXObject object, long seed, CallbackInfo ci) {
		if (lobecorp$resetOverridden(object)) {
			ci.cancel();
		}
	}

	@Unique
	@SuppressWarnings("resource") // 只借用 FXRuntime 拥有的状态协调器。
	private boolean lobecorp$resetOverridden(FXObject object) {
		if (!(runtime instanceof PhotonAnimationRuntimeAccess access)) {
			return false;
		}

		var animations = access.lobecorp$animations();
		return animations != null && !animations.timelineState().allowsReset(timeline, object.id());
	}

	@Inject(method = "evaluate", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;dispatchSignals(Ljava/util/List;D)V"))
	@SuppressWarnings("DataFlowIssue") // Mixin 合并后 this 是目标 TimelinePlayer。
	private void lobecorp$childAnimations(double time, CallbackInfo ci) {
		var exactTime = ((PhotonAnimationPlayerAccess) this).lobecorp$sampleTime(time);
		PhotonChildAnimationPlayer.evaluate((TimelinePlayer) (Object) this, runtime, timeline, exactTime);

		if (!stopped) {
			PhotonModelAnimationTimelineUtil.evaluate((TimelinePlayer) (Object) this, runtime, timeline, exactTime);
		}
	}

	@Inject(method = "frame", at = @At("RETURN"))
	@SuppressWarnings("DataFlowIssue") // Mixin 合并后 this 是目标 TimelinePlayer。
	private void lobecorp$childAnimationFrames(float partialTicks, CallbackInfo ci) {
		var access = (PhotonAnimationPlayerAccess) this;
		var time = access.lobecorp$sampleTime(access.lobecorp$lastEvalTime() + partialTicks);
		PhotonChildAnimationPlayer.frame((TimelinePlayer) (Object) this, runtime, timeline, time);

		if (!stopped) {
			PhotonModelAnimationTimelineUtil.evaluate((TimelinePlayer) (Object) this, runtime, timeline, time);
		}
	}

	@Inject(method = "stop", at = @At("HEAD"))
	@SuppressWarnings("DataFlowIssue") // Mixin 合并后 this 是目标 TimelinePlayer。
	private void lobecorp$releaseChildAnimations(CallbackInfo ci) {
		PhotonChildAnimationPlayer.stop((TimelinePlayer) (Object) this);
		PhotonModelAnimationTimelineUtil.stop((TimelinePlayer) (Object) this);
	}

	@Inject(method = {"evaluate", "frame"}, at = @At("RETURN"))
	private void lobecorp$previewParameters(CallbackInfo ci) {
		PhotonEditorTools.applyPreview(runtime);
	}
}
