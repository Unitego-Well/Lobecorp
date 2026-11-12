package org.unitego.lobecorp.mixin.minecraft.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.encounter.midnightcleanup.MidnightCleanupSky;

@Mixin(CloudRenderer.class)
public class CloudRendererMixin {
	@Shadow
	private int prevCellX;
	@Shadow
	private int prevCellZ;

	@Unique
	private boolean lobecorp$rimPrepared;

	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;"))
	private void lobecorp$prepareCloudRim(CallbackInfo ci) {
		lobecorp$rimPrepared = MidnightCleanupSky.prepareCloudRim(prevCellX, prevCellZ);
	}

	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderPass;drawIndexed(IIII)V"))
	private void lobecorp$cloudRim(RenderPass pass, int baseVertex, int firstIndex, int indexCount, int instanceCount,
	                               Operation<Void> original, @Local(name = "renderPipeline") RenderPipeline renderPipeline) {
		original.call(pass, baseVertex, firstIndex, indexCount, instanceCount);
		if (!lobecorp$rimPrepared || !MidnightCleanupSky.cloudRimActive()) {
			return;
		}

		try {
			MidnightCleanupSky.drawCloudRim(pass, baseVertex, firstIndex, indexCount, instanceCount);
		} finally {
			pass.setPipeline(renderPipeline);
		}
	}
}
