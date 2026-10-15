package org.unitego.lobecorp.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.world.effect.StunMobEffect;

@Mixin(Mob.class)
public abstract class MobMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void lobecorp$preventStunnedAttack(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (StunMobEffect.preventsActions((Mob) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
