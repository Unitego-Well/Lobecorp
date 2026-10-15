package org.unitego.lobecorp.animation.demo;

import org.unitego.lobecorp.animation.LcAnimationTransitionSettings;
import org.unitego.lobecorp.animation.LcControllerBlendType;
import org.unitego.lobecorp.animation.LcRotationTransitionMode;
import org.unitego.lobecorp.animation.LcTransitionMode;
import org.unitego.lobecorp.animation.runtime.LcAnimationInstance;

import java.util.Objects;

/// 调试示例共用的播放速度与视觉过渡；时长使用逻辑 tick。
public record LcAnimationPlaybackOptions(double speed, LcAnimationTransitionSettings transitions) {
	private static final String INVALID_SPEED = "Animation speed must be finite and non-negative";
	/// 示例默认用八 tick 进出姿态，速度一对应资源原始时间。
	public static final LcAnimationPlaybackOptions DEFAULT = of(1, 8, 8,
			LcTransitionMode.OVERLAP, LcRotationTransitionMode.SHORTEST_PATH);

	public LcAnimationPlaybackOptions {
		Objects.requireNonNull(transitions);
		if (!Double.isFinite(speed) || speed < 0) {
			throw new IllegalArgumentException(INVALID_SPEED);
		}
	}

	public static LcAnimationPlaybackOptions of(double speed, int fadeIn, int fadeOut,
	                                            LcTransitionMode transition, LcRotationTransitionMode rotation) {
		return new LcAnimationPlaybackOptions(speed, new LcAnimationTransitionSettings(fadeIn, fadeOut,
				transition, transition, rotation, rotation, transition, rotation, LcControllerBlendType.OVERRIDE));
	}

	public void apply(LcAnimationInstance instance) {
		instance.setSpeed(speed);
		var policy = instance.playerPolicy();
		if (policy != null) {
			instance.setPlayerPolicy(policy.withTransitions(transitions));
		}
	}
}

