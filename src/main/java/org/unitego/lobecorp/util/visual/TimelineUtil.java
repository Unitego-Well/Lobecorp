package org.unitego.lobecorp.util.visual;

import net.minecraft.util.Mth;

/// 无世界依赖的时间轴采样，客户端和服务端共用相同过渡。
public class TimelineUtil {

	public static float phase(float time, float from, float to) {
		float value = Math.clamp((time - from) / (to - from), 0, 1);
		return value * value * (3 - 2 * value);
	}

	public static float sample(float[][] keys, float time) {
		if (time <= keys[0][0]) {
			return keys[0][1];
		}
		for (int index = 1; index < keys.length; index++) {
			if (time < keys[index][0]) {
				return Mth.lerp(phase(time, keys[index - 1][0], keys[index][0]), keys[index - 1][1], keys[index][1]);
			}
		}
		return keys[keys.length - 1][1];
	}
}
