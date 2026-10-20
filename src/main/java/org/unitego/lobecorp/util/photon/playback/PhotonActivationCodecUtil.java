package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;

import java.util.Set;

/// 合并原生已知字段时先删除旧值，既保留未知字段，也不复活已解除的绑定。
public class PhotonActivationCodecUtil {
	private static final Set<String> TRACK_FIELDS = Set.of("target", "name", "mute", "lock", "clips", "lobecorpActivation");
	private static final Set<String> CLIP_FIELDS = Set.of("start", "duration", "speed", "clipTarget", "seed", "randomSeed");

	public static CompoundTag track(CompoundTag source, CompoundTag current) {
		return merge(source, current, TRACK_FIELDS);
	}

	public static CompoundTag clip(CompoundTag source, CompoundTag current) {
		return merge(source, current, CLIP_FIELDS);
	}

	private static CompoundTag merge(CompoundTag source, CompoundTag current, Set<String> fields) {
		var result = source.copy();
		fields.forEach(result::remove);
		result.merge(current);
		return result;
	}
}
