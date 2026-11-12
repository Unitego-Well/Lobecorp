package org.unitego.lobecorp.mixin.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.animation.player.LcPlayerAnimationRuntime;
import org.unitego.lobecorp.registry.animation.CannedEnkephalinAnimationDefinitions;
import org.unitego.lobecorp.registry.world.item.LcItems;

@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
	// 同时用于第三人称和项目的第一人称持物层，复用已冻结的玩家采样。
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void lobecorp$animateCan(ArmedEntityRenderState state, ItemStackRenderState item, ItemStack stack,
	                                HumanoidArm arm, PoseStack poses, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (!(state instanceof AvatarRenderState avatar) || !stack.is(LcItems.CANNED_ENKEPHALIN.get())) {
			return;
		}

		var frame = LcPlayerAnimationRuntime.frame(avatar);
		if (frame == null) {
			return;
		}

		for (var layer : frame.layers()) {
			var definition = layer.frame().snapshot().definitionId();
			boolean right = definition.equals(CannedEnkephalinAnimationDefinitions.RIGHT);
			boolean left = definition.equals(CannedEnkephalinAnimationDefinitions.LEFT);
			if (right && arm == HumanoidArm.RIGHT || left && arm == HumanoidArm.LEFT) {
				var transform = layer.pose().get(right ? "right_item" : "left_item");
				poses.translate(transform.position().x, transform.position().y, transform.position().z);
				poses.mulPose(transform.rotation());
				poses.scale((float) transform.scale().x, (float) transform.scale().y, (float) transform.scale().z);
				return;
			}
		}
	}
}
