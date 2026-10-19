package org.unitego.lobecorp.util.animation;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import org.unitego.lobecorp.animation.LcAnimationControllerTransitions;
import org.unitego.lobecorp.animation.LcAnimationLodAccess;

public class LcAnimationControllerUtil {
	/// 显式允许本模组实体在远处降低姿态采样率，控制器时间与关键帧事件仍正常推进。
	public static <T extends GeoAnimatable> AnimationController<T> withDistanceLod(AnimationController<T> controller) {
		if (controller instanceof LcAnimationLodAccess access) {
			access.lobecorp$setDistanceLod(true);
		}
		return controller;
	}

	/// 默认姿态首次呈现时直接生效，停止后重新进入及后续动画切换仍使用原过渡设置。
	public static <T extends GeoAnimatable> AnimationController<T> withoutInitialFadeIn(AnimationController<T> controller) {
		LcAnimationControllerTransitions.of(controller).lobecorp$setInitialFadeInSkipped(true);
		return controller;
	}
}
