package org.unitego.lobecorp.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.unitego.lobecorp.conductor.control.ConductorMovement;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Vec3 lobecorp$conductorUniformMovement(Vec3 movement, MoverType moverType, Vec3 originalMovement) {
		return ConductorMovement.uniformMovement((Entity) (Object) this, moverType, movement);
	}
}
