package org.unitego.lobecorp.client.photon.playback;

import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions;

import javax.annotation.Nullable;
import java.util.List;

public interface PhotonActivationTrackAccess {
	PhotonActivationOptions lobecorp$activationOptions();

	void lobecorp$activationOptions(PhotonActivationOptions options);

	@Nullable
	CompoundTag lobecorp$activationSource();

	void lobecorp$activationSource(@Nullable CompoundTag source);

	void lobecorp$copyActivationData(CompoundTag source, List<CompoundTag> clips);
}
