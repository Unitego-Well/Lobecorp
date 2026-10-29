package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.client.fx.FXData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

@Mixin(FXData.class)
public abstract class PhotonFXDataGuardMixin {
    @Inject(method = "deserializeNBT", at = @At("HEAD"))
    private void lobecorp$checkBeforeMutation(HolderLookup.Provider provider, CompoundTag tag, CallbackInfo ci) {
        if (!PhotonCastProfiler.enabled()) {
            PhotonEditorProjectGuardUtil.requireSupported(tag);
            return;
        }

        var scope = PhotonFXLoadDiagnosticsUtil.begin(Stage.GUARD_FX);
        try {
            PhotonEditorProjectGuardUtil.requireSupported(tag);
            scope.succeeded();
        } finally {
            PhotonFXLoadDiagnosticsUtil.finish(scope);
        }
    }
}
