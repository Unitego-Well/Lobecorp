package org.unitego.lobecorp.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.particle.damage.DamageParticleHitContext;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {
	@WrapMethod(method = "hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;")
	@SuppressWarnings("resource")
	private ProjectileDeflection lobecorp$trackHitPosition(HitResult hitResult, Operation<ProjectileDeflection> original) {
		var projectile = (Projectile) (Object) this;
		if (!(projectile.level() instanceof ServerLevel) || !(hitResult instanceof EntityHitResult entityHit)) {
			return original.call(hitResult);
		}

		try (var _ = DamageParticleHitContext.projectile(projectile, entityHit)) {
			return original.call(hitResult);
		}
	}
}
