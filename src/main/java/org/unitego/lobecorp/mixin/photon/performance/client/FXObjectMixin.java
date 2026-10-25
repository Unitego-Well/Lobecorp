package org.unitego.lobecorp.mixin.photon.performance.client;

import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.performance.PhotonBenchConditions;

@Mixin(FXObject.class)
public class FXObjectMixin {
    @Inject(method = "setEffect", at = @At("RETURN"))
    private void lobecorp$seedBenchEffect(IEffectExecutor executor, CallbackInfo ci) {
        PhotonBenchConditions.seed((FXObject) (Object) this);
    }
}
