package org.unitego.lobecorp.util.photon.compat;

import net.minecraft.nbt.*;

import java.util.Set;

/// 尚未验收的生效扩展继续保护；已恢复字段的关闭状态不应阻断普通原生工程。
public class PhotonInactiveLegacyFieldUtil {
	private static final Set<String> OFFSETS = Set.of("X", "Y", "Z", "Red", "Green", "Blue", "Alpha", "Yaw", "Pitch", "Roll",
			"OrbitalX", "OrbitalY", "OrbitalZ", "CenterX", "CenterY", "CenterZ", "Radial", "Multiplier", "UV");

	public static boolean allowed(CompoundTag owner, String key, Tag value) {
		if (key.equals("lobecorp$qualityExempt") || key.equals("lobecorp$planarLod")) {
			return value instanceof ByteTag byteTag && (byteTag.byteValue() == 0 || byteTag.byteValue() == 1);
		}
		if (Set.of("lobecorp$fixedRendering", "lobecorp$viewDepthSort").contains(key)) {
			return off(value);
		}
		if (key.equals("lobecorp$lifetimeCycleEnabled") || key.equals("lobecorp$rotationCycleEnabled")) {
			return off(value);
		}
		boolean inactive = offOrAbsent(owner, "lobecorp$lifetimeCycleEnabled") && offOrAbsent(owner, "lobecorp$rotationCycleEnabled");
		if (!inactive) {
			return false;
		}
		if (key.equals("lobecorp$lifetimeCycleTicks") || key.equals("lobecorp$rotationCycleTicks")) {
			return value instanceof IntTag integer && integer.intValue() > 0;
		}
		if (key.startsWith("lobecorp$cycleOffset") && OFFSETS.contains(key.substring("lobecorp$cycleOffset".length()))) {
			return value instanceof NumericTag numeric && Float.isFinite(numeric.floatValue());
		}
		if (key.equals("lobecorp$occlusion") && value instanceof CompoundTag config) {
			return Set.of("enable", "_enable", "alphaCutoff").containsAll(config.keySet())
					&& offOrAbsent(config, "enable") && offOrAbsent(config, "_enable")
					&& (!config.contains("alphaCutoff") || config.get("alphaCutoff") instanceof NumericTag number
					&& Float.isFinite(number.floatValue()) && number.floatValue() >= 0 && number.floatValue() <= 1);
		}
		return false;
	}

	private static boolean off(Tag value) {
		return value instanceof ByteTag byteTag && byteTag.byteValue() == 0;
	}

	private static boolean offOrAbsent(CompoundTag owner, String key) {
		return !owner.contains(key) || off(owner.get(key));
	}
}
