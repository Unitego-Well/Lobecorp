package org.unitego.lobecorp.hitbox;

/// 椭球判断框的三个半轴。
///
/// @param radiusX X 轴半径
/// @param radiusY Y 轴半径
/// @param radiusZ Z 轴半径
public record EllipsoidSize(double radiusX, double radiusY, double radiusZ) implements HitboxSize {
	public EllipsoidSize {
		if (radiusX < 0.0 || radiusY < 0.0 || radiusZ < 0.0) {
			throw new IllegalArgumentException("ellipsoid radii must not be negative");
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.ELLIPSOID;
	}
}
