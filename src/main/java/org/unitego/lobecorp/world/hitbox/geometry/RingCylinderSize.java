package org.unitego.lobecorp.world.hitbox.geometry;

/// 圆环柱判断框尺寸。
///
/// @param radius    外半径
/// @param height    总高度
/// @param thickness 径向厚度
public record RingCylinderSize(double radius, double height, double thickness) implements IHitboxSize {
	/// 环形柱尺寸小于零时的错误说明。
	private static final String NEGATIVE_SIZE_MESSAGE = "ring cylinder size must not be negative";

	public RingCylinderSize {
		if (radius < 0.0 || height < 0.0 || thickness < 0.0) {
			throw new IllegalArgumentException(NEGATIVE_SIZE_MESSAGE);
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
