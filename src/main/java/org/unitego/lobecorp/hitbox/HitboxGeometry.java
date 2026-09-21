package org.unitego.lobecorp.hitbox;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/// 判断框旋转、外包围盒和凸体相交计算。
public final class HitboxGeometry {
	/// GJK 防止退化模型无限迭代的上限。
	private static final int MAXIMUM_GJK_ITERATIONS = 32;
	/// 向量退化判断的平方误差。
	private static final double DIRECTION_EPSILON_SQUARED = 1.0E-12;

	private HitboxGeometry() {
	}

	/// 判断实例的实际旋转几何体是否与实体 AABB 相交。
	///
	/// @param instance 判断框实例
	/// @param bounds 目标实体的世界 AABB
	/// @return 两个凸体是否相交
	public static boolean intersects(HitboxInstance instance, AABB bounds) {
		if (!boundingBox(instance).intersects(bounds)) {
			return false;
		}

		Vec3 direction = bounds.getCenter().subtract(instance.position());
		if (direction.lengthSqr() <= DIRECTION_EPSILON_SQUARED) {
			direction = new Vec3(1.0, 0.0, 0.0);
		}
		List<Vec3> simplex = new ArrayList<>(4);
		Vec3 point = minkowskiSupport(instance, bounds, direction);
		simplex.add(point);
		direction = point.reverse();

		for (int iteration = 0; iteration < MAXIMUM_GJK_ITERATIONS; iteration++) {
			if (direction.lengthSqr() <= DIRECTION_EPSILON_SQUARED) {
				return true;
			}
			point = minkowskiSupport(instance, bounds, direction);
			if (point.dot(direction) < 0.0) {
				return false;
			}
			simplex.add(0, point);
			SimplexResult result = updateSimplex(simplex);
			if (result.containsOrigin()) {
				return true;
			}
			direction = result.direction();
		}
		return false;
	}

	/// 计算旋转形状的世界轴对齐外包围盒。
	///
	/// @param instance 判断框实例
	/// @return 可用于候选实体粗筛的 AABB
	public static AABB boundingBox(HitboxInstance instance) {
		Vec3 positiveX = support(instance, new Vec3(1.0, 0.0, 0.0));
		Vec3 negativeX = support(instance, new Vec3(-1.0, 0.0, 0.0));
		Vec3 positiveY = support(instance, new Vec3(0.0, 1.0, 0.0));
		Vec3 negativeY = support(instance, new Vec3(0.0, -1.0, 0.0));
		Vec3 positiveZ = support(instance, new Vec3(0.0, 0.0, 1.0));
		Vec3 negativeZ = support(instance, new Vec3(0.0, 0.0, -1.0));
		return new AABB(negativeX.x, negativeY.y, negativeZ.z, positiveX.x, positiveY.y, positiveZ.z);
	}

	/// 按 X、Y、Z 顺序应用欧拉旋转。
	///
	/// @param vector 原向量
	/// @param pitchDegrees X 轴角度
	/// @param yawDegrees Y 轴角度
	/// @param rollDegrees Z 轴角度
	/// @return 旋转后的向量
	public static Vec3 rotate(Vec3 vector, double pitchDegrees, double yawDegrees, double rollDegrees) {
		Vec3 rotated = rotateX(vector, Math.toRadians(pitchDegrees));
		rotated = rotateY(rotated, Math.toRadians(yawDegrees));
		return rotateZ(rotated, Math.toRadians(rollDegrees));
	}

	private static Vec3 inverseRotate(Vec3 vector, Vec3 rotation) {
		Vec3 rotated = rotateZ(vector, -Math.toRadians(rotation.z));
		rotated = rotateY(rotated, -Math.toRadians(rotation.y));
		return rotateX(rotated, -Math.toRadians(rotation.x));
	}

	private static Vec3 minkowskiSupport(HitboxInstance instance, AABB bounds, Vec3 direction) {
		return support(instance, direction).subtract(aabbSupport(bounds, direction.reverse()));
	}

	private static Vec3 support(HitboxInstance instance, Vec3 worldDirection) {
		Vec3 localDirection = inverseRotate(worldDirection, instance.rotation());
		Vec3 localPoint = localSupport(instance.size(), localDirection);
		Vec3 worldPoint = rotate(localPoint, instance.rotation().x, instance.rotation().y, instance.rotation().z);
		return worldPoint.add(instance.position());
	}

	private static Vec3 localSupport(HitboxSize size, Vec3 direction) {
		return switch (size) {
			case SphereSize sphere -> sphereSupport(sphere, direction);
			case CylinderSize cylinder -> cylinderSupport(cylinder.radius(), cylinder.height(), direction);
			case BoxSize box -> new Vec3(
					direction.x >= 0.0 ? box.width() / 2.0 : -box.width() / 2.0,
					direction.y >= 0.0 ? box.height() / 2.0 : -box.height() / 2.0,
					direction.z >= 0.0 ? box.depth() / 2.0 : -box.depth() / 2.0
			);
			case SectorCylinderSize sector -> sectorSupport(sector, direction);
			case ConeSize cone -> coneSupport(cone, direction);
			case EllipsoidSize ellipsoid -> ellipsoidSupport(ellipsoid, direction);
			default -> throw new IllegalStateException("Unsupported hitbox size: " + size.getClass().getName());
		};
	}

	private static Vec3 ellipsoidSupport(EllipsoidSize ellipsoid, Vec3 direction) {
		double x = ellipsoid.radiusX() * direction.x;
		double y = ellipsoid.radiusY() * direction.y;
		double z = ellipsoid.radiusZ() * direction.z;
		double denominator = Math.sqrt(x * x + y * y + z * z);
		if (denominator <= DIRECTION_EPSILON_SQUARED) {
			return Vec3.ZERO;
		}
		return new Vec3(
				ellipsoid.radiusX() * x / denominator,
				ellipsoid.radiusY() * y / denominator,
				ellipsoid.radiusZ() * z / denominator
		);
	}

	private static Vec3 coneSupport(ConeSize cone, Vec3 direction) {
		double radius = Math.tan(Math.toRadians(cone.angleDegrees() / 2.0)) * cone.length();
		double radialLength = Math.sqrt(direction.x * direction.x + direction.y * direction.y);
		Vec3 base;
		if (radialLength == 0.0) {
			base = new Vec3(0.0, 0.0, cone.length());
		} else {
			base = new Vec3(direction.x / radialLength * radius,
					direction.y / radialLength * radius, cone.length());
		}
		if (base.dot(direction) > 0.0) {
			return base;
		}
		return Vec3.ZERO;
	}

	private static Vec3 sphereSupport(SphereSize sphere, Vec3 direction) {
		if (direction.lengthSqr() <= DIRECTION_EPSILON_SQUARED) {
			return Vec3.ZERO;
		}
		return direction.normalize().scale(sphere.radius());
	}

	private static Vec3 cylinderSupport(double radius, double height, Vec3 direction) {
		double horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
		double x = 0.0;
		double z = 0.0;
		if (horizontalLength > 0.0) {
			x = direction.x / horizontalLength * radius;
			z = direction.z / horizontalLength * radius;
		}
		double y = direction.y >= 0.0 ? height / 2.0 : -height / 2.0;
		return new Vec3(x, y, z);
	}

	private static Vec3 sectorSupport(SectorCylinderSize sector, Vec3 direction) {
		double halfAngle = Math.toRadians(sector.angleDegrees() / 2.0);
		double directionAngle = Math.atan2(direction.x, direction.z);
		double clampedAngle = Math.max(-halfAngle, Math.min(halfAngle, directionAngle));
		double x = Math.sin(clampedAngle) * sector.radius();
		double z = Math.cos(clampedAngle) * sector.radius();
		if (x * direction.x + z * direction.z < 0.0) {
			x = 0.0;
			z = 0.0;
		}
		double y = direction.y >= 0.0 ? sector.height() / 2.0 : -sector.height() / 2.0;
		return new Vec3(x, y, z);
	}

	private static Vec3 aabbSupport(AABB bounds, Vec3 direction) {
		return new Vec3(
				direction.x >= 0.0 ? bounds.maxX : bounds.minX,
				direction.y >= 0.0 ? bounds.maxY : bounds.minY,
				direction.z >= 0.0 ? bounds.maxZ : bounds.minZ
		);
	}

	private static SimplexResult updateSimplex(List<Vec3> simplex) {
		return switch (simplex.size()) {
			case 2 -> updateLine(simplex);
			case 3 -> updateTriangle(simplex);
			case 4 -> updateTetrahedron(simplex);
			default -> new SimplexResult(false, simplex.getFirst().reverse());
		};
	}

	private static SimplexResult updateLine(List<Vec3> simplex) {
		Vec3 a = simplex.get(0);
		Vec3 b = simplex.get(1);
		Vec3 ao = a.reverse();
		Vec3 ab = b.subtract(a);
		if (ab.dot(ao) > 0.0) {
			return new SimplexResult(false, tripleCross(ab, ao, ab));
		}
		simplex.clear();
		simplex.add(a);
		return new SimplexResult(false, ao);
	}

	private static SimplexResult updateTriangle(List<Vec3> simplex) {
		Vec3 a = simplex.get(0);
		Vec3 b = simplex.get(1);
		Vec3 c = simplex.get(2);
		Vec3 ao = a.reverse();
		Vec3 ab = b.subtract(a);
		Vec3 ac = c.subtract(a);
		Vec3 normal = ab.cross(ac);

		if (normal.cross(ac).dot(ao) > 0.0) {
			if (ac.dot(ao) > 0.0) {
				simplex.clear();
				simplex.add(a);
				simplex.add(c);
				return new SimplexResult(false, tripleCross(ac, ao, ac));
			}
			simplex.clear();
			simplex.add(a);
			simplex.add(b);
			return updateLine(simplex);
		}
		if (ab.cross(normal).dot(ao) > 0.0) {
			simplex.clear();
			simplex.add(a);
			simplex.add(b);
			return updateLine(simplex);
		}
		if (normal.dot(ao) > 0.0) {
			return new SimplexResult(false, normal);
		}
		simplex.set(1, c);
		simplex.set(2, b);
		return new SimplexResult(false, normal.reverse());
	}

	private static SimplexResult updateTetrahedron(List<Vec3> simplex) {
		Vec3 a = simplex.get(0);
		Vec3 b = simplex.get(1);
		Vec3 c = simplex.get(2);
		Vec3 d = simplex.get(3);
		Vec3 ao = a.reverse();

		Vec3 abc = outwardNormal(a, b, c, d);
		if (abc.dot(ao) > 0.0) {
			setTriangle(simplex, a, b, c);
			return updateTriangle(simplex);
		}
		Vec3 acd = outwardNormal(a, c, d, b);
		if (acd.dot(ao) > 0.0) {
			setTriangle(simplex, a, c, d);
			return updateTriangle(simplex);
		}
		Vec3 adb = outwardNormal(a, d, b, c);
		if (adb.dot(ao) > 0.0) {
			setTriangle(simplex, a, d, b);
			return updateTriangle(simplex);
		}
		return new SimplexResult(true, Vec3.ZERO);
	}

	private static Vec3 outwardNormal(Vec3 a, Vec3 b, Vec3 c, Vec3 opposite) {
		Vec3 normal = b.subtract(a).cross(c.subtract(a));
		if (normal.dot(opposite.subtract(a)) > 0.0) {
			return normal.reverse();
		}
		return normal;
	}

	private static void setTriangle(List<Vec3> simplex, Vec3 a, Vec3 b, Vec3 c) {
		simplex.clear();
		simplex.add(a);
		simplex.add(b);
		simplex.add(c);
	}

	private static Vec3 tripleCross(Vec3 first, Vec3 second, Vec3 third) {
		return first.cross(second).cross(third);
	}

	private static Vec3 rotateX(Vec3 vector, double angle) {
		double cosine = Math.cos(angle);
		double sine = Math.sin(angle);
		return new Vec3(vector.x, vector.y * cosine - vector.z * sine, vector.y * sine + vector.z * cosine);
	}

	private static Vec3 rotateY(Vec3 vector, double angle) {
		double cosine = Math.cos(angle);
		double sine = Math.sin(angle);
		return new Vec3(vector.x * cosine + vector.z * sine, vector.y, -vector.x * sine + vector.z * cosine);
	}

	private static Vec3 rotateZ(Vec3 vector, double angle) {
		double cosine = Math.cos(angle);
		double sine = Math.sin(angle);
		return new Vec3(vector.x * cosine - vector.y * sine, vector.x * sine + vector.y * cosine, vector.z);
	}

	private record SimplexResult(boolean containsOrigin, Vec3 direction) {
	}
}
