package org.unitego.lobecorp.world.hitbox.geometry;

/// 三维圆锥尺寸。
///
/// @param length       从尖端沿局部正 Z 轴延伸的长度
/// @param angleDegrees 圆锥完整顶角
public record ConeSize(double length, double angleDegrees) implements IHitboxSize {
	/// 圆锥尺寸或顶角不合法时的错误说明。
	private static final String INVALID_DIMENSIONS_MESSAGE = "cone dimensions must be positive and angle must be below 180 degrees";

	public ConeSize {
		if (length <= 0.0 || angleDegrees <= 0.0 || angleDegrees >= 180.0) {
			throw new IllegalArgumentException(INVALID_DIMENSIONS_MESSAGE);
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.CONE;
	}
}
