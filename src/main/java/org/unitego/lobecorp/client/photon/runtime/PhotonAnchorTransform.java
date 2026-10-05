package org.unitego.lobecorp.client.photon.runtime;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public record PhotonAnchorTransform(Vector3f position, Quaternionf rotation, Vector3f scale) {
	public PhotonAnchorTransform {
		position = new Vector3f(position);
		rotation = new Quaternionf(rotation);
		scale = new Vector3f(scale);
	}
}
