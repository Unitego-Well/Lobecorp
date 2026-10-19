package org.unitego.lobecorp.util.animation;

/// 实体姿态与模型共用距离档位；不依赖特效运行态。
public class LcVisualQualityUtil {
	public static int band(double distance, int previous, double near, double far, double hysteresis) {
		if (distance <= near) {
			return 0;
		}

		far = Math.max(near + 1, far);
		if (distance >= far) {
			return 2;
		}

		if (previous == 2 && distance >= Math.max(near, far - hysteresis)) {
			return 2;
		}

		return 1;
	}
}
