package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/// 施放与预热共用的命名播放定义变换；只处理NBT，不依赖客户端对象或注册表。
public final class PhotonNamedPlaybackDefinitionUtil {
	private PhotonNamedPlaybackDefinitionUtil() {
	}

	public static CompoundTag prepare(CompoundTag definition) {
		var result = definition.copy();
		result.putString("defaultAnimation", "");

		for (var animation : result.getListOrEmpty("animations")) {
			controlled(((CompoundTag) animation).getListOrEmpty("tracks"));
		}

		return result;
	}

	private static void controlled(ListTag tracks) {
		for (var tag : tracks) {
			var track = (CompoundTag) tag;
			var data = track.getCompoundOrEmpty("data");

			if (track.getStringOr("type", "").equals("group")) {
				controlled(data.getListOrEmpty("children"));
			} else if (track.getStringOr("type", "").equals("lobecorp_child_animation")) {
				for (var clip : data.getListOrEmpty("clips")) {
					((CompoundTag) clip).putString("mode", "CONTROLLED");
				}
			}
		}
	}
}
