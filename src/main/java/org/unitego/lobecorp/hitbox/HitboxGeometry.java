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
		if (instance.size() instanceof RingCylinderSize ring) {
			return intersectsRingCylinder(ring, localBounds(instance, bounds));
		}
		if (instance.size() instanceof BoxRingCylinderSize ring) {
			return intersectsBoxRingCylinder(ring, localBounds(instance, bounds));
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
			case RingCylinderSize ring -> cylinderSupport(ring.radius(), ring.height(), direction);
			case BoxSize box -> boxSupport(box.width(), box.height(), box.depth(), direction);
			case BoxRingCylinderSize ring -> boxSupport(ring.width(), ring.height(), ring.depth(), direction);
			case SectorCylinderSize sector -> sectorSupport(sector, direction);
			case ConeSize cone -> coneSupport(cone, direction);
			case EllipsoidSize ellipsoid -> ellipsoidSupport(ellipsoid, direction);
			default -> throw new IllegalStateException("Unsupported hitbox size: " + size.getClass().getName());
		};
	}

	private static Vec3 boxSupport(double width, double height, double depth, Vec3 direction) {
		return new Vec3(
			direction.x >= 0.0 ? width / 2.0 : -width / 2.0,
			direction.y >= 0.0 ? height / 2.0 : -height / 2.0,
			direction.z >= 0.0 ? depth / 2.0 : -depth / 2.0
		);
	}

	private static AABB localBounds(HitboxInstance instance, AABB bounds) {
		Vec3 position = instance.position();
		Vec3 rotation = instance.rotation();
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (int xIndex = 0; xIndex < 2; xIndex++) {
			for (int yIndex = 0; yIndex < 2; yIndex++) {
				for (int zIndex = 0; zIndex < 2; zIndex++) {
					Vec3 corner = new Vec3(
						xIndex == 0 ? bounds.minX : bounds.maxX,
						yIndex == 0 ? bounds.minY : bounds.maxY,
						zIndex == 0 ? bounds.minZ : bounds.maxZ
					);
					Vec3 local = inverseRotate(corner.subtract(position), rotation);
					minX = Math.min(minX, local.x);
					minY = Math.min(minY, local.y);
					minZ = Math.min(minZ, local.z);
					maxX = Math.max(maxX, local.x);
					maxY = Math.max(maxY, local.y);
					maxZ = Math.max(maxZ, local.z);
				}
			}
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static boolean intersectsRingCylinder(RingCylinderSize ring, AABB bounds) {
		double halfHeight = ring.height() / 2.0;
		if (bounds.maxY < -halfHeight || bounds.minY > halfHeight) {
			return false;
		}
		double nearestX = bounds.minX > 0.0 ? bounds.minX : Math.min(bounds.maxX, 0.0);
		double nearestZ = bounds.minZ > 0.0 ? bounds.minZ : Math.min(bounds.maxZ, 0.0);
		if (nearestX * nearestX + nearestZ * nearestZ > ring.radius() * ring.radius()) {
			return false;
		}
		double farthestX = Math.max(bounds.minX * bounds.minX, bounds.maxX * bounds.maxX);
		double farthestZ = Math.max(bounds.minZ * bounds.minZ, bounds.maxZ * bounds.maxZ);
		return ring.innerRadius() == 0.0
				|| farthestX + farthestZ > ring.innerRadius() * ring.innerRadius();
	}

	private static boolean intersectsBoxRingCylinder(BoxRingCylinderSize ring, AABB bounds) {
		double halfHeight = ring.height() / 2.0;
		if (bounds.maxY < -halfHeight || bounds.minY > halfHeight) {
			return false;
		}
		double halfWidth = ring.width() / 2.0;
		double halfDepth = ring.depth() / 2.0;
		if (bounds.maxX < -halfWidth || bounds.minX > halfWidth
				|| bounds.maxZ < -halfDepth || bounds.minZ > halfDepth) {
			return false;
		}
		double innerHalfWidth = ring.innerWidth() / 2.0;
		double innerHalfDepth = ring.innerDepth() / 2.0;
		if (innerHalfWidth == 0.0 || innerHalfDepth == 0.0) {
			return true;
		}
		return bounds.minX < -innerHalfWidth || bounds.maxX > innerHalfWidth
				|| bounds.minZ < -innerHalfDepth || bounds.maxZ > innerHalfDepth;
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
