package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.photon.client.fx.FXHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationLifecycleUtil;

@Mixin(FXHelper.class)
public abstract class PhotonAnimationReloadMixin {
	@Inject(method = "clearCache", at = @At("HEAD"))
	private static void lobecorp$clearAnimations(CallbackInfoReturnable<Integer> cir) {
		PhotonAnimationLifecycleUtil.clear(null);
	}
}

