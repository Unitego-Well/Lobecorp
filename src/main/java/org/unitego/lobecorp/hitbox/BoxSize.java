package org.unitego.lobecorp.hitbox;

/// 立方体判断框的完整边长。
///
/// @param width X 轴边长
/// @param height Y 轴边长
/// @param depth Z 轴边长
public record BoxSize(double width, double height, double depth) implements HitboxSize {
	public BoxSize {
		if (width < 0.0 || height < 0.0 || depth < 0.0) {
			throw new IllegalArgumentException("box size must not be negative");
		}
	}

	@Override
	public HitboxShapeType type() {
		return HitboxShapeType.BOX;
	}
}
