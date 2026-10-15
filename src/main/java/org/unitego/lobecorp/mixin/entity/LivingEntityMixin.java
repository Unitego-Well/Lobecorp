package org.unitego.lobecorp.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.registry.effect.LcMobEffects;
import org.unitego.lobecorp.world.effect.StunMobEffect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "stabAttack", at = @At("HEAD"), cancellable = true)
	private void lobecorp$preventStunnedStabAttack(EquipmentSlot weaponSlot, Entity target, float baseDamage,
	                                               boolean dealsDamage, boolean dealsKnockback, boolean dismounts, CallbackInfoReturnable<Boolean> cir) {
		if (StunMobEffect.preventsActions((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

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

	@ModifyExpressionValue(
			method = "aiStep",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isEffectiveAi()Z", ordinal = 1)
	)
	private boolean lobecorp$allowStunnedMobTravel(boolean effectiveAi) {
		LivingEntity entity = (LivingEntity) (Object) this;
		return effectiveAi || entity instanceof Mob mob && LcMobEffects.STUN.get().isAiDisabledMob(mob);
	}
}
