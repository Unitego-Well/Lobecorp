package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.fxpack.FXPackExporter;
import net.minecraft.core.HolderLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.File;

@Mixin(FXPackExporter.class)
public abstract class PhotonPackWriteGuardMixin {
	@Inject(method = "exportInto", at = @At("HEAD"))
	private static void lobecorp$checkPack(File file, String namespace, String name, FX fx,
	                                       HolderLookup.Provider provider, CallbackInfoReturnable<FXPackExporter.Result> cir) {
		PhotonEditorProjectGuardUtil.requireSupportedFile(file);
		PhotonEditorProjectGuardUtil.requireSupported(fx.serializeNBT(provider));
	}
}
