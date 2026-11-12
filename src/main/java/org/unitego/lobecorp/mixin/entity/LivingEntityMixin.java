package org.unitego.lobecorp.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.api.ILivingEntityExpand;
import org.unitego.lobecorp.registry.world.effect.LcMobEffects;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;
import org.unitego.lobecorp.world.effect.StunMobEffect;
import org.unitego.lobecorp.world.entity.damage.LcDamageContext;

import java.util.Stack;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements ILivingEntityExpand {
	@Shadow
	protected Stack<DamageContainer> damageContainers;
	@Unique
	private boolean lobecorp$shieldBlocked;
	@Unique
	private boolean lobecorp$capturingShieldBlock;
	@Unique
	@Nullable
	private LcDamageContext lobecorp$damageContext;

	@Override
	public boolean lobecorp$isShieldBlocked() {
		return lobecorp$shieldBlocked;
	}

	@Override
	public void lobecorp$setShieldBlocked(boolean blocked) {
		lobecorp$shieldBlocked = blocked;
	}

	@Override
	public boolean lobecorp$isCapturingShieldBlock() {
		return lobecorp$capturingShieldBlock;
	}

	@Override
	public void lobecorp$setCapturingShieldBlock(boolean capture) {
		lobecorp$capturingShieldBlock = capture;
	}

	@Override
	@Nullable
	public LcDamageContext lobecorp$getDamageContext() {
		return lobecorp$damageContext;
	}

	@Override
	public void lobecorp$setDamageContext(@Nullable LcDamageContext context) {
		lobecorp$damageContext = context;
	}

	@Override
	@Nullable
	public DamageContainer lobecorp$getDamageContainer() {
		return damageContainers.isEmpty() ? null : damageContainers.peek();
	}

	@WrapMethod(method = "hurtServer")
	private boolean lobecorp$withDamageContext(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
		LcDamageContext previous = lobecorp$damageContext;
		boolean previousBlocked = lobecorp$shieldBlocked;
		boolean captureThis = lobecorp$capturingShieldBlock;
		lobecorp$capturingShieldBlock = false;
		lobecorp$shieldBlocked = false;
		lobecorp$damageContext = LcDamageContext.create(source);
		try {
			return original.call(level, source, damage);
		} finally {
			lobecorp$damageContext = previous;
			lobecorp$capturingShieldBlock = captureThis;
			if (!captureThis) {
				lobecorp$shieldBlocked = previousBlocked;
			}
		}
	}

	@WrapOperation(method = "hurtServer", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;hurtTime:I", opcode = 181))
	private void lobecorp$shieldHurtTime(LivingEntity entity, int value, Operation<Void> original) {
		if (!lobecorp$shieldBlocked) {
			original.call(entity, value);
		}
	}

	@WrapOperation(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;broadcastDamageEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V"))
	private void lobecorp$shieldBroadcast(ServerLevel level, Entity entity, DamageSource source, Operation<Void> original) {
		if (!lobecorp$shieldBlocked) {
			original.call(level, entity, source);
		}
	}

	@WrapOperation(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;indicateDamage(DD)V"))
	private void lobecorp$shieldIndication(LivingEntity entity, double x, double z, Operation<Void> original) {
		if (!lobecorp$shieldBlocked) {
			original.call(entity, x, z);
		}
	}

	@WrapOperation(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;markHurt()V"))
	private void lobecorp$shieldImpact(LivingEntity entity, Operation<Void> original) {
		if (!lobecorp$shieldBlocked) {
			original.call(entity);
		}
	}

	@WrapOperation(
			method = "hurtServer",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;applyItemBlocking(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)F")
	)
	private float lobecorp$blockHealthChannels(LivingEntity entity, ServerLevel level, DamageSource source,
	                                           float damage, Operation<Float> original) {
		LcDamageContext context = lobecorp$damageContext;
		if (context == null || !context.hasIndependentResources()) {
			return original.call(entity, level, source, damage);
		}

		if (context.hasHealthDamage()) {
			context.setShieldReduction(original.call(entity, level, source, damage));
		}

		// 精神与灵魂保留本次命中的无敌帧和受击反馈，盾牌扣除额只应用于生命通道。
		return 0.0F;
	}

	@WrapMethod(method = "actuallyHurt")
	private void lobecorp$applyDamageChannels(ServerLevel level, DamageSource source, float dmg, Operation<Void> original) {
		LcDamageUtil.actuallyHurt((LivingEntity) (Object) this, level, source, dmg, original);
	}

	@WrapMethod(method = "getDamageAfterArmorAbsorb")
	private float lobecorp$applyPhysicsArmor(DamageSource damageSource, float damage, Operation<Float> original) {
		LcDamageContext context = lobecorp$damageContext;
		if (context == null || !context.isPrepared()) {
			return original.call(damageSource, damage);
		}

		float physics = context.getPhysicsDamage();
		float remainingPhysics = physics > 0.0F ? original.call(damageSource, physics) : 0.0F;
		context.setPhysicsAfterArmor(remainingPhysics);
		return remainingPhysics + context.getErosionDamage();
	}

	@WrapOperation(
			method = "actuallyHurt",
			at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDamagePre(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;)F")
	)
	private float lobecorp$combineHealth(LivingEntity entity, DamageContainer container, Operation<Float> original) {
		return LcDamageUtil.afterDamagePre(entity, container, original.call(entity, container));
	}

	@Inject(method = "stabAttack", at = @At("HEAD"), cancellable = true)
	private void lobecorp$preventStunnedStabAttack(EquipmentSlot weaponSlot, Entity target, float baseDamage,
	                                               boolean dealsDamage, boolean dealsKnockback, boolean dismounts, CallbackInfoReturnable<Boolean> cir) {
		if (StunMobEffect.preventsActions((LivingEntity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	@WrapOperation(method = "stabAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean lobecorp$captureStabShieldBlock(Entity target, ServerLevel level, DamageSource source, float damage,
	                                                Operation<Boolean> original, @Share("shieldBlocked") LocalBooleanRef blocked) {
		return LcDamageUtil.captureShieldBlock(target, () -> original.call(target, level, source, damage), blocked);
	}

	@WrapOperation(method = "stabAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;causeExtraKnockback(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/phys/Vec3;)V"))
	private void lobecorp$shieldStabKnockback(LivingEntity entity, Entity target, float strength, Vec3 movement,
	                                          Operation<Void> original, @Share("shieldBlocked") LocalBooleanRef blocked) {
		original.call(entity, target, blocked.get() ? 0.0F : strength, movement);
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
