package org.unitego.lobecorp.mixin.geckolib.client;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffects;

/**
 * 动画切换或重启时结束本控制器旧的关键帧效果，保留代码管理的实例。
 */
@Mixin(AnimationController.class)
public abstract class AnimationControllerMixin<T extends GeoAnimatable> {
	@Inject(method = "initializeNewAnimation", at = @At("HEAD"))
	private void lobecorp$stopPreviousPhotonEffects(T animatable, GeoRenderState renderState, GeoModel<T> geoModel,
	                                                double prevAnimSpeed, int prevTransitionTicks, CallbackInfo ci) {
		PhotonGeoEffects.stopKeyframes(renderState, (AnimationController<?>) (Object) this);
	}
}
