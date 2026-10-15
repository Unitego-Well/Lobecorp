package org.unitego.lobecorp.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.particle.damage.DamageParticleHitContext;

@Mixin(Player.class)
public abstract class PlayerMixin {
	@WrapOperation(
			method = {
					"attack(Lnet/minecraft/world/entity/Entity;)V",
					"stabAttack(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/Entity;FZZZ)Z"
			},
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
	)
	private boolean lobecorp$trackDirectMeleeDamage(Entity target, DamageSource source, float damage,
	                                                Operation<Boolean> original) {
		var player = (Player) (Object) this;
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return original.call(target, source, damage);
		}

		try (var _ = DamageParticleHitContext.melee(serverPlayer, target, source)) {
			return original.call(target, source, damage);
		}
	}

	@WrapOperation(
			method = "doSweepAttack(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/damagesource/DamageSource;FLnet/minecraft/world/phys/AABB;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z")
	)
	private boolean lobecorp$trackSweepDamage(LivingEntity target, ServerLevel level, DamageSource source,
	                                          float damage, Operation<Boolean> original) {
		var player = (Player) (Object) this;
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return original.call(target, level, source, damage);
		}

		try (var _ = DamageParticleHitContext.melee(serverPlayer, target, source)) {
			return original.call(target, level, source, damage);
		}
	}
}
