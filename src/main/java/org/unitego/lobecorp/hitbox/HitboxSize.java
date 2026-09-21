package org.unitego.lobecorp.hitbox;

/// 判断框形状尺寸的类型化公共接口。
public interface HitboxSize {
	/// @return 与当前尺寸匹配的固定形状类型
	HitboxShapeType type();
}
