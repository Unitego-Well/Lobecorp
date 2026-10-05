package org.unitego.lobecorp.mixin.conductor.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.network.tc.ConductorSnapshotPayload;

@Mixin(SonicBoom.class)
public abstract class SonicBoomConductorMixin {
	@Inject(method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/warden/Warden;)Z",
			at = @At("HEAD"), cancellable = true)
	private void lobecorp$checkConductorAttackState(ServerLevel level, Warden body,
	                                                CallbackInfoReturnable<Boolean> callback) {
		LivingEntity target = body.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		if (target == null || !ConductorController.allowWardenSonicBoom(body, target)) {
			callback.setReturnValue(false);
		}
	}

	@Inject(method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/warden/Warden;J)V",
			at = @At("TAIL"))
	private void lobecorp$consumeManualRequest(ServerLevel level, Warden body, long timestamp, CallbackInfo callback) {
		ConductorController.consumeManualWardenSonicBoomRequest(body);
		ConductorSnapshotPayload.sendAll(level.getServer(), ConductorData.get(level.getServer()));
	}

	@Inject(method = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/warden/Warden;J)V",
			at = @At("TAIL"))
	private void lobecorp$syncCooldown(ServerLevel level, Warden body, long timestamp, CallbackInfo callback) {
		ConductorController.completeManualWardenSonicBoom(body);
		ConductorSnapshotPayload.sendAll(level.getServer(), ConductorData.get(level.getServer()));
	}
}
