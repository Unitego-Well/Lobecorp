package org.unitego.lobecorp.util.horde;

/// 服务端与客户端共用当地尾波通过时间；站程必须来自路线投影。
public class HordeTimingUtil {
	public static double tailRemaining(double stop, double station, double speed, double time) {
		return speed > 0 ? stop + station / speed - time : 0;
	}

	public static double closingRemaining(double stop, double station, double speed, double time, double lead) {
		return tailRemaining(stop, station, speed, time) - lead;
	}
}
