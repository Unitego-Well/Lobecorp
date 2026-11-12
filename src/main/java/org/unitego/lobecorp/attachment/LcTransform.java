package org.unitego.lobecorp.attachment;

import net.minecraft.world.phys.Vec3;
import org.joml.*;

/// 以格为位移单位的变换；旋转副本阻止接收端修改共享姿态。
public record LcTransform(Vec3 position, Quaternionf rotation, Vec3 scale) {
	public LcTransform {
		rotation = new Quaternionf(rotation);
	}

	public static LcTransform identity() {
		return new LcTransform(Vec3.ZERO, new Quaternionf(), new Vec3(1, 1, 1));
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

		var rotation = scale.x == 0 || scale.y == 0 || scale.z == 0
				? singularRotation(orientation, scale)
				: orientation.getUnnormalizedRotation(new Quaternionf()).normalize();
		return new LcTransform(new Vec3(position.x, position.y, position.z), rotation,
				new Vec3(scale.x, scale.y, scale.z));
	}

	/// 零缩放轴的旋转不唯一；用有效轴补齐右手基，单轴采用最短弧旋转。
	private static Quaternionf singularRotation(Matrix4dc matrix, Vector3d scale) {
		var x = new Vector3d(matrix.m00(), matrix.m01(), matrix.m02());
		var y = new Vector3d(matrix.m10(), matrix.m11(), matrix.m12());
		var z = new Vector3d(matrix.m20(), matrix.m21(), matrix.m22());
		if (scale.x != 0) {
			x.div(scale.x);
		}
		if (scale.y != 0) {
			y.div(scale.y);
		}
		if (scale.z != 0) {
			z.div(scale.z);
		}

		if (scale.x == 0) {
			if (scale.y == 0) {
				return scale.z == 0 ? new Quaternionf()
						: new Quaternionf().rotationTo(0, 0, 1, (float) z.x, (float) z.y, (float) z.z);
			}
			x.set(y).cross(z);
			if (x.lengthSquared() == 0) {
				return new Quaternionf().rotationTo(0, 1, 0, (float) y.x, (float) y.y, (float) y.z);
			}
			x.normalize();
			z.set(x).cross(y).normalize();
		} else {
			if (scale.y == 0) {
				y.set(z).cross(x);
				if (y.lengthSquared() == 0) {
					return new Quaternionf().rotationTo(1, 0, 0, (float) x.x, (float) x.y, (float) x.z);
				}
				y.normalize();
				z.set(x).cross(y).normalize();
			} else {
				z.set(x).cross(y);
				if (z.lengthSquared() == 0) {
					return new Quaternionf().rotationTo(1, 0, 0, (float) x.x, (float) x.y, (float) x.z);
				}
				z.normalize();
				y.set(z).cross(x).normalize();
			}
		}
		return new Quaternionf().setFromNormalized(new Matrix3d(x, y, z)).normalize();
	}

	@Override
	public Quaternionf rotation() {
		return new Quaternionf(rotation);
	}

	public Matrix4d matrix() {
		return new Matrix4d().translation(position.x, position.y, position.z)
				.rotate(rotation).scale(scale.x, scale.y, scale.z);
	}

	public LcTransform compose(LcTransform local) {
		return fromMatrix(matrix().mul(local.matrix()));
	}

	public LcTransform interpolate(LcTransform next, double weight) {
		return new LcTransform(position.lerp(next.position, weight),
				rotation().slerp(next.rotation, (float) weight), scale.lerp(next.scale, weight));
	}
}
