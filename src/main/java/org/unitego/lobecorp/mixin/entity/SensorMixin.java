package org.unitego.lobecorp.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.conductor.control.ConductorController;

@Mixin(Sensor.class)
public abstract class SensorMixin {
	@Inject(method = "isEntityAttackable", at = @At("HEAD"), cancellable = true)
	private static void lobecorp$filterConductorSensorTarget(ServerLevel level, LivingEntity body,
	                                                         LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
		if (body instanceof Mob mob && !ConductorController.allowsTargetChange(mob, target)) {
			callback.setReturnValue(false);
		}
	}

	@Inject(method = "isEntityAttackableIgnoringLineOfSight", at = @At("HEAD"), cancellable = true)
	private static void lobecorp$filterConductorSensorTargetWithoutSight(ServerLevel level, LivingEntity body,
	                                                                     LivingEntity target, CallbackInfoReturnable<Boolean> callback) {
		if (body instanceof Mob mob && !ConductorController.allowsTargetChange(mob, target)) {
			callback.setReturnValue(false);
		}
	}
}
