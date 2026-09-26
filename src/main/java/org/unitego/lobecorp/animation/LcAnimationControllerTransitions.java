package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;

public interface LcAnimationControllerTransitions<T extends GeoAnimatable> {
	LcControllerBlendType lc$getBlendType();

	AnimationController<T> lc$setBlendType(LcControllerBlendType blendType);

	int lc$getFadeInTicks();

	AnimationController<T> lc$setFadeInTicks(int ticks);

	int lc$getFadeOutTicks();

	AnimationController<T> lc$setFadeOutTicks(int ticks);

	LcTransitionMode lc$getFadeInTransitionMode();

	AnimationController<T> lc$setFadeInTransitionMode(LcTransitionMode mode);

	LcTransitionMode lc$getFadeOutTransitionMode();

	AnimationController<T> lc$setFadeOutTransitionMode(LcTransitionMode mode);

	LcRotationTransitionMode lc$getFadeInRotationTransitionMode();

	AnimationController<T> lc$setFadeInRotationTransitionMode(LcRotationTransitionMode mode);

	LcRotationTransitionMode lc$getFadeOutRotationTransitionMode();

	AnimationController<T> lc$setFadeOutRotationTransitionMode(LcRotationTransitionMode mode);

	LcTransitionMode lc$getAnimationTransitionMode();

	AnimationController<T> lc$setAnimationTransitionMode(LcTransitionMode mode);

	LcTransitionMode lc$getControllerTransitionMode();

	AnimationController<T> lc$setControllerTransitionMode(LcTransitionMode mode);

	LcRotationTransitionMode lc$getRotationTransitionMode();

	AnimationController<T> lc$setRotationTransitionMode(LcRotationTransitionMode mode);

	void lc$setAnimationTransitionPaused(boolean paused);

	void lc$setControllerTransitionPaused(boolean paused);

	@SuppressWarnings("unchecked")
	static <T extends GeoAnimatable> LcAnimationControllerTransitions<T> of(AnimationController<T> controller) {
		return (LcAnimationControllerTransitions<T>)controller;
	}
}
