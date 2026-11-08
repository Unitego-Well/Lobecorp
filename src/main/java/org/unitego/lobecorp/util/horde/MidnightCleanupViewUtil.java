package org.unitego.lobecorp.util.horde;

import net.minecraft.util.Mth;

/// 开幕朝向轨迹的共同时间轴，单位为游戏刻；服务端锁与客户端插值共用。
public class MidnightCleanupViewUtil {

	public static final int IMPACT_END = 12;
	public static final int FIRST_CENTER_END = 28;
	public static final int LEFT_END = 38, RIGHT_START = 47, RIGHT_PEAK = 61, RIGHT_END = 65;
	public static final int CENTER_START = 75, CENTER_AIM_END = 84, CENTER_END = 96;
	public static final int TEXT_READY = 106, DISSOLVE_START = 168;
	private static final float LOOK_ANGLE = 27.5F, LOOK_OVERSHOOT = 1.5F;
	private static final float ZOOM_MAGNIFICATION = 1.5F;
	private static final int ZOOM_RECOVERY_END = 188;

	public record Angles(float yaw, float pitch) {
	}

	public static Angles sample(float startYaw, float startPitch, float targetYaw, float targetPitch, float age) {
		float center = startYaw + Mth.wrapDegrees(targetYaw - startYaw);
		float left = center - LOOK_ANGLE;
		float right = left + LOOK_ANGLE * 2;
		float yaw;
		if (age < IMPACT_END) {
			yaw = startYaw;
		} else if (age < FIRST_CENTER_END) {
			yaw = turn(startYaw, center, phase(age, IMPACT_END, FIRST_CENTER_END));
		} else if (age < RIGHT_START) {
			yaw = turn(center, left, urgent(age, FIRST_CENTER_END, LEFT_END, 2));
		} else if (age < RIGHT_PEAK) {
			yaw = turn(left, right + LOOK_OVERSHOOT, urgent(age, RIGHT_START, RIGHT_PEAK, 3));
		} else if (age < CENTER_START) {
			yaw = turn(right + LOOK_OVERSHOOT, right, phase(age, RIGHT_PEAK, RIGHT_END));
		} else {
			yaw = turn(right, center, phase(age, CENTER_START, CENTER_AIM_END));
		}

		float pitch = Mth.lerp(phase(age, IMPACT_END, FIRST_CENTER_END), startPitch, targetPitch);
		return new Angles(yaw, Math.clamp(pitch, -85, 85));
	}

	/// 只在最后回中停稳后望远；文字期间保持，退场用一秒恢复常态。
	public static float magnification(float age) {
		float reveal = phase(age, CENTER_AIM_END, CENTER_END);
		float restore = 1 - phase(age, DISSOLVE_START, ZOOM_RECOVERY_END);
		return 1 + (ZOOM_MAGNIFICATION - 1) * reveal * restore;
	}

	private static float urgent(float age, float from, float to, int urgency) {
		float eased = phase(age, from, to);
		return 1 - (float) Math.pow(1 - eased, urgency);
	}

	public static float phase(float age, float from, float to) {
		float value = Math.clamp((age - from) / (to - from), 0, 1);
		return value * value * (3 - 2 * value);
	}

	private static float turn(float from, float to, float progress) {
		return from + Mth.wrapDegrees(to - from) * progress;
	}
}
