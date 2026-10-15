package org.unitego.lobecorp.world.hitbox.geometry;

/// 椭球判断框的三个半轴。
///
/// @param radiusX X 轴半径
/// @param radiusY Y 轴半径
/// @param radiusZ Z 轴半径
public record EllipsoidSize(double radiusX, double radiusY, double radiusZ) implements IHitboxSize {
	/// 椭球半径小于零时的错误说明。
	private static final String NEGATIVE_SIZE_MESSAGE = "ellipsoid radii must not be negative";

	public EllipsoidSize {
		if (radiusX < 0.0 || radiusY < 0.0 || radiusZ < 0.0) {
			throw new IllegalArgumentException(NEGATIVE_SIZE_MESSAGE);
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.ELLIPSOID;
	}
}
