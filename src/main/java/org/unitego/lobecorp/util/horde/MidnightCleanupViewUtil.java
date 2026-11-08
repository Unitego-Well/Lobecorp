package org.unitego.lobecorp.util.horde;

import net.minecraft.util.Mth;

/// 开幕朝向轨迹的共同时间轴，单位为游戏刻；服务端锁与客户端插值共用。
public class MidnightCleanupViewUtil {

	public static final int IMPACT_END = 12;
	public static final int FIRST_CENTER_END = 28;
	public static final int LEFT_END = 40, RIGHT_START = 52, RIGHT_END = 66;
	public static final int CENTER_START = 78, CENTER_END = 96;
	public static final int TEXT_READY = 106, DISSOLVE_START = 168;
	private static final float LOOK_ANGLE = 55;

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
			yaw = turn(center, left, phase(age, FIRST_CENTER_END, LEFT_END));
		} else if (age < CENTER_START) {
			yaw = turn(left, right, phase(age, RIGHT_START, RIGHT_END));
		} else {
			yaw = turn(right, center, phase(age, CENTER_START, CENTER_END));
		}

		float pitch = Mth.lerp(phase(age, IMPACT_END, FIRST_CENTER_END), startPitch, targetPitch);
		return new Angles(yaw, Math.clamp(pitch, -85, 85));
	}

	public static float phase(float age, float from, float to) {
		float value = Math.clamp((age - from) / (to - from), 0, 1);
		return value * value * (3 - 2 * value);
	}

	private static float turn(float from, float to, float progress) {
		return from + Mth.wrapDegrees(to - from) * progress;
	}
}
