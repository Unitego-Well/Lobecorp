package org.unitego.lobecorp.attachment;

import net.minecraft.world.level.Level;

/// 来源只提供空间变换，不管理目标或动画的生命周期。
public interface ILcAttachmentAnchor {
	Level level();

	LcTransform resolve();

	LcAnchorReference reference();

	default LcTransform offset() {
		return LcTransform.identity();
	}

	default boolean isAvailable() {
		return true;
	}
}

