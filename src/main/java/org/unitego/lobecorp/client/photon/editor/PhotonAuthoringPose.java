package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.photon.client.gameobject.FXObject;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/// 制作姿态与预览姿态分别持有；比较并恢复仍由当前时间线拥有的分量。
public record PhotonAuthoringPose(Vector3f position, Quaternionf rotation, Vector3f scale) {
	public PhotonAuthoringPose {
		position = new Vector3f(position);
		rotation = new Quaternionf(rotation);
		scale = new Vector3f(scale);
	}

	public static PhotonAuthoringPose capture(FXObject object) {
		return new PhotonAuthoringPose(object.transform().localPosition(), object.transform().localRotation(), object.transform().localScale());
	}

	public PhotonAuthoringPose authored(PhotonAuthoringPose before, PhotonAuthoringPose applied) {
		return new PhotonAuthoringPose(position.equals(applied.position) ? before.position : position,
				rotation.equals(applied.rotation) ? before.rotation : rotation, scale.equals(applied.scale) ? before.scale : scale);
	}

	public void apply(FXObject object) {
		object.transform().localPosition(new Vector3f(position));
		object.transform().localRotation(new Quaternionf(rotation));
		object.transform().localScale(new Vector3f(scale));
	}
}
