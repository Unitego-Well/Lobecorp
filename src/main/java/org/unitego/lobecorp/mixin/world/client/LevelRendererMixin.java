package org.unitego.lobecorp.mixin.world.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.unitego.lobecorp.client.particle.EntityForegroundParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.horde.HordeGroundCache;
import org.unitego.lobecorp.registry.client.entity.LcDebugEntries;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
	@Shadow @Final private FeatureRenderDispatcher featureRenderDispatcher;
	@Shadow @Final private RenderBuffers renderBuffers;
	@Shadow @Final private SubmitNodeStorage submitNodeStorage;

	@Shadow
	protected abstract void submitBlockEntities(PoseStack poseStack, LevelRenderState state, SubmitNodeStorage storage);

	@WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"))
	private void lobecorp$captureBlockEntityDepth(LevelRenderer renderer, PoseStack poseStack, LevelRenderState state,
	                                             SubmitNodeCollector collector, Operation<Void> original) {
		if (EntityForegroundParticles.needsBlockEntityDepth()) {
			this.submitBlockEntities(poseStack, state, this.submitNodeStorage);
			EntityForegroundParticles.captureBlockEntityDepth(this.featureRenderDispatcher, this.renderBuffers);
		}

		original.call(renderer, poseStack, state, collector);
	}

	@WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;submitBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeStorage;)V"))
	private void lobecorp$submitBlockEntitiesOnce(LevelRenderer renderer, PoseStack poseStack, LevelRenderState state,
	                                             SubmitNodeStorage storage, Operation<Void> original) {
		if (!EntityForegroundParticles.needsBlockEntityDepth()) {
			original.call(renderer, poseStack, state, storage);
		}
	}
	@Inject(method = "blockChanged", at = @At("HEAD"))
	private void lobecorp$invalidateGround(BlockGetter level, BlockPos pos, BlockState old,
	                                       BlockState current, int updateFlags, CallbackInfo callback) {
		if (level == Minecraft.getInstance().level) {
			HordeGroundCache.invalidate(pos);
		}
	}

	@ModifyExpressionValue(
			method = {"extractBlockOutline", "renderHitOutline"},
			at = @At(value = "FIELD", target = "Lnet/minecraft/SharedConstants;DEBUG_SHAPES:Z", opcode = Opcodes.GETSTATIC)
	)
	private boolean lobecorp$showDebugShapes(boolean configured) {
		return configured && LcDebugEntries.isDebugRenderingEnabled(LcDebugEntries.DEBUG_SHAPES);
	}
}
