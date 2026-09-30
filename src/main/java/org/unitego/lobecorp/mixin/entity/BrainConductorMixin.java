package org.unitego.lobecorp.mixin.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.conductor.control.ConductorController;

@Mixin(Brain.class)
public abstract class BrainConductorMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void lobecorp$enterConductorBrainTick(ServerLevel level, LivingEntity body, CallbackInfo callback) {
		ConductorController.enterBrainTick(body);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void lobecorp$exitConductorBrainTick(ServerLevel level, LivingEntity body, CallbackInfo callback) {
		ConductorController.exitBrainTick(body);
	}

	@Inject(method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V",
			at = @At("HEAD"), cancellable = true)
	private void lobecorp$filterConductorAttackTarget(MemoryModuleType<?> type, Object value, CallbackInfo callback) {
		if ((type == MemoryModuleType.ATTACK_TARGET || type == MemoryModuleType.ROAR_TARGET
				|| type == MemoryModuleType.NEAREST_ATTACKABLE) && value instanceof LivingEntity target
				&& ConductorController.shouldBlockBrainTarget(target)) {
			callback.cancel();
		}
	}

	@Inject(method = "setMemoryInternal(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;J)V",
			at = @At("HEAD"), cancellable = true)
	private void lobecorp$filterConductorExpiringAttackTarget(MemoryModuleType<?> type, Object value,
	                                                          long tileToLive, CallbackInfo callback) {
		if ((type == MemoryModuleType.ATTACK_TARGET || type == MemoryModuleType.ROAR_TARGET
				|| type == MemoryModuleType.NEAREST_ATTACKABLE) && value instanceof LivingEntity target
				&& ConductorController.shouldBlockBrainTarget(target)) {
			callback.cancel();
		}
	}
}
