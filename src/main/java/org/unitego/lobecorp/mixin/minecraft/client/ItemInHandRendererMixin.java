package org.unitego.lobecorp.mixin.minecraft.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionfc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.entity.player.renderer.LcPlayerFirstPersonRenderer;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

	@WrapMethod(method = "renderHandsWithItems")
	private void lobecorp$firstPersonScope(float frameInterp, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
	                                       LocalPlayer player, int lightCoords, Operation<Void> original) {
		var scope = LcPlayerFirstPersonRenderer.open(player, frameInterp);
		try (scope) {
			original.call(frameInterp, poseStack, submitNodeCollector, player, lightCoords);
		}
	}

	@ModifyExpressionValue(method = "renderHandsWithItems", at = {
			@At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer$HandRenderSelection;renderMainHand:Z", opcode = Opcodes.GETFIELD),
			@At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer$HandRenderSelection;renderOffHand:Z", opcode = Opcodes.GETFIELD)
	})
	private boolean lobecorp$showBothArms(boolean selected) {
		return LcPlayerFirstPersonRenderer.active() || selected;
	}

	@WrapOperation(method = "renderHandsWithItems", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
	private void lobecorp$skipViewModelLag(PoseStack poseStack, Quaternionfc by, Operation<Void> original) {
		if (!LcPlayerFirstPersonRenderer.active()) {
			original.call(poseStack, by);
		}
	}

	// 保留方法 HEAD 的渲染通道过滤，以及外层逐手事件和提交刷新。
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/player/AbstractClientPlayer;isScoping()Z"), cancellable = true)
	private void lobecorp$submitAnimatedHand(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
	                                         float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
	                                         SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
		if (LcPlayerFirstPersonRenderer.submitHand(hand, poseStack, submitNodeCollector, lightCoords)) {
			ci.cancel();
		}
	}
}
