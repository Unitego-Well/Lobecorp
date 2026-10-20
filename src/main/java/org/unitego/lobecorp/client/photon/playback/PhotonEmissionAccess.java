package org.unitego.lobecorp.client.photon.playback;

public interface PhotonEmissionAccess {
	boolean lobecorp$emissionStopped();

	void lobecorp$emissionStopped(boolean stopped);

	void lobecorp$preserveParticles(boolean preserve);

	void lobecorp$clearParticles();
}
