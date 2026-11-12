package org.unitego.lobecorp.util.horde;

import net.minecraft.world.phys.Vec3;

/// 客户端表现与服务端预警共用的潮空间判定；不包含动画和音效时序。
public class HordePresenceUtil {
	private static final double END_EPSILON = 0.001;

	public static double head(double time, double speed, double length) {
		return Math.clamp(time * speed, 0, length);
	}

	public static double tail(double time, double stop, double speed, double length) {
		return Math.clamp((time - stop) * speed, 0, head(time, speed, length));
	}

	public static double center(double head, double tail) {
		return (head + tail) / 2;
	}

	public static double arrival(double station, double time, double speed) {
		return station / speed - time;
	}

	/// 站程和横向投影共同限制范围，端点外侧不能被投影钳制误判为内部。
	public static boolean contains(Vec3 observer, double station, Vec3 center, Vec3 forward,
	                               double from, double to, double halfWidth) {
		if (to <= from || station < from - END_EPSILON || station > to + END_EPSILON) {
			return false;
		}
		double dx = observer.x - center.x, dz = observer.z - center.z;
		double along = dx * forward.x + dz * forward.z;
		return !(station <= from + END_EPSILON && along < 0
				|| station >= to - END_EPSILON && along > 0)
				&& dx * dx + dz * dz <= halfWidth * halfWidth;
	}
}
