package org.unitego.lobecorp.util.photon.animation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;

import java.util.UUID;

public class PhotonAnimationDataUtil {
	/// 第六版保存动画独立形参、单次事件调用及原生子动画片段。
	public static final int VERSION = 6;
	/// 自动时长和模块开启默认值从第三版引入，后续版本不能改变旧定义的缺省语义。
	public static final int AUTO_DURATION_VERSION = 3;
	/// 缺少版本字段的旧库沿用固定播放长度与自动开启模块。
	public static final int LEGACY_VERSION = 1;
	/// 新动画的默认制作时长，单位为游戏 tick。
	public static final double DEFAULT_DURATION = 20;
	/// 每次推进允许处理的事件数，防止触发规则递归耗尽客户端线程。
	public static final int EVENT_BUDGET = 256;
	/// 无效控制器仅禁用动画扩展，不阻断原生特效发射。
	public static final String INVALID_CONTROLLER_LOG = "Photon animation controller disabled for this effect: {}";

	public static UUID uuid(CompoundTag tag, String key) {
		try {
			return UUID.fromString(tag.getStringOr(key, ""));
		} catch (IllegalArgumentException _) {
			return UUID.randomUUID();
		}
	}

	public static <E extends Enum<E>> E enumeration(CompoundTag tag, String key, E fallback) {
		try {
			return Enum.valueOf(fallback.getDeclaringClass(), tag.getStringOr(key, fallback.name()));
		} catch (IllegalArgumentException _) {
			return fallback;
		}
	}

	/// 持久化类型必须严格读取，禁止把未知操作静默解释为其他行为。
	public static <E extends Enum<E>> E strictEnumeration(CompoundTag tag, String key, E fallback) {
		if (!tag.contains(key)) {
			return fallback;
		}

		return Enum.valueOf(fallback.getDeclaringClass(), tag.getString(key).orElseThrow(IllegalArgumentException::new));
	}

	public static float[] floats(CompoundTag tag, String key, float[] fallback) {
		var values = tag.getListOrEmpty(key);

		if (values.isEmpty()) {
			return fallback.clone();
		}

		var result = new float[values.size()];

		for (int i = 0; i < result.length; i++) {
			result[i] = values.getFloatOr(i, 0);
		}

		return result;
	}

	public static ListTag floats(float[] values) {
		var tag = new ListTag();

		for (float value : values) {
			tag.add(FloatTag.valueOf(value));
		}

		return tag;
	}

	public static double duration(double value) {
		if (!Double.isFinite(value) || value < 0) {
			throw new IllegalArgumentException();
		}

		return value;
	}
}
