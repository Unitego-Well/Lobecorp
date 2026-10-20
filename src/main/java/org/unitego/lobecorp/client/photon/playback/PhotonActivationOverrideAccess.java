package org.unitego.lobecorp.client.photon.playback;

import javax.annotation.Nullable;

public interface PhotonActivationOverrideAccess {
	boolean lobecorp$ownActivation();

	void lobecorp$ownActivation(boolean own);

	@Nullable
	Object lobecorp$activationOwner();

	void lobecorp$activationOwner(@Nullable Object owner);
}
