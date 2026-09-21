package org.unitego.lobecorp.mixin.client;

import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.unitego.lobecorp.animation.LcAnimationRuntime;

@Mixin(value = RenderPassInfo.class, remap = false)
public abstract class RenderPassInfoMixin {
	@Redirect(
			method = "create",
			at = @At(
					value = "INVOKE",
					target = "Lcom/geckolib/renderer/base/RenderPassInfo;addBoneUpdater(Lcom/geckolib/renderer/base/RenderPassInfo$BoneUpdater;)V",
					ordinal = 1
			)
	)
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void addLcAnimationMixer(RenderPassInfo renderPassInfo,
			RenderPassInfo.BoneUpdater<GeoRenderState> rendererAdjustment) {
		renderPassInfo.addBoneUpdater(LcAnimationRuntime::apply);
		renderPassInfo.addBoneUpdater(rendererAdjustment);
	}
}
