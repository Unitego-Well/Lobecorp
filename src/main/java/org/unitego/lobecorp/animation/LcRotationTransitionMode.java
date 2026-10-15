package org.unitego.lobecorp.animation;

/// 定义旋转混合采用直接插值还是最短路径。
public enum LcRotationTransitionMode {
	/// 按各旋转通道的原值直接插值。
	NORMAL,
	/// 使用四元数选择最短旋转路径。
	SHORTEST_PATH
}
