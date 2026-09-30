package org.unitego.lobecorp.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.AbstractDragonPhaseInstance;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonHoldingPatternPhase;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.conductor.control.ConductorController;

@Mixin(DragonHoldingPatternPhase.class)
public abstract class DragonHoldingPatternPhaseMixin extends AbstractDragonPhaseInstance {
	public DragonHoldingPatternPhaseMixin(EnderDragon dragon) {
		super(dragon);
	}

	@Inject(method = "doServerTick", at = @At("HEAD"), cancellable = true)
	private void lobecorp$keepConductorDestination(ServerLevel level, CallbackInfo callback) {
		if (ConductorController.commandedDragonDestination(dragon) != null) {
			callback.cancel();
		}
	}

	@Inject(method = "getFlyTargetLocation", at = @At("HEAD"), cancellable = true)
	private void lobecorp$conductorFlyTarget(CallbackInfoReturnable<Vec3> callback) {
		Vec3 destination = ConductorController.commandedDragonDestination(dragon);
		if (destination != null) {
			callback.setReturnValue(destination);
		}
	}
}
