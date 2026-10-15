package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.photon.client.fx.VanillaParticleHost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationLifecycleUtil;

@Mixin(VanillaParticleHost.class)
public abstract class PhotonAnimationVanillaHostMixin {
	@Inject(method = "onWipe", at = @At("HEAD"))
	private static void lobecorp$discardAnimations(CallbackInfo ci) {
		PhotonAnimationLifecycleUtil.clear(VanillaParticleHost.INSTANCE);
	}
}

