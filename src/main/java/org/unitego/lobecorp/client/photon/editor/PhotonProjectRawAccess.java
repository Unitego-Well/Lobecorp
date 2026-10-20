package org.unitego.lobecorp.client.photon.editor;

import net.minecraft.nbt.CompoundTag;

/// 容器原树随工程副本和历史保存；调用方只能取得独立副本。
@SuppressWarnings("UnnecessaryModifier")
public interface PhotonProjectRawAccess {
	public CompoundTag lobecorp$rawContainer();

	public void lobecorp$rawContainer(CompoundTag source);
}
