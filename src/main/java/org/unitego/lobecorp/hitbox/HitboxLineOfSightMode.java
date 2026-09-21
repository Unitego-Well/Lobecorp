package org.unitego.lobecorp.hitbox;

/// 判断框命中前使用的方块遮挡检查方式。
public enum HitboxLineOfSightMode {
	/// 从判断框中心向目标七个采样点检测。
	CENTER_TO_ENTITY,
	/// 从来源实体七个采样点向目标七个采样点检测。
	ENTITY_TO_ENTITY,
	/// 不检测方块遮挡。
	UNRESTRICTED
}
