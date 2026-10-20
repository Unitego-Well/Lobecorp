package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/// 同一制作定义内复制轨道时重建子窗口的局部身份，目标对象和被调用动画身份保持。
public class PhotonTrackCopyUtil {
	public static CompoundTag copy(CompoundTag track) {
		var result = track.copy();
		String type = result.getStringOr("type", "");
		var data = result.getCompoundOrEmpty("data");
		if (type.equals("group")) {
			var children = data.getListOrEmpty("children");
			for (int index = 0; index < children.size(); index++) {
				children.set(index, copy((CompoundTag) children.get(index)));
			}
		} else if (type.equals("lobecorp_child_animation") || type.equals("lobecorp_animation_call")) {
			for (var value : data.getListOrEmpty("clips")) {
				var clip = (CompoundTag) value;
				if (clip.contains("id")) {
					var id = UUID.fromString(clip.getStringOr("id", ""));
					if (!id.toString().equalsIgnoreCase(clip.getStringOr("id", ""))) {
						throw new IllegalArgumentException("子窗口UUID格式不完整。");
					}
					clip.putString("id", UUID.randomUUID().toString());
				}
			}
		}
		return result;
	}
}
