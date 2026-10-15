package org.unitego.lobecorp.animation;

/// 定义动画层对已有骨骼姿态的混合方式。
public enum LcControllerBlendType {
	/// 在已有姿态上叠加当前动画层。
	ADDITIVE,
	/// 使用当前动画层覆盖其有关键帧的通道。
	OVERRIDE,
	/// 仅对遮罩允许的骨骼应用当前动画层。
	MASK
}
