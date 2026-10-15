package org.unitego.lobecorp.world.hitbox.geometry;

/// 以局部正 Z 轴为中心的扇形圆柱判断框尺寸。
///
/// @param radius       半径
/// @param height       总高度
/// @param angleDegrees 完整扇形角度，范围为 0 至 180 度
public record SectorCylinderSize(double radius, double height, double angleDegrees) implements IHitboxSize {
	/// 扇形柱尺寸小于零时的错误说明。
	private static final String NEGATIVE_SIZE_MESSAGE = "sector cylinder size must not be negative";
	/// 扇形柱角度超出支持范围时的错误说明。
	private static final String INVALID_ANGLE_MESSAGE = "sector angle must be between 0 and 180 degrees";

	/// 凸扇形允许的最大角度。
	private static final double MAXIMUM_ANGLE_DEGREES = 180.0;

	public SectorCylinderSize {
		if (radius < 0.0 || height < 0.0) {
			throw new IllegalArgumentException(NEGATIVE_SIZE_MESSAGE);
		}

		if (angleDegrees < 0.0 || angleDegrees > MAXIMUM_ANGLE_DEGREES) {
			throw new IllegalArgumentException(INVALID_ANGLE_MESSAGE);
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.SECTOR_CYLINDER;
	}
}
