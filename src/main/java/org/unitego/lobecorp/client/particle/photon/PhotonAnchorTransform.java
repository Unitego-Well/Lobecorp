package org.unitego.lobecorp.client.particle.photon;

import org.joml.Quaternionf;
import org.joml.Vector3f;

record PhotonAnchorTransform(Vector3f position, Quaternionf rotation, Vector3f scale) {
	PhotonAnchorTransform {
		position = new Vector3f(position);
		rotation = new Quaternionf(rotation);
		scale = new Vector3f(scale);
	}
}
