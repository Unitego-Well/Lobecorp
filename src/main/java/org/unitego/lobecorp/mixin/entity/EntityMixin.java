package org.unitego.lobecorp.mixin.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.unitego.lobecorp.conductor.control.ConductorMovement;
import org.unitego.lobecorp.util.entity.EntityGrabUtil;

@Mixin(Entity.class)
public abstract class EntityMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Vec3 lobecorp$conductorUniformMovement(Vec3 movement, MoverType moverType, Vec3 originalMovement) {
		Entity entity = (Entity) (Object) this;
		return EntityGrabUtil.restrictMovement(entity, ConductorMovement.uniformMovement(entity, moverType, movement));
	}
}
