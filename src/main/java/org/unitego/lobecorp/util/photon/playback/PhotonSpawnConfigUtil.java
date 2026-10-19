package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.lowdraglib2.utils.LDLibExtraCodecs;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig;
import org.unitego.lobecorp.config.photon.playback.PhotonSpawnShape;

/// 适配当前生成器对象协议；严格校验控制字段，未知字段留在原树。
public class PhotonSpawnConfigUtil {
	public static void validate(CompoundTag object) {
		if (object.getIntOr("version", 0) != 0 || !(object.get("config") instanceof CompoundTag config)) {
			throw new IllegalArgumentException("子发射对象版本或配置无效。");
		}

		enumeration(config, "source", PhotonSpawnConfig.Source.class);
		enumeration(config, "mode", PhotonSpawnConfig.Mode.class);
		enumeration(config, "endBehavior", PhotonSpawnConfig.EndBehavior.class);

		for (var key : new String[]{"enabled", "includeChildren", "looping", "clearOnRestart", "follow",
				"inheritRotation", "inheritScale", "inheritColor", "inheritVelocity"}) {
			bool(config, key);
		}

		for (var key : new String[]{"duration", "startDelay", "interval", "maxInstances", "maxTotal", "maxPerTick", "maxDepth", "instanceLifetime"}) {
			if (config.contains(key) && (!(config.get(key) instanceof NumericTag value) || !Double.isFinite(value.doubleValue())
					|| value.doubleValue() < 0 || value.doubleValue() > Integer.MAX_VALUE || Math.rint(value.doubleValue()) != value.doubleValue())) {
				throw new IllegalArgumentException("子发射整数配置无效：" + key);
			}
		}

		if (config.contains("fxLocation")) {
			String location = config.getString("fxLocation").orElseThrow(() -> new IllegalArgumentException("子发射资源ID类型无效。"));

			if (!LDLibExtraCodecs.isEmptyOrStringNull(NbtOps.INSTANCE, config.get("fxLocation")) && Identifier.tryParse(location) == null) {
				throw new IllegalArgumentException("子发射资源ID无效。");
			}
		}

		if (config.contains("shape")) {
			if (!(config.get("shape") instanceof CompoundTag shape)) {
				throw new IllegalArgumentException("子发射形状类型无效。");
			}

			enumeration(shape, "type", PhotonSpawnShape.Type.class);
		}

		if (config.contains("parameters")) {
			if (!(config.get("parameters") instanceof CompoundTag parameters)) {
				throw new IllegalArgumentException("子发射覆盖参数类型无效。");
			}

			for (var key : new String[]{"overrideLifetime", "overrideSize", "overrideColor", "overrideSpeed", "overrideBeamLength", "overrideBeamWidth"}) {
				bool(parameters, key);
			}

			if (parameters.contains("targetName") && !(parameters.get("targetName") instanceof StringTag)) {
				throw new IllegalArgumentException("子发射覆盖目标名称无效。");
			}

			if (parameters.contains("playbackSpeed") && (!(parameters.get("playbackSpeed") instanceof NumericTag value)
					|| !Float.isFinite(value.floatValue()) || value.floatValue() < 0 || value.floatValue() > PhotonSpawnConfig.MAX_PLAYBACK_SPEED)) {
				throw new IllegalArgumentException("子发射播放倍率无效。");
			}
		}
	}

	private static <T extends Enum<T>> void enumeration(CompoundTag source, String key, Class<T> type) {
		if (!source.contains(key)) {
			return;
		}

		String value = source.getString(key).orElseThrow(() -> new IllegalArgumentException("子发射枚举类型无效：" + key));

		if (!value.startsWith(PhotonSpawnConfig.ENUM_PREFIX)) {
			throw new IllegalArgumentException("子发射枚举编码无效：" + key);
		}

		Enum.valueOf(type, value.substring(PhotonSpawnConfig.ENUM_PREFIX.length()));
	}

	private static void bool(CompoundTag source, String key) {
		if (source.contains(key) && (!(source.get(key) instanceof ByteTag value) || value.byteValue() != 0 && value.byteValue() != 1)) {
			throw new IllegalArgumentException("子发射布尔字段无效：" + key);
		}
	}
}
