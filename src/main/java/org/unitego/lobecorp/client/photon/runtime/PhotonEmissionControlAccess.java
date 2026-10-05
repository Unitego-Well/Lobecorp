package org.unitego.lobecorp.client.photon.runtime;

@SuppressWarnings("UnnecessaryModifier")
public interface PhotonEmissionControlAccess {
	public boolean lobecorp$isEmissionStopped();

	public void lobecorp$setEmissionStopped(boolean stopped);

	public void lobecorp$clearParticles();

	public void lobecorp$setPreserveOnReset(boolean preserve);
}
