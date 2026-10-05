package org.unitego.lobecorp.mixin.particlestorm.client;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.state.KeyFrameEvent;
import com.geckolib.cache.animation.keyframeevent.ParticleKeyframeData;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import org.mesdag.particlestorm.api.geckolib.GeckoLibHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonGeoEffects;

/**
 * 复用 ParticleStorm 的关键帧分发，只消费明确标记为 Photon 的效果。
 */
@Mixin(GeckoLibHelper.class)
public abstract class GeckoLibHelperMixin {
	@WrapMethod(method = "processParticleEffect")
	private static void lobecorp$routePhotonKeyframe(
			KeyFrameEvent<? extends GeoAnimatable, ParticleKeyframeData> event, Operation<Void> original) {
		if (!PhotonGeoEffects.processKeyframe(event)) {
			original.call(event);
		}
	}

	@Inject(method = "afterReload", at = @At("HEAD"))
	private static void lobecorp$clearPhotonBindings(CallbackInfo ci) {
		Minecraft.getInstance().execute(PhotonGeoEffects::clear);
	}
}

