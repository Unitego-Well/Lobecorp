package org.unitego.lobecorp.util.horde;

import net.minecraft.util.Mth;

/// 开幕朝向轨迹的共同时间轴，单位为游戏刻；服务端锁与客户端插值共用。
public class MidnightCleanupViewUtil {

	public static final int IMPACT_END = 12;
	public static final int LEFT_END = 26, RIGHT_START = 30, RIGHT_END = 46;
	public static final int CENTER_START = 50, CENTER_END = 68;
	public static final int TEXT_READY = 78, DISSOLVE_START = 156;
	private static final float LOOK_ANGLE = 55;

	public record Angles(float yaw, float pitch) {
	}

	public static Angles sample(float startYaw, float startPitch, float targetYaw, float targetPitch, float age) {
		float left = startYaw + Mth.wrapDegrees(targetYaw - LOOK_ANGLE - startYaw);
		float right = left + LOOK_ANGLE * 2;
		float center = left + LOOK_ANGLE;
		float yaw;
		if (age < IMPACT_END) {
			yaw = startYaw;
		} else if (age < RIGHT_START) {
			yaw = turn(startYaw, left, phase(age, IMPACT_END, LEFT_END));
		} else if (age < CENTER_START) {
			yaw = turn(left, right, phase(age, RIGHT_START, RIGHT_END));
		} else {
			yaw = turn(right, center, phase(age, CENTER_START, CENTER_END));
		}

		float pitch = Mth.lerp(phase(age, IMPACT_END, CENTER_END), startPitch, targetPitch);
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
