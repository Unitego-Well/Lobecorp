package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.fx.FXHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;


/// 原生特效缓存失效时刷新本模组持有的设备准备状态。
@Mixin(FXHelper.class)
public abstract class FXHelperMixin {
	@Inject(method = "invalidate", at = @At("RETURN"))
	private static void lobecorp$markOwnedEffectsDirty(CallbackInfo ci) {
		PhotonOwnedEffectResources.markDeviceDirty();
	}
}
