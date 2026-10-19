package org.unitego.lobecorp.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.unitego.lobecorp.conductor.control.ConductorMovement;
import org.unitego.lobecorp.util.entity.EntityGrabUtil;
import org.unitego.lobecorp.util.entity.combat.CombatUtil;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@ModifyReturnValue(method = "isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z", at = @At("RETURN"))
	private boolean lobecorp$combatAlliance(boolean original, Entity other) {
		Entity entity = (Entity) (Object) this;
		if (other == null || entity == other) {
			return original;
		}
		if (!CombatUtil.participates(entity) && !CombatUtil.participates(other)) {
			return original;
		}
		if (CombatUtil.isIndiscriminate(entity) || CombatUtil.isIndiscriminate(other)) {
			return false;
		}

		return original || CombatUtil.isAllied(entity, other);
	}

	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Vec3 lobecorp$conductorUniformMovement(Vec3 movement, MoverType moverType, Vec3 originalMovement) {
		Entity entity = (Entity) (Object) this;
		return EntityGrabUtil.restrictMovement(entity, ConductorMovement.uniformMovement(entity, moverType, movement));
	}
}
