package org.unitego.lobecorp.client.photon.playback;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

public interface PhotonProjectPreviewAccess {
	@Nullable
	CompoundTag lobecorp$projectPreview();

	void lobecorp$projectPreview(@Nullable CompoundTag source);
}
