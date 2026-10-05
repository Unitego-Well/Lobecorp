package org.unitego.lobecorp.mixin.conductor.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.conductor.control.ConductorController;

@Mixin(PathNavigation.class)
public abstract class PathNavigationConductorMixin {
	@Shadow
	@Final
	protected Mob mob;

	@Inject(method = "moveTo(Lnet/minecraft/world/level/pathfinder/Path;D)Z", at = @At("HEAD"), cancellable = true)
	private void lobecorp$filterConductorNavigation(Path newPath, double speedModifier,
	                                                CallbackInfoReturnable<Boolean> callback) {
		if (newPath != null && !ConductorController.allowNavigation(mob, Vec3.atCenterOf(newPath.getTarget()))) {
			callback.setReturnValue(false);
		}
	}
}
