package org.unitego.lobecorp.world.hitbox.geometry;

/// 立方环柱判断框尺寸。
///
/// @param width     外宽
/// @param height    总高度
/// @param depth     外深
/// @param thickness 四周边框厚度
public record BoxRingCylinderSize(double width, double height, double depth, double thickness)
		implements IHitboxSize {
	/// 矩形环柱尺寸小于零时的错误说明。
	private static final String NEGATIVE_SIZE_MESSAGE = "box ring cylinder size must not be negative";

	public BoxRingCylinderSize {
		if (width < 0.0 || height < 0.0 || depth < 0.0 || thickness < 0.0) {
			throw new IllegalArgumentException(NEGATIVE_SIZE_MESSAGE);
		}
	}

	public double innerWidth() {
		return Math.max(0.0, width - thickness * 2.0);
	}

	public double innerDepth() {
		return Math.max(0.0, depth - thickness * 2.0);
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.BOX_RING_CYLINDER;
	}
}
