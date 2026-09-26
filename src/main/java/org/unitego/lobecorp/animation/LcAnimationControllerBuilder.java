package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.AnimationController.AnimationStateHandler;
import com.geckolib.animation.RawAnimation;

public class LcAnimationControllerBuilder<T extends GeoAnimatable> {
	private final AnimationController<T> controller;
	private final LcAnimationControllerTransitions<T> transitions;

	public LcAnimationControllerBuilder(String name, int transitionTicks, AnimationStateHandler<T> stateHandler) {
		this.controller = new AnimationController<>(name, transitionTicks, stateHandler);
		this.transitions = LcAnimationControllerTransitions.of(this.controller);
	}

	public LcAnimationControllerBuilder<T> blendType(LcControllerBlendType blendType) {
		this.transitions.lc$setBlendType(blendType);
		return this;
	}

	public LcAnimationControllerBuilder<T> fadeInTicks(int ticks) {
		this.transitions.lc$setFadeInTicks(ticks);
		return this;
	}

	public LcAnimationControllerBuilder<T> fadeOutTicks(int ticks) {
		this.transitions.lc$setFadeOutTicks(ticks);
		return this;
	}

	public LcAnimationControllerBuilder<T> fadeInTransitionMode(LcTransitionMode mode) {
		this.transitions.lc$setFadeInTransitionMode(mode);
		return this;
	}

	public LcAnimationControllerBuilder<T> fadeOutTransitionMode(LcTransitionMode mode) {
		this.transitions.lc$setFadeOutTransitionMode(mode);
		return this;
	}

	public LcAnimationControllerBuilder<T> fadeInRotationTransitionMode(LcRotationTransitionMode mode) {
		this.transitions.lc$setFadeInRotationTransitionMode(mode);
		return this;
	}

	public LcAnimationControllerBuilder<T> fadeOutRotationTransitionMode(LcRotationTransitionMode mode) {
		this.transitions.lc$setFadeOutRotationTransitionMode(mode);
		return this;
	}

	public LcAnimationControllerBuilder<T> animationTransitionMode(LcTransitionMode mode) {
		this.transitions.lc$setAnimationTransitionMode(mode);
		return this;
	}

	public LcAnimationControllerBuilder<T> rotationTransitionMode(LcRotationTransitionMode mode) {
		this.transitions.lc$setRotationTransitionMode(mode);
		return this;
	}

	public LcAnimationControllerBuilder<T> triggerableAnim(String name, RawAnimation animation) {
		this.controller.triggerableAnim(name, animation);
		return this;
	}

	public AnimationController<T> build() {
		return this.controller;
	}
}
