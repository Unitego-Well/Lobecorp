package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectAssets;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;

/// 只使自有资产设备凭据失效，不修改Photon及Iris自身资源。
@Mixin(IrisCompat.class)
public abstract class PhotonOwnedDeviceMixin {
	@Inject(method = "invalidate", at = @At("RETURN"))
	private static void lobecorp$invalidateOwnedDevices(CallbackInfo ci) {
		PhotonOwnedEffectAssets.invalidateDeviceResources();
		PhotonOwnedEffectResources.markDeviceDirty();
	}
}
