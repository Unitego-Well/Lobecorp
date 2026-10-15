package org.unitego.lobecorp.world.hitbox.geometry;

/// 圆柱判断框尺寸。
///
/// @param radius 半径
/// @param height 总高度
public record CylinderSize(double radius, double height) implements IHitboxSize {
	/// 圆柱尺寸小于零时的错误说明。
	private static final String NEGATIVE_SIZE_MESSAGE = "cylinder size must not be negative";

	public CylinderSize {
		if (radius < 0.0 || height < 0.0) {
			throw new IllegalArgumentException(NEGATIVE_SIZE_MESSAGE);
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.CYLINDER;
	}
}
