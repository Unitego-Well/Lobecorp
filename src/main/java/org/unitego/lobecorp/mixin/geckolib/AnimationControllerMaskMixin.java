package org.unitego.lobecorp.mixin.geckolib;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.state.AnimationTimeline;
import com.geckolib.model.GeoModel;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.animation.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Mixin(AnimationController.class)
public abstract class AnimationControllerMaskMixin<T extends GeoAnimatable> implements LcAnimationControllerMask<T>, LcAnimationControllerTransitions<T> {
	@Unique
	private final Set<String> lobecorp$lockedBones = new HashSet<>();
	@Unique
	private final Set<String> lobecorp$enabledBones = new HashSet<>();
	@Shadow
	protected RawAnimation currentRawAnimation;
	@Unique
	private LcTransitionMode lobecorp$animationTransitionMode = LcTransitionMode.SEQUENTIAL;
	@Unique
	private LcControllerBlendType lobecorp$blendType = LcControllerBlendType.ADDITIVE;
	@Unique
	private int lobecorp$fadeInTicks = 3;
	@Unique
	private int lobecorp$fadeOutTicks = 5;
	@Unique
	private LcTransitionMode lobecorp$fadeInTransitionMode = LcTransitionMode.OVERLAP;
	@Unique
	private LcTransitionMode lobecorp$fadeOutTransitionMode = LcTransitionMode.SEQUENTIAL;
	@Unique
	private LcRotationTransitionMode lobecorp$fadeInRotationTransitionMode = LcRotationTransitionMode.NORMAL;
	@Unique
	private LcRotationTransitionMode lobecorp$fadeOutRotationTransitionMode = LcRotationTransitionMode.SHORTEST_PATH;
	@Unique
	private LcRotationTransitionMode lobecorp$rotationTransitionMode = LcRotationTransitionMode.NORMAL;
	@Unique
	private boolean lobecorp$animationTransitionPaused;
	@Unique
	private boolean lobecorp$controllerTransitionPaused;
	@Unique
	private RawAnimation lobecorp$previousRawAnimation;
	@Unique
	private int lobecorp$animationTriggerRevision;

	@Inject(method = "setAnimation", at = @At("HEAD"))
	private void lobecorp$capturePreviousAnimation(RawAnimation rawAnimation, CallbackInfo ci) {
		this.lobecorp$previousRawAnimation = this.currentRawAnimation;
	}

	@Inject(method = "setAnimation", at = @At("TAIL"))
	private void lobecorp$pauseSequentialAnimationTransition(RawAnimation rawAnimation, CallbackInfo ci) {
		if (this.lobecorp$previousRawAnimation != null && this.lobecorp$previousRawAnimation != this.currentRawAnimation
				&& this.lobecorp$animationTransitionMode == LcTransitionMode.SEQUENTIAL) {
			this.lobecorp$animationTransitionPaused = true;
		}
	}

	@WrapOperation(
			method = "checkControllerState",
			at = @At(
					value = "INVOKE",
					target = "Lcom/geckolib/animation/AnimationController;safetyCheckTickLinearity(DD)D"
			)
	)
	private double lobecorp$pauseAnimationTimeline(AnimationController<T> instance, double currentTick, double compareTo, Operation<Double> original) {
		if (this.lobecorp$animationTransitionPaused || this.lobecorp$controllerTransitionPaused) {
			return compareTo;
		}
		return original.call(instance, currentTick, compareTo);
	}

	@Inject(method = "triggerAnimation", at = @At("HEAD"))
	private void lobecorp$capturePreviousTriggeredAnimation(String animName, CallbackInfoReturnable<Boolean> cir) {
		this.lobecorp$previousRawAnimation = this.currentRawAnimation;
	}

	@Inject(method = "triggerAnimation", at = @At("RETURN"))
	private void lobecorp$pauseSequentialTriggeredAnimation(String animName, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) {
			this.lobecorp$animationTriggerRevision++;
			if (this.lobecorp$animationTransitionMode == LcTransitionMode.SEQUENTIAL) {
				this.lobecorp$animationTransitionPaused = true;
			}
		}
	}

	@WrapOperation(
			method = "initializeNewAnimation",
			at = @At(
					value = "INVOKE",
					target = "Lcom/geckolib/animation/state/AnimationTimeline;create(Lcom/geckolib/animation/RawAnimation;Lcom/geckolib/animatable/GeoAnimatable;Lcom/geckolib/model/GeoModel;I)Lcom/geckolib/animation/state/AnimationTimeline;"
			)
	)
	private AnimationTimeline lobecorp$removeGeckoTransitionStages(RawAnimation rawAnimation, T animatable, GeoModel<T> model,
	                                                               int transitionTicks, Operation<AnimationTimeline> original) {
		if (animatable instanceof LcCustomAnimatable) {
			return original.call(rawAnimation, animatable, model, 0);
		}
		return original.call(rawAnimation, animatable, model, transitionTicks);
	}

	@Override
	public boolean lobecorp$isBoneInfluenced(String boneName) {
		if (!this.lobecorp$enabledBones.isEmpty()) {
			return !this.lobecorp$lockedBones.contains(boneName) && this.lobecorp$enabledBones.contains(boneName);
		}
		return !this.lobecorp$lockedBones.contains(boneName);
	}

	@Override
	public AnimationController<T> lobecorp$lockBones(String... boneNames) {
		this.lobecorp$lockedBones.addAll(Arrays.asList(boneNames));
		return this.lobecorp$getController();
	}

	@Override
	public AnimationController<T> lobecorp$removeLockBones(String... boneNames) {
		Arrays.stream(boneNames).forEach(this.lobecorp$lockedBones::remove);
		return this.lobecorp$getController();
	}

	@Override
	public AnimationController<T> lobecorp$enabledBones(String... boneNames) {
		this.lobecorp$enabledBones.addAll(Arrays.asList(boneNames));
		return this.lobecorp$getController();
	}

	@Override
	public AnimationController<T> lobecorp$removeEnabledBones(String... boneNames) {
		Arrays.stream(boneNames).forEach(this.lobecorp$enabledBones::remove);
		return this.lobecorp$getController();
	}

	@Override
	public LcControllerBlendType lobecorp$getBlendType() {
		return this.lobecorp$blendType;
	}

	@Override
	public AnimationController<T> lobecorp$setBlendType(LcControllerBlendType blendType) {
		this.lobecorp$blendType = Objects.requireNonNull(blendType);
		return this.lobecorp$getController();
	}

	@Override
	public int lobecorp$getFadeInTicks() {
		return this.lobecorp$fadeInTicks;
	}

	@Override
	public AnimationController<T> lobecorp$setFadeInTicks(int ticks) {
		this.lobecorp$fadeInTicks = ticks;
		return this.lobecorp$getController();
	}

	@Override
	public int lobecorp$getFadeOutTicks() {
		return this.lobecorp$fadeOutTicks;
	}

	@Override
	public AnimationController<T> lobecorp$setFadeOutTicks(int ticks) {
		this.lobecorp$fadeOutTicks = ticks;
		return this.lobecorp$getController();
	}

	@Override
	public LcTransitionMode lobecorp$getFadeInTransitionMode() {
		return this.lobecorp$fadeInTransitionMode;
	}

	@Override
	public AnimationController<T> lobecorp$setFadeInTransitionMode(LcTransitionMode mode) {
		this.lobecorp$fadeInTransitionMode = Objects.requireNonNull(mode);
		if (mode == LcTransitionMode.OVERLAP) {
			this.lobecorp$controllerTransitionPaused = false;
		}
		return this.lobecorp$getController();
	}

	@Override
	public LcTransitionMode lobecorp$getFadeOutTransitionMode() {
		return this.lobecorp$fadeOutTransitionMode;
	}

	@Override
	public AnimationController<T> lobecorp$setFadeOutTransitionMode(LcTransitionMode mode) {
		this.lobecorp$fadeOutTransitionMode = Objects.requireNonNull(mode);
		return this.lobecorp$getController();
	}

	@Override
	public LcRotationTransitionMode lobecorp$getFadeInRotationTransitionMode() {
		return this.lobecorp$fadeInRotationTransitionMode;
	}

	@Override
	public AnimationController<T> lobecorp$setFadeInRotationTransitionMode(LcRotationTransitionMode mode) {
		this.lobecorp$fadeInRotationTransitionMode = Objects.requireNonNull(mode);
		return this.lobecorp$getController();
	}

	@Override
	public LcRotationTransitionMode lobecorp$getFadeOutRotationTransitionMode() {
		return this.lobecorp$fadeOutRotationTransitionMode;
	}

	@Override
	public AnimationController<T> lobecorp$setFadeOutRotationTransitionMode(LcRotationTransitionMode mode) {
		this.lobecorp$fadeOutRotationTransitionMode = Objects.requireNonNull(mode);
		return this.lobecorp$getController();
	}

	@Override
	public LcTransitionMode lobecorp$getAnimationTransitionMode() {
		return this.lobecorp$animationTransitionMode;
	}

	@Override
	public AnimationController<T> lobecorp$setAnimationTransitionMode(LcTransitionMode mode) {
		this.lobecorp$animationTransitionMode = Objects.requireNonNull(mode);
		if (mode == LcTransitionMode.OVERLAP) {
			this.lobecorp$animationTransitionPaused = false;
		}
		return this.lobecorp$getController();
	}

	@Override
	public LcTransitionMode lobecorp$getControllerTransitionMode() {
		return this.lobecorp$fadeOutTransitionMode;
	}

	@Override
	public AnimationController<T> lobecorp$setControllerTransitionMode(LcTransitionMode mode) {
		return this.lobecorp$setFadeOutTransitionMode(mode);
	}

	@Override
	public LcRotationTransitionMode lobecorp$getRotationTransitionMode() {
		return this.lobecorp$rotationTransitionMode;
	}

	@Override
	public AnimationController<T> lobecorp$setRotationTransitionMode(LcRotationTransitionMode mode) {
		this.lobecorp$rotationTransitionMode = Objects.requireNonNull(mode);
		return this.lobecorp$getController();
	}

	@Override
	public void lobecorp$setAnimationTransitionPaused(boolean paused) {
		this.lobecorp$animationTransitionPaused = paused;
	}

	@Override
	public void lobecorp$setControllerTransitionPaused(boolean paused) {
		this.lobecorp$controllerTransitionPaused = paused;
	}

	@Override
	public int lobecorp$getAnimationTriggerRevision() {
		return this.lobecorp$animationTriggerRevision;
	}

	@Unique
	@SuppressWarnings("unchecked")
	private AnimationController<T> lobecorp$getController() {
		return (AnimationController<T>) (Object) this;
	}
}
