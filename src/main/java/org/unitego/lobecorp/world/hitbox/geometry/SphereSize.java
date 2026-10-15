package org.unitego.lobecorp.world.hitbox.geometry;

/// 球形判断框尺寸。
///
/// @param radius 半径
public record SphereSize(double radius) implements IHitboxSize {
	/// 球体半径小于零时的错误说明。
	private static final String NEGATIVE_RADIUS_MESSAGE = "radius must not be negative";

	public SphereSize {
		if (radius < 0.0) {
			throw new IllegalArgumentException(NEGATIVE_RADIUS_MESSAGE);
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.SPHERE;
	}
}
