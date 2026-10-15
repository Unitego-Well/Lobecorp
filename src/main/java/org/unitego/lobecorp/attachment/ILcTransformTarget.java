package org.unitego.lobecorp.attachment;

import net.minecraft.world.level.Level;

/// 只接收显式变换；对象寿命与伤害等规则仍归原系统管理。
public interface ILcTransformTarget {
	Level level();

	LcTransform transform();

	void applyTransform(LcTransform transform);

	default Object identity() {
		return this;
	}

	default boolean isAvailable() {
		return true;
	}
}
