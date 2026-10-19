package org.unitego.lobecorp.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.entity.damage.LcDamageUtil;

@Mixin(Player.class)
public abstract class PlayerMixin {
	@WrapMethod(method = "actuallyHurt")
	private void lobecorp$applyDamageChannels(ServerLevel level, DamageSource source, float dmg, Operation<Void> original) {
		LcDamageUtil.actuallyHurt((LivingEntity) (Object) this, level, source, dmg, original);
	}

	@WrapOperation(
			method = "actuallyHurt",
			at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onLivingDamagePre(Lnet/minecraft/world/entity/LivingEntity;Lnet/neoforged/neoforge/common/damagesource/DamageContainer;)F")
	)
	private float lobecorp$combineHealth(LivingEntity entity, DamageContainer container, Operation<Float> original) {
		return LcDamageUtil.afterDamagePre(entity, container, original.call(entity, container));
	}
}
