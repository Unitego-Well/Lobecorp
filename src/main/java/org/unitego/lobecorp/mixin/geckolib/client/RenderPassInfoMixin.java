package org.unitego.lobecorp.mixin.geckolib.client;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.animation.LcAnimationControllerIntegration;
import org.unitego.lobecorp.animation.LcCustomAnimatable;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffects;

@Mixin(RenderPassInfo.class)
public abstract class RenderPassInfoMixin {
	@Inject(method = "renderPosed", at = @At(value = "FIELD",
			target = "Lcom/geckolib/renderer/base/RenderPassInfo;bonePositionListeners:Ljava/util/Map;",
			opcode = Opcodes.GETFIELD, ordinal = 0))
	private void lobecorp$attachPhotonLocators(Runnable renderTask, CallbackInfo ci) {
		PhotonGeoEffects.attachLocators((RenderPassInfo<?>) (Object) this);
	}

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
