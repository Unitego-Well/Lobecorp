package org.unitego.lobecorp.mixin.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.conductor.control.ConductorController;

@Mixin(TargetGoal.class)
public abstract class TargetGoalMixin {
	@Shadow
	protected Mob mob;

	@Shadow
	protected LivingEntity targetMob;

	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void lobecorp$filterConductorGoalTarget(LivingEntity target, TargetingConditions conditions,
	                                                CallbackInfoReturnable<Boolean> callback) {
		if (target != null && !ConductorController.allowsTargetChange(mob, target)) {
			callback.setReturnValue(false);
		}
	}

	@Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
	private void lobecorp$filterConductorContinuingGoal(CallbackInfoReturnable<Boolean> callback) {
		LivingEntity target = mob.getTarget() == null ? targetMob : mob.getTarget();
		if (target != null && !ConductorController.allowsTargetChange(mob, target)) {
			callback.setReturnValue(false);
		}
	}
}
