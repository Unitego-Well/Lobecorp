package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.fx.FXRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;

@Mixin(FXRuntime.class)
public abstract class FXRuntimeMixin {
	@Inject(method = "emit(Lcom/lowdragmc/photon/client/fx/IEffectExecutor;I)V", at = @At("RETURN"))
	private void lobecorp$previewAfterRestart(CallbackInfo ci) {
		PhotonEditorTools.applyPreview((FXRuntime) (Object) this);
	}
}
