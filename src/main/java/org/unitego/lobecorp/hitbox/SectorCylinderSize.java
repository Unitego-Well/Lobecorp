package org.unitego.lobecorp.hitbox;

/// 以局部正 Z 轴为中心的扇形圆柱判断框尺寸。
///
/// @param radius 半径
/// @param height 总高度
/// @param angleDegrees 完整扇形角度，范围为 0 至 180 度
public record SectorCylinderSize(double radius, double height, double angleDegrees) implements HitboxSize {
	/// 凸扇形允许的最大角度。
	private static final double MAXIMUM_ANGLE_DEGREES = 180.0;

	public SectorCylinderSize {
		if (radius < 0.0 || height < 0.0) {
			throw new IllegalArgumentException("sector cylinder size must not be negative");
		}
		if (angleDegrees < 0.0 || angleDegrees > MAXIMUM_ANGLE_DEGREES) {
			throw new IllegalArgumentException("sector angle must be between 0 and 180 degrees");
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.SECTOR_CYLINDER;
	}
}
