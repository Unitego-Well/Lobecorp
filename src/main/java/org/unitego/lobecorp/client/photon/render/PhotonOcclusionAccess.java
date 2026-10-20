package org.unitego.lobecorp.client.photon.render;

import org.unitego.lobecorp.config.photon.PhotonOcclusionConfig;

public interface PhotonOcclusionAccess {
	boolean lobecorp$isOcclusionEnabled();

	float lobecorp$getOcclusionAlphaCutoff();

	PhotonOcclusionConfig lobecorp$getOcclusionConfig();
}
