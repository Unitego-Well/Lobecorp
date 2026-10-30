package org.unitego.lobecorp.mixin.photon.creation.client;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;

@Mixin(value = FXHelper.class, remap = false)
public class FXHelperCreationMixin {
    @Inject(method = "getFX(Lnet/minecraft/resources/Identifier;Z)Lcom/lowdragmc/photon/client/fx/FX;", at = @At("RETURN"))
    private static void lobecorp$recordResourceSource(Identifier id, boolean cached, CallbackInfoReturnable<FX> result) {
        if (cached && result.getReturnValue() != null) {
            PhotonRuntimeFactory.registerResource(result.getReturnValue(), id.toString());
        }
    }

    @Inject(method = "clearCache", at = @At("HEAD"))
    private static void lobecorp$invalidateCompiledDefinitions(CallbackInfoReturnable<Integer> result) {
        PhotonRuntimeFactory.invalidateAll();
    }
}
