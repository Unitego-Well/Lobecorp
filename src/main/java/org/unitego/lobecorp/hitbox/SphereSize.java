package org.unitego.lobecorp.hitbox;

/// 球形判断框尺寸。
///
/// @param radius 半径
public record SphereSize(double radius) implements HitboxSize {
	public SphereSize {
		if (radius < 0.0) {
			throw new IllegalArgumentException("radius must not be negative");
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.SPHERE;
	}
}
