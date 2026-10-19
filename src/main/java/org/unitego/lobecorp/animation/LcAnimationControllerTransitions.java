package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;

/// 动画控制器过渡配置与运行状态的 Mixin 桥接契约。
public interface LcAnimationControllerTransitions<T extends GeoAnimatable> {
	@SuppressWarnings("unchecked")
	static <T extends GeoAnimatable> LcAnimationControllerTransitions<T> of(AnimationController<T> controller) {
		return (LcAnimationControllerTransitions<T>) controller;
	}

	LcControllerBlendType lobecorp$getBlendType();

	AnimationController<T> lobecorp$setBlendType(LcControllerBlendType blendType);

	int lobecorp$getFadeInTicks();

	boolean lobecorp$isInitialFadeInSkipped();

	void lobecorp$setInitialFadeInSkipped(boolean skipped);

	AnimationController<T> lobecorp$setFadeInTicks(int ticks);

	int lobecorp$getFadeOutTicks();

	AnimationController<T> lobecorp$setFadeOutTicks(int ticks);

	LcTransitionMode lobecorp$getFadeInTransitionMode();

	AnimationController<T> lobecorp$setFadeInTransitionMode(LcTransitionMode mode);

	LcTransitionMode lobecorp$getFadeOutTransitionMode();

	AnimationController<T> lobecorp$setFadeOutTransitionMode(LcTransitionMode mode);

	LcRotationTransitionMode lobecorp$getFadeInRotationTransitionMode();

	AnimationController<T> lobecorp$setFadeInRotationTransitionMode(LcRotationTransitionMode mode);

	LcRotationTransitionMode lobecorp$getFadeOutRotationTransitionMode();

	AnimationController<T> lobecorp$setFadeOutRotationTransitionMode(LcRotationTransitionMode mode);

	LcTransitionMode lobecorp$getAnimationTransitionMode();

	AnimationController<T> lobecorp$setAnimationTransitionMode(LcTransitionMode mode);

	LcTransitionMode lobecorp$getControllerTransitionMode();

	AnimationController<T> lobecorp$setControllerTransitionMode(LcTransitionMode mode);

	LcRotationTransitionMode lobecorp$getRotationTransitionMode();

	AnimationController<T> lobecorp$setRotationTransitionMode(LcRotationTransitionMode mode);

	void lobecorp$setAnimationTransitionPaused(boolean paused);

	void lobecorp$setControllerTransitionPaused(boolean paused);

	int lobecorp$getAnimationTriggerRevision();
}
