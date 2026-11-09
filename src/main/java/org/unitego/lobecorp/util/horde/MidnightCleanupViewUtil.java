package org.unitego.lobecorp.util.horde;

import net.minecraft.util.Mth;

/// 服务端朝向锁与客户端演出共用的游戏刻时间轴；不依赖固定地图坐标。
public class MidnightCleanupViewUtil {

	public static final int IMPACT_END = 36;
	/// 环境起染仍在原九十六刻完成，不随相机恢复段延长。
	public static final int ATMOSPHERE_END = 96;
	public static final int FIRST_CENTER_END = 68;
	public static final int CENTER_AIM_END = 148, CENTER_END = 276;
	public static final int TEXT_READY = 188, DISSOLVE_START = 212;
	public static final int RECOVERY_START = 220;
	private static final float BASE_FOV = 68;
	private static final float[][] YAW = {{0, 2}, {24, 5}, {48, -8}, {72, 8}, {96, -3}, {132, 0}, {220, 0}};
	private static final float[][] PITCH = {{0, 24}, {24, 18}, {48, -7}, {72, -9}, {96, -5}, {132, 0}, {220, 0}};
	private static final float[][] ROLL = {{0, 0}, {48, -1.72F}, {72, 3.15F}, {96, -2.58F}, {124, 3.44F},
			{160, 1.72F}, {196, 1.03F}, {228, 0.46F}, {252, 0.11F}, {276, 0}};
	private static final float[][] FOV = {{0, 62}, {28, 64}, {56, 74}, {88, 79}, {120, 64}, {CENTER_AIM_END, 48},
			{176, 44}, {204, 52}, {236, 60}, {260, 66}, {276, BASE_FOV}};
	private static final float[][] SIDE = {{0, 0}, {26, 0.06F}, {52, 0.22F}, {84, 0.72F}, {116, 1.14F},
			{144, 0.98F}, {176, 0.74F}, {208, 0.42F}, {236, 0.20F}, {256, 0.05F}, {276, 0}};
	private static final float[][] HEIGHT = {{0, -0.53F}, {26, -0.35F}, {52, -0.07F}, {84, 0.14F},
			{116, 0.25F}, {144, 0.17F}, {176, 0.09F}, {208, 0.04F}, {236, 0.02F}, {256, 0.01F}, {276, 0}};
	private static final float[][] FORWARD = {{0, 0.34F}, {26, 0.22F}, {52, -0.08F}, {84, -0.62F},
			{116, -1.18F}, {144, -1.58F}, {176, -1.20F}, {208, -0.64F}, {236, -0.26F}, {256, -0.06F}, {276, 0}};

	public record Angles(float yaw, float pitch) {
	}

	public static Angles sample(float startYaw, float startPitch, float targetYaw, float targetPitch, float age) {
		float align = phase(age, 0, FIRST_CENTER_END);
		float restore = phase(age, RECOVERY_START, CENTER_END);
		float center = startYaw + Mth.wrapDegrees(targetYaw - startYaw) * align;
		float yaw = center + track(YAW, age) * (1 - restore);
		yaw += Mth.wrapDegrees(startYaw - yaw) * restore;
		float pitch = Mth.lerp(align, startPitch, targetPitch) + track(PITCH, age) * (1 - restore);
		pitch = Mth.lerp(restore, pitch, startPitch);
		return new Angles(yaw, Math.clamp(pitch, -90, 90));
	}

	public static float magnification(float age) {
		return (float) (Math.tan(Math.toRadians(BASE_FOV) / 2) / Math.tan(Math.toRadians(track(FOV, age)) / 2));
	}

	public static float roll(float age) {
		return track(ROLL, age);
	}

	public static float side(float age) {
		return track(SIDE, age);
	}

	public static float height(float age) {
		return track(HEIGHT, age);
	}

	public static float forward(float age) {
		return track(FORWARD, age);
	}

	public static float blackout(float age) {
		return 1 - phase(age, 0, IMPACT_END);
	}

	public static float letterbox(float age) {
		return Mth.lerp(phase(age, 120, 176), 0.10F, 0.21F) * phase(age, 0, 32)
				* (1 - phase(age, RECOVERY_START, CENTER_END));
	}

	public static float phase(float age, float from, float to) {
		float value = Math.clamp((age - from) / (to - from), 0, 1);
		return value * value * (3 - 2 * value);
	}

	private static float track(float[][] keys, float age) {
		for (int i = 1; i < keys.length; i++) {
			if (age < keys[i][0]) {
				return Mth.lerp(phase(age, keys[i - 1][0], keys[i][0]), keys[i - 1][1], keys[i][1]);
			}
		}
		return keys[keys.length - 1][1];
	}
}
