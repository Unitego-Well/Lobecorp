package org.unitego.lobecorp.animation.player;

/// 软停止允许玩家视觉淡出；失去目标、追踪或世界时立即清除视觉引用。
public enum LcAnimationTermination {
	NONE,
	FINISHED,
	STOPPED,
	UNAVAILABLE,
	UNTRACKED,
	WORLD_CLEAR;

	public boolean allowsFade() {
		return this == FINISHED || this == STOPPED;
	}
}
