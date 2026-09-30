package org.unitego.lobecorp.mixin.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.breeze.Breeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.conductor.control.ConductorController;

@Mixin(Breeze.class)
public abstract class BreezeMixin {
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void lobecorp$allowCommandedTarget(LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
		if (ConductorController.isCommandedTarget((Breeze) (Object) this, target)) {
			callback.setReturnValue(true);
		}
	}
}
