package org.unitego.lobecorp.hitbox;

/// 判断框支持的几何形状。
public enum HitboxShapeType {
	/// 球。
	SPHERE,
	/// 圆柱。
	CYLINDER,
	/// 可旋转立方体。
	BOX,
	/// 可旋转扇形圆柱。
	SECTOR_CYLINDER,
	/// 可旋转三维圆锥。
	CONE,
	/// 可旋转椭球。
	ELLIPSOID,
	/// 圆环柱。
	RING_CYLINDER,
	/// 立方环柱。
	BOX_RING_CYLINDER
}
