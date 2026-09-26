package org.unitego.lobecorp.mixin.client;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.animation.LcAnimationControllerIntegration;
import org.unitego.lobecorp.animation.LcCustomAnimatable;

@Mixin(RenderPassInfo.class)
public class RenderPassInfoAnimationMixin {
	@WrapOperation(
			method = "create",
			at = @At(
					value = "INVOKE",
					target = "Lcom/geckolib/renderer/base/RenderPassInfo;addBoneUpdater(Lcom/geckolib/renderer/base/RenderPassInfo$BoneUpdater;)V",
					ordinal = 0
			)
	)
	private static <R extends GeoRenderState> void lobecorp$selectAnimationControllerUpdater(
			RenderPassInfo<R> renderPassInfo,
			RenderPassInfo.BoneUpdater<R> updater,
			Operation<Void> original
	) {
		Class<? extends GeoAnimatable> animatableClass =
				renderPassInfo.renderState().getGeckolibData(DataTickets.ANIMATABLE_CLASS);
		if (animatableClass != null && LcCustomAnimatable.class.isAssignableFrom(animatableClass)) {
			renderPassInfo.addBoneUpdater(LcAnimationControllerIntegration::apply);
		} else {
			original.call(renderPassInfo, updater);
		}
	}
}
