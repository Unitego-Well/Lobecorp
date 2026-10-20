package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;

import java.io.IOException;

/// 工程测试字段独立于FXData，保持原树和身份；发布定义与运行包不读取此字段。
public class PhotonProjectPreviewUtil {
	public static final String PROJECT_FIELD = "lobecorp_animation_preview";

	public static Definition read(CompoundTag source) throws IOException {
		var library = new CompoundTag();
		var version = source.get("version");
		library.put("version", version == null ? IntTag.valueOf(PhotonNamedLibrary.VERSION) : version.copy());
		var animations = new ListTag();
		animations.add(source.copy());
		library.put("animations", animations);
		return PhotonNamedCodecUtil.read(library).animations().getFirst();
	}

	public static CompoundTag checkedCopy(CompoundTag source) {
		try {
			read(source);
			return source.copy();
		} catch (IOException invalid) {
			throw new IllegalArgumentException("工程测试定义无法按当前协议读取。", invalid);
		}
	}
}
