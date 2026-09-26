package org.unitego.lobecorp.hitbox;

/// 圆环柱判断框尺寸。
///
/// @param radius 外半径
/// @param height 总高度
/// @param thickness 径向厚度
public record RingCylinderSize(double radius, double height, double thickness) implements HitboxSize {
	public RingCylinderSize {
		if (radius < 0.0 || height < 0.0 || thickness < 0.0) {
			throw new IllegalArgumentException("ring cylinder size must not be negative");
		}
	}

	public double innerRadius() {
		return Math.max(0.0, radius - thickness);
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.RING_CYLINDER;
	}
}
