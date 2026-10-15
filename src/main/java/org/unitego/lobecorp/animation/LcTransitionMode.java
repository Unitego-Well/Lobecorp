package org.unitego.lobecorp.animation;

/// 定义动画或控制器权重过渡采用重叠还是顺序方式。
public enum LcTransitionMode {
	/// 旧动画退出与新动画进入同时进行。
	OVERLAP,
	/// 先完成旧动画退出，再开始新动画进入。
	SEQUENTIAL
}
