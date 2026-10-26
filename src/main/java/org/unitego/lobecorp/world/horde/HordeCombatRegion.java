package org.unitego.lobecorp.world.horde;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/// 独立运行期战斗方区；水平沿世界轴对齐，竖直边界保留目标真实高度。
public record HordeCombatRegion(AABB bounds) {
	public static HordeCombatRegion around(AABB target, double margin) {
		double half = Math.max(target.getXsize(), target.getZsize()) / 2 + margin;
		var center = target.getCenter();
		return new HordeCombatRegion(new AABB(center.x - half, target.minY, center.z - half,
				center.x + half, target.maxY, center.z + half));
	}

	public boolean containsHorizontal(Vec3 point) {
		return point.x >= bounds.minX && point.x <= bounds.maxX
				&& point.z >= bounds.minZ && point.z <= bounds.maxZ;
	}

	public Vec3 clampHorizontal(Vec3 point, double radius) {
		double half = Math.max(0, bounds.getXsize() / 2 - radius);
		var center = bounds.getCenter();
		return new Vec3(Math.clamp(point.x, center.x - half, center.x + half), point.y,
				Math.clamp(point.z, center.z - half, center.z + half));
	}
}
