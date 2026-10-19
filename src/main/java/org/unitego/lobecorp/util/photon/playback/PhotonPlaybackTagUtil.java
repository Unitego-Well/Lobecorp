package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;

/// 扩展轨道在交给原生宽松读取器前校验已知字段，未知字段仍保留在原树。
public class PhotonPlaybackTagUtil {
	public static void validateChildTrack(CompoundTag source) {
		uuid(source, "target");
		text(source, "name");
		bool(source, "mute");
		bool(source, "lock");

		for (var value : list(source, "clips")) {
			if (!(value instanceof CompoundTag clip)) {
				throw new IllegalArgumentException("子动画轨道含非片段值。");
			}

			text(clip, "id");
			text(clip, "animation");
			text(clip, "mode");
			text(clip, "completion");
			uuid(clip, "clipTarget");
			bool(clip, "randomSeed");

			for (var key : new String[]{"start", "duration", "speed", "playbackDuration", "seed"}) {
				number(clip, key);
			}

			for (var input : list(clip, "inputs")) {
				if (!(input instanceof CompoundTag entry)) {
					throw new IllegalArgumentException("子动画形参含非复合值。");
				}

				text(entry, "target");
				text(entry, "source");
				bool(entry, "follow");
			}
		}
	}

	private static ListTag list(CompoundTag source, String key) {
		if (source.contains(key) && !(source.get(key) instanceof ListTag)) {
			throw new IllegalArgumentException("轨道列表类型无效：" + key);
		}

		return source.getListOrEmpty(key);
	}

	private static void number(CompoundTag source, String key) {
		if (source.contains(key) && (!(source.get(key) instanceof NumericTag value) || !Double.isFinite(value.doubleValue()))) {
			throw new IllegalArgumentException("轨道数值类型无效：" + key);
		}
	}

	private static void uuid(CompoundTag source, String key) {
		if (source.contains(key) && source.read(key, UUIDUtil.CODEC).isEmpty()) {
			throw new IllegalArgumentException("轨道引用身份无效：" + key);
		}
	}

	private static void text(CompoundTag source, String key) {
		if (source.contains(key) && !(source.get(key) instanceof StringTag)) {
			throw new IllegalArgumentException("轨道文本类型无效：" + key);
		}
	}

	private static void bool(CompoundTag source, String key) {
		if (source.contains(key) && (!(source.get(key) instanceof ByteTag value) || value.byteValue() != 0 && value.byteValue() != 1)) {
			throw new IllegalArgumentException("轨道布尔值无效：" + key);
		}
	}
}
