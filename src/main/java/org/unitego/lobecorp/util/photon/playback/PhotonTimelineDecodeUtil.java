package org.unitego.lobecorp.util.photon.playback;

import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/// 在原生读取前拒绝会被跳过的未知轨道/属性类型，未知普通字段仍由原树层保留。
public class PhotonTimelineDecodeUtil {
	public static Timeline read(HolderLookup.Provider provider, CompoundTag source) {
		if (source.contains("tracks") && !(source.get("tracks") instanceof ListTag)) {
			throw new IllegalArgumentException("轨道容器不是列表。");
		}
		check(source.getListOrEmpty("tracks"), 0);
		var result = new Timeline();
		result.deserializeNBT(provider, source.copy());
		return result;
	}

	public static void checkTracks(ListTag tracks) {
		check(tracks, 0);
	}

	private static void check(ListTag tracks, int depth) {
		if (depth > 64) {
			throw new IllegalArgumentException("制作轨道嵌套超出预算。");
		}
		for (var value : tracks) {
			if (!(value instanceof CompoundTag entry) || !(entry.get("data") instanceof CompoundTag data)) {
				throw new IllegalArgumentException("轨道包装无效。");
			}
			var name = entry.getStringOr("type", "");
			if (PhotonRegistries.TIMELINE_TRACKS.get(name) == null) {
				throw new IllegalArgumentException("未知轨道不能跳过：" + name);
			}
			PhotonPlaybackTagUtil.validateNativeTrack(data);
			if (name.equals("group")) {
				if (data.contains("children") && !(data.get("children") instanceof ListTag)) {
					throw new IllegalArgumentException("子轨道容器不是列表。");
				}
				check(data.getListOrEmpty("children"), depth + 1);
			} else if (name.equals("animation")) {
				if (data.contains("properties") && !(data.get("properties") instanceof ListTag)) {
					throw new IllegalArgumentException("动画属性容器不是列表。");
				}
				for (var property : data.getListOrEmpty("properties")) {
					if (!(property instanceof CompoundTag tag) || PhotonRegistries.ANIMATED_PROPERTIES.get(tag.getStringOr("type", "")) == null) {
						throw new IllegalArgumentException("未知动画属性不能跳过。");
					}
				}
			}
		}
	}
}
