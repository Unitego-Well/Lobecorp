package org.unitego.lobecorp.util.photon.playback;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Completion;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;

import java.io.IOException;
import java.util.UUID;

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

	/// 只合并明确编辑的制作属性；已有身份、轨道及未知字段保持原值。
	public static CompoundTag edit(CompoundTag source, String name, double duration, boolean autoDuration,
	                               boolean loop, int priority, Completion completion) {
		if (!name.equals(source.getStringOr("name", "")) && !name.matches("[a-z_]+")
				|| !Double.isFinite(duration) || duration < 0) {
			throw new IllegalArgumentException("名称仅允许小写字母和下划线，时长必须为有限非负数。");
		}

		var result = source.copy();
		result.putString("name", name);
		result.putDouble("duration", duration);
		result.putBoolean("autoDuration", autoDuration);
		result.putBoolean("loop", loop);
		result.putInt("priority", priority);
		result.putString("completion", completion.name());
		return checkedCopy(result);
	}

	public static CompoundTag create() {
		var source = new CompoundTag();
		source.putString("id", UUID.randomUUID().toString());
		source.putString("name", "project_test");
		return checkedCopy(source);
	}
}
