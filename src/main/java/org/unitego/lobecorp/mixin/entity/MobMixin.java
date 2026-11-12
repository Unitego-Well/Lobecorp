package org.unitego.lobecorp.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;
import org.unitego.lobecorp.world.effect.StunMobEffect;

@Mixin(Mob.class)
public abstract class MobMixin {
	@WrapOperation(method = "doHurtTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
	private boolean lobecorp$captureShieldBlock(Entity target, ServerLevel level, DamageSource source, float damage,
	                                            Operation<Boolean> original, @Share("shieldBlocked") LocalBooleanRef blocked) {
		return LcDamageUtil.captureShieldBlock(target, () -> original.call(target, level, source, damage), blocked);
	}

	@WrapOperation(method = "doHurtTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;causeExtraKnockback(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/phys/Vec3;)V"))
	private void lobecorp$shieldExtraKnockback(Mob mob, Entity target, float strength, Vec3 movement,
	                                           Operation<Void> original, @Share("shieldBlocked") LocalBooleanRef blocked) {
		original.call(mob, target, blocked.get() ? 0.0F : strength, movement);
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void lobecorp$preventStunnedAttack(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (StunMobEffect.preventsActions((Mob) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
