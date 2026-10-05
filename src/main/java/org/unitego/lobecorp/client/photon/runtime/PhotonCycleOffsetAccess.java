package org.unitego.lobecorp.client.photon.runtime;

@SuppressWarnings("UnnecessaryModifier")
public interface PhotonCycleOffsetAccess {
	public float lobecorp$getCycleOffset(Component component);

	public enum Component {
		X, Y, Z, ROLL, PITCH, YAW, ORBITAL_X, ORBITAL_Y, ORBITAL_Z, CENTER_X, CENTER_Y, CENTER_Z, RADIAL, MULTIPLIER, RED, GREEN, BLUE, ALPHA, UV
	}
}
