package org.unitego.lobecorp.mixin.minecraft.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.horde.HordeIrisShadows;
import org.unitego.lobecorp.client.particle.HordeGroundSmokeParticle;

@Mixin(QuadParticleRenderState.class)
public class GroundSmokePipelineMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"))
	private void lobecorp$groundSmokePipeline(RenderPass pass, RenderPipeline pipeline, Operation<Void> original) {
		if (HordeGroundSmokeParticle.usesPipeline(pipeline)) {
			HordeIrisShadows.setNativePipeline(pass, pipeline);
			return;
		}

		original.call(pass, pipeline);
	}
}
