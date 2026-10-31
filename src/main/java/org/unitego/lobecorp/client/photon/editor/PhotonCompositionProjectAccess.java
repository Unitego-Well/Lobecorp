package org.unitego.lobecorp.client.photon.editor;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/// 组合作者数据与旧动画预览字段独立保存，所有读写均隔离副本。
public interface PhotonCompositionProjectAccess {
	@Nullable
	CompoundTag lobecorp$composition();

	void lobecorp$composition(@Nullable CompoundTag value);
}
