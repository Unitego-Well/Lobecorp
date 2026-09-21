package org.unitego.lobecorp.hitbox;

/// 圆柱判断框尺寸。
///
/// @param radius 半径
/// @param height 总高度
public record CylinderSize(double radius, double height) implements HitboxSize {
	public CylinderSize {
		if (radius < 0.0 || height < 0.0) {
			throw new IllegalArgumentException("cylinder size must not be negative");
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.CYLINDER;
	}
}
