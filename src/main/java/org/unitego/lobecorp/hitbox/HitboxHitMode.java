package org.unitego.lobecorp.hitbox;

/// 单个目标可被判断框重复处理的方式。
public enum HitboxHitMode {
	/// 每个目标仅成功一次。
	ONCE,
	/// 每 tick 都可重新成功。
	EVERY_TICK,
	/// 按固定 tick 间隔重新成功。
	INTERVAL
}
