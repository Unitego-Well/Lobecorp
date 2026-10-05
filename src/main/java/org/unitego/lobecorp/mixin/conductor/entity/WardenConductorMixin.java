package org.unitego.lobecorp.mixin.conductor.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.conductor.data.ConductorData;

@Mixin(Warden.class)
public abstract class WardenConductorMixin {
	@Inject(method = "canTargetEntity", at = @At("HEAD"), cancellable = true)
	private void lobecorp$allowCommandedWardenTarget(Entity entity, CallbackInfoReturnable<Boolean> callback) {
		Warden warden = (Warden) (Object) this;
		if (entity != null && warden.level() instanceof ServerLevel level
				&& ConductorData.get(level.getServer()).allied(warden.getUUID(), entity.getUUID())) {
			callback.setReturnValue(false);
			return;
		}
		if (!(entity instanceof Warden target) || target == warden || !warden.isAlive() || !target.isAlive()
				|| !(warden.level() instanceof ServerLevel level) || warden.level() != target.level()
				|| target.isInvulnerable() || !level.getWorldBorder().isWithinBounds(target.getBoundingBox())) {
			return;
		}
		ConductorData data = ConductorData.get(level.getServer());
		ConductorData.Unit unit = data.unit(warden.getUUID());
		if (unit != null && unit.order() == ConductorData.OrderType.ATTACK
				&& unit.target().equals(target.getUUID().toString())
				&& !data.allied(warden.getUUID(), target.getUUID())) {
			callback.setReturnValue(true);
		}
	}
}
