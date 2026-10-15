package org.unitego.lobecorp.attachment;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/// 以格为位移单位的变换；旋转副本阻止接收端修改共享姿态。
public record LcTransform(Vec3 position, Quaternionf rotation, Vec3 scale) {
	public LcTransform {
		rotation = new Quaternionf(rotation);
	}

	@Override
	public Quaternionf rotation() {
		return new Quaternionf(rotation);
	}

	public static LcTransform identity() {
		return new LcTransform(Vec3.ZERO, new Quaternionf(), new Vec3(1, 1, 1));
	}

	public Matrix4d matrix() {
		return new Matrix4d().translation(position.x, position.y, position.z)
				.rotate(rotation).scale(scale.x, scale.y, scale.z);
	}

	public static LcTransform fromMatrix(Matrix4dc matrix) {
		var position = matrix.getTranslation(new Vector3d());
		var scale = matrix.getScale(new Vector3d());
		var orientation = new Matrix4d(matrix);

		// 将镜像反射固定归入 X 缩放，避免提取四元数时丢失变换的手性。
		if (matrix.determinant() < 0) {
			scale.x = -scale.x;
			orientation.scale(-1, 1, 1);
		}

		return new LcTransform(new Vec3(position.x, position.y, position.z),
				orientation.getUnnormalizedRotation(new Quaternionf()).normalize(),
				new Vec3(scale.x, scale.y, scale.z));
	}

	public LcTransform compose(LcTransform local) {
		return fromMatrix(matrix().mul(local.matrix()));
	}

	public LcTransform interpolate(LcTransform next, double weight) {
		return new LcTransform(position.lerp(next.position, weight),
				rotation().slerp(next.rotation, (float) weight), scale.lerp(next.scale, weight));
	}
}
