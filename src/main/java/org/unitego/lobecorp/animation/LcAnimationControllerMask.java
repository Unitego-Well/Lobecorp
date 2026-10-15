package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;

/// 动画控制器骨骼遮罩的 Mixin 桥接契约。
public interface LcAnimationControllerMask<T extends GeoAnimatable> {
	@SuppressWarnings("unchecked")
	static <T extends GeoAnimatable> LcAnimationControllerMask<T> of(AnimationController<T> controller) {
		return (LcAnimationControllerMask<T>) controller;
	}

	boolean lobecorp$isBoneInfluenced(String boneName);

	AnimationController<T> lobecorp$lockBones(String... boneNames);

	AnimationController<T> lobecorp$removeLockBones(String... boneNames);

	AnimationController<T> lobecorp$enabledBones(String... boneNames);

	AnimationController<T> lobecorp$removeEnabledBones(String... boneNames);
}
