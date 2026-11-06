package org.unitego.lobecorp.mixin.photon.creation.client;

import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.fx.IEffectExecutor;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// 只供显式租约的完整 reset 使用；默认值与当前依赖字段初始化一致。
@Mixin(value = FXObject.class, remap = false)
public interface FXObjectLeaseAccessor {
	/// 直接清引用，不经过 setEffect 的 seed 注入。
	@Accessor("effectExecutor")
	void lobecorp$setLeaseExecutor(@Nullable IEffectExecutor value);

	@Accessor("lastTick")
	void lobecorp$setLeaseLastTick(long value);

	@Accessor("lastTickTime")
	void lobecorp$setLeaseLastTickTime(float value);

	@Accessor("deltaTime")
	void lobecorp$setLeaseDeltaTime(float value);
}
