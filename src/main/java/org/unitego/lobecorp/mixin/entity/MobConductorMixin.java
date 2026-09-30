package org.unitego.lobecorp.mixin.entity;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;

@Mixin(Mob.class)
public abstract class MobConductorMixin {
	@Inject(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V"))
	private void lobecorp$conductorAi(CallbackInfo callback) {
		Mob mob = (Mob) (Object) this;
		ConductorController.tick(mob);
		if (mob instanceof Sweeper sweeper) sweeper.updateMovementSpeed();
	}

	@Inject(method = "canReplaceCurrentItem", at = @At("HEAD"), cancellable = true)
	private void lobecorp$allowConductorWitherSkeletonBow(ItemStack incoming, ItemStack current,
	                                                      EquipmentSlot slot, CallbackInfoReturnable<Boolean> callback) {
		Mob mob = (Mob) (Object) this;
		if (mob instanceof WitherSkeleton && slot == EquipmentSlot.MAINHAND
				&& incoming.getItem() instanceof BowItem && ConductorController.isControlled(mob)) {
			callback.setReturnValue(true);
		}
	}
}
