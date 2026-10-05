package org.unitego.lobecorp.mixin.geckolib.client;

import com.geckolib.cache.model.GeoLocator;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffects;

/**
 * 在 locator 位移与旋转均已应用的姿态处捕获 Photon 锚点。
 */
@Mixin(GeoLocator.class)
public abstract class GeoLocatorMixin {
	@Inject(method = "updatePositionListeners", at = @At(value = "INVOKE",
			target = "Lcom/geckolib/util/RenderUtil;providePositionsToListeners(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/geckolib/renderer/base/RenderPassInfo;[Lcom/geckolib/renderer/base/RenderPassInfo$BonePositionListener;)V"))
	private void lobecorp$capturePhotonAnchor(PoseStack poseStack, RenderPassInfo<?> renderPassInfo, CallbackInfo ci) {
		PhotonGeoEffects.captureLocator((GeoLocator) (Object) this, poseStack, renderPassInfo);
	}
}
