package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;

public interface LcAnimationControllerMask<T extends GeoAnimatable> {
	boolean lc$isBoneInfluenced(String boneName);

	AnimationController<T> lc$lockBones(String... boneNames);

	AnimationController<T> lc$removeLockBones(String... boneNames);

	AnimationController<T> lc$enabledBones(String... boneNames);

	AnimationController<T> lc$removeEnabledBones(String... boneNames);

	@SuppressWarnings("unchecked")
	static <T extends GeoAnimatable> LcAnimationControllerMask<T> of(AnimationController<T> controller) {
		return (LcAnimationControllerMask<T>)controller;
	}
}
