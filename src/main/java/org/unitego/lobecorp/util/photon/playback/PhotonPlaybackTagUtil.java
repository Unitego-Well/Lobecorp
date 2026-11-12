package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildClip;

import java.util.HashSet;
import java.util.UUID;

/// 扩展轨道在交给原生宽松读取器前校验已知字段，未知字段仍保留在原树。
public class PhotonPlaybackTagUtil {
	public static void validateChildTrack(CompoundTag source) {
		uuid(source, "target");
		text(source, "name");
		bool(source, "mute");
		bool(source, "lock");

		var identities = new HashSet<UUID>();
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

			var identity = clip.getStringOr("id", "");
			var id = UUID.fromString(identity);
			if (!id.toString().equalsIgnoreCase(identity) || !identities.add(id)) {
				throw new IllegalArgumentException("子片段身份无效或重复。");
			}
			var checked = new PhotonChildClip(clip.getDoubleOr("start", 0), clip.getDoubleOr("duration", 0), clip.getFloatOr("speed", 1));
			checked.playbackDuration = clip.getDoubleOr("playbackDuration", -1);
			checked.mode = PhotonChildClip.Mode.valueOf(clip.getStringOr("mode", "CONTROLLED"));
			checked.completion = PhotonChildClip.Completion.valueOf(clip.getStringOr("completion", "INHERIT"));
			checked.inputs = PhotonNamedCodecUtil.inputs(clip);
			checked.validate();

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

	public static void validateNativeTrack(CompoundTag source) {
		uuid(source, "target");
		text(source, "name");
		bool(source, "mute");
		bool(source, "lock");
		for (var value : list(source, "clips")) {
			if (!(value instanceof CompoundTag clip)) {
				throw new IllegalArgumentException("轨道含非片段数据。");
			}
			for (var key : new String[]{"start", "duration", "speed", "seed"}) {
				number(clip, key);
			}
			bool(clip, "randomSeed");
			uuid(clip, "clipTarget");
			double start = clip.getDoubleOr("start", 0);
			double duration = clip.getDoubleOr("duration", 0);
			double speed = clip.getDoubleOr("speed", 1);
			if (start < 0 || duration < 0 || speed < 0 || speed > Float.MAX_VALUE || !Double.isFinite(start + duration)) {
				throw new IllegalArgumentException("轨道片段窗口无效。");
			}
		}
		for (var value : list(source, "signals")) {
			if (!(value instanceof CompoundTag signal)) {
				throw new IllegalArgumentException("信号列表含非信号值。");
			}
			number(signal, "time");
			text(signal, "name");
			if (signal.getDoubleOr("time", 0) < 0 || signal.contains("data") && !(signal.get("data") instanceof CompoundTag)) {
				throw new IllegalArgumentException("信号时间或附加数据无效。");
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
