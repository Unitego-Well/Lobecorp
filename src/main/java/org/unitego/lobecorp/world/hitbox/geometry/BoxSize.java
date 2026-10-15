package org.unitego.lobecorp.world.hitbox.geometry;

/// 立方体判断框的完整边长。
///
/// @param width  X 轴边长
/// @param height Y 轴边长
/// @param depth  Z 轴边长
public record BoxSize(double width, double height, double depth) implements IHitboxSize {
	/// 长方体尺寸小于零时的错误说明。
	private static final String NEGATIVE_SIZE_MESSAGE = "box size must not be negative";

	public BoxSize {
		if (width < 0.0 || height < 0.0 || depth < 0.0) {
			throw new IllegalArgumentException(NEGATIVE_SIZE_MESSAGE);
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.BOX;
	}
}
