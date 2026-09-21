package org.unitego.lobecorp.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/// 原版死亡流程直接清空效果表，因此需要在清空前主动移除效果提供的属性修饰符。
	@Inject(
			method = "triggerOnDeathMobEffects",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V")
	)
	private void lobecorp$removeDeathEffectAttributeModifiers(
			ServerLevel level, Entity.RemovalReason reason, CallbackInfo ci
	) {
		LivingEntity entity = (LivingEntity) (Object) this;
		for (MobEffectInstance effect : entity.getActiveEffects()) {
			effect.getEffect().value().removeAttributeModifiers(entity.getAttributes());
		}
	}
}
