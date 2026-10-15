package org.unitego.lobecorp.util.photon.animation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;

/// 工程测试时间轴使用原动画定义格式，独立保存在工程数据中。
public class PhotonAnimationProjectPreviewUtil {
	/// .fxproj 中与 fx 同级的测试字段，FX 与 FXPack 序列化不读取此字段。
	public static final String PROJECT_FIELD = "lobecorp_animation_preview";
	/// 测试定义记录当前动画格式版本，读取时复用原定义版本规则。
	private static final String VERSION_FIELD = "version";
	/// 测试播放的内部名称，不作为导出动画或外部调用入口。
	private static final String INTERNAL_NAME = "lobecorp_animation_preview";

	public static PhotonAnimationDefinition create() {
		var definition = new PhotonAnimationDefinition();
		definition.name = INTERNAL_NAME;
		definition.autoDuration = true;
		definition.autoEnableModules = false;
		return definition;
	}

	public static CompoundTag write(HolderLookup.Provider provider, PhotonAnimationDefinition definition) {
		var tag = definition.write(provider);
		tag.putInt(VERSION_FIELD, PhotonAnimationDataUtil.VERSION);
		return tag;
	}

	public static PhotonAnimationDefinition read(HolderLookup.Provider provider, CompoundTag tag) {
		var version = tag.getIntOr(VERSION_FIELD, PhotonAnimationDataUtil.VERSION);

		if (version < PhotonAnimationDataUtil.LEGACY_VERSION || version > PhotonAnimationDataUtil.VERSION) {
			throw new IllegalArgumentException();
		}

		return PhotonAnimationDefinition.read(provider, tag, version);
	}
}
