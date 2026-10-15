package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.photon.client.PhotonParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationLifecycleUtil;

@Mixin(PhotonParticleManager.class)
public abstract class PhotonAnimationManagerMixin {
	@Inject(method = "clear", at = @At("HEAD"))
	private void lobecorp$discardAnimations(CallbackInfo ci) {
		PhotonAnimationLifecycleUtil.clear((PhotonParticleManager) (Object) this);
	}
}

