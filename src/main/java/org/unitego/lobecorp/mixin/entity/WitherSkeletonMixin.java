package org.unitego.lobecorp.mixin.entity;

import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.conductor.control.ConductorController;

@Mixin(WitherSkeleton.class)
public abstract class WitherSkeletonMixin {
	@Inject(method = "canHoldItem", at = @At("HEAD"), cancellable = true)
	private void lobecorp$allowConductorBow(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
		WitherSkeleton skeleton = (WitherSkeleton) (Object) this;
		if (stack.getItem() instanceof BowItem && ConductorController.isControlled(skeleton)) {
			callback.setReturnValue(true);
		}
	}
}
