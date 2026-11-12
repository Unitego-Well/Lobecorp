package org.unitego.lobecorp.util.encounter;

import net.minecraft.util.Mth;
import org.unitego.lobecorp.util.visual.TimelineUtil;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;

/// 服务端朝向锁与客户端演出共用的游戏刻时间轴；不依赖固定地图坐标。
public class MidnightCleanupViewUtil {

	public static final int IMPACT_END = MidnightCleanupEncounter.IMPACT_END;
	public static final int FIRST_CENTER_END = MidnightCleanupEncounter.FIRST_CENTER_END;
	public static final int CENTER_AIM_END = MidnightCleanupEncounter.CENTER_AIM_END, CENTER_END = MidnightCleanupEncounter.CENTER_END;
	public static final int TEXT_READY = MidnightCleanupEncounter.TEXT_READY, DISSOLVE_START = MidnightCleanupEncounter.DISSOLVE_START;
	public static final int RECOVERY_START = MidnightCleanupEncounter.RECOVERY_START;

	public static Angles sample(float startYaw, float startPitch, float targetYaw, float targetPitch, float age) {
		float restore = phase(age, RECOVERY_START, CENTER_END);
		float center = startYaw + Mth.wrapDegrees(targetYaw - startYaw);
		float yaw = center + MidnightCleanupEncounter.viewYaw(age) * (1 - restore);
		float pitch = targetPitch + MidnightCleanupEncounter.viewPitch(age) * (1 - restore);
		return new Angles(yaw, Math.clamp(pitch, -90, 90));
	}

	public static float magnification(float age) {
		return (float) (Math.tan(Math.toRadians(MidnightCleanupEncounter.BASE_FOV) / 2) / Math.tan(Math.toRadians(MidnightCleanupEncounter.viewFov(age)) / 2));
	}

	public static float roll(float age) {
		return MidnightCleanupEncounter.viewRoll(age);
	}

	public static float side(float age) {
		return MidnightCleanupEncounter.viewSide(age);
	}

	public static float height(float age) {
		return MidnightCleanupEncounter.viewHeight(age);
	}

	public static float forward(float age) {
		return MidnightCleanupEncounter.viewForward(age);
	}

	public static float blackout(float age) {
		return 1 - phase(age, 0, IMPACT_END);
	}

	public static float letterbox(float age) {
		return Mth.lerp(phase(age, MidnightCleanupEncounter.LETTERBOX_GROW_START, MidnightCleanupEncounter.LETTERBOX_GROW_END),
				MidnightCleanupEncounter.LETTERBOX_INITIAL_HEIGHT, MidnightCleanupEncounter.LETTERBOX_FINAL_HEIGHT) * phase(age, 0, MidnightCleanupEncounter.LETTERBOX_ENTER_TICKS)
				* (1 - phase(age, RECOVERY_START, CENTER_END));
	}

	public static float phase(float age, float from, float to) {
		return TimelineUtil.phase(age, from, to);
	}

	public record Angles(float yaw, float pitch) {
	}
}
