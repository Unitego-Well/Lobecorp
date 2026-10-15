package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationSamplingScope;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPlayerAccess;

import java.util.List;
import java.util.UUID;

/// 仅暴露原播放器时钟，不复制任何轨道执行逻辑。
@Mixin(TimelinePlayer.class)
public abstract class PhotonAnimationPlayerMixin implements PhotonAnimationPlayerAccess {
	@Unique
	private @Nullable String lobecorp$animation;
	@Unique
	private double lobecorp$exactTime;
	@Unique
	private boolean lobecorp$exactTimeActive;

	@Override
	@Invoker("evaluate")
	public abstract void lobecorp$evaluate(double time);

	@Override
	@Accessor("localTime")
	public abstract void lobecorp$localTime(double time);

	@Override
	@Accessor("localTime")
	public abstract double lobecorp$localTime();

	@Override
	@Accessor("lastEvalTime")
	public abstract double lobecorp$lastEvalTime();

	@Override
	@Accessor("lastEvalTime")
	public abstract void lobecorp$lastEvalTime(double time);

	@Override
	@Accessor("audioEnabled")
	public abstract boolean lobecorp$audioEnabled();

	@Invoker("frame")
	public abstract void lobecorp$frame(float partialTicks);

	@Override
	public void lobecorp$evaluateAt(double time) {
		var previousTime = lobecorp$exactTime;
		var previousActive = lobecorp$exactTimeActive;
		lobecorp$exactTime = time;
		lobecorp$exactTimeActive = true;
		try {
			lobecorp$evaluate(time);
			lobecorp$localTime(time + 1);
		} finally {
			lobecorp$exactTime = previousTime;
			lobecorp$exactTimeActive = previousActive;
		}
	}

	@Override
	public void lobecorp$frameAt(double time) {
		var previousTime = lobecorp$exactTime;
		var previousActive = lobecorp$exactTimeActive;
		lobecorp$exactTime = time;
		lobecorp$exactTimeActive = true;
		try {
			lobecorp$frame(0);
		} finally {
			lobecorp$exactTime = previousTime;
			lobecorp$exactTimeActive = previousActive;
		}
	}

	@Override
	public double lobecorp$sampleTime(double fallback) {
		return lobecorp$exactTimeActive ? lobecorp$exactTime : fallback;
	}

	@Inject(method = "evaluate", at = @At(value = "FIELD", target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;lastEvalTime:D", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
	private void lobecorp$correctSampleTime(double time, CallbackInfo ci) {
		if (lobecorp$exactTimeActive) {
			lobecorp$lastEvalTime(lobecorp$exactTime);
		}
	}

	@WrapOperation(method = "evaluate", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/timeline/ActivatorTrack;clipAt(D)Lcom/lowdragmc/photon/client/fx/timeline/Clip;"))
	private Clip lobecorp$activatorTime(ActivatorTrack track, double time, Operation<Clip> original) {
		return original.call(track, lobecorp$sampleTime(time));
	}

	@WrapOperation(method = "evaluate", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/timeline/ControlTrack;clipForObjectAt(Ljava/util/UUID;D)Lcom/lowdragmc/photon/client/fx/timeline/Clip;"))
	private Clip lobecorp$controlTime(ControlTrack track, UUID objectId, double time, Operation<Clip> original) {
		return original.call(track, objectId, lobecorp$sampleTime(time));
	}

	@WrapOperation(method = {"evaluate", "frame"}, at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;applyAnimations(D)V"))
	private void lobecorp$animationTime(TimelinePlayer player, double time, Operation<Void> original) {
		PhotonAnimationSamplingScope.beginPass();
		original.call(player, lobecorp$sampleTime(time));
		PhotonAnimationSamplingScope.commit();
	}

	@WrapOperation(method = "evaluate", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;applySpeed(Ljava/util/List;D)V"))
	private void lobecorp$speedTime(TimelinePlayer player, List<Track> leaves, double time, Operation<Void> original) {
		original.call(player, leaves, lobecorp$sampleTime(time));
	}

	@WrapOperation(method = "evaluate", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;dispatchSignals(Ljava/util/List;D)V"))
	private void lobecorp$signalTime(TimelinePlayer player, List<Track> leaves, double time, Operation<Void> original) {
		original.call(player, leaves, lobecorp$sampleTime(time));
	}

	@WrapOperation(method = "evaluate", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;applyAudio(Ljava/util/List;D)V"))
	private void lobecorp$audioTime(TimelinePlayer player, List<Track> leaves, double time, Operation<Void> original) {
		original.call(player, leaves, lobecorp$sampleTime(time));
	}

	@WrapOperation(method = "frame", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/timeline/TimelinePlayer;applyPostProcess(D)V"))
	private void lobecorp$postProcessTime(TimelinePlayer player, double time, Operation<Void> original) {
		original.call(player, lobecorp$sampleTime(time));
	}

	@Override
	@Accessor("duration")
	public abstract void lobecorp$duration(double duration);

	@Override
	@Accessor("lastSignalTick")
	public abstract void lobecorp$lastSignalTick(double time);

	@Override
	public void lobecorp$animation(@Nullable String animation) {
		lobecorp$animation = animation;
	}

	@Override
	public @Nullable String lobecorp$animation() {
		return lobecorp$animation;
	}
}
