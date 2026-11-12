package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.client.gameobject.IFXObject;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

@Mixin(IFXObject.class)
public interface PhotonObjectDataGuardMixin {
	@Inject(method = "deserializeWrapper", at = @At("HEAD"))
	private static void lobecorp$checkCopiedObject(Tag tag, CallbackInfoReturnable<IFXObject> cir) {
		if (!PhotonCastProfiler.enabled()) {
			PhotonEditorProjectGuardUtil.requireSupported(tag);
			return;
		}

		var scope = PhotonFXLoadDiagnosticsUtil.begin(Stage.GUARD_OBJECT);
		try {
			PhotonEditorProjectGuardUtil.requireSupported(tag);
			scope.succeeded();
		} finally {
			PhotonFXLoadDiagnosticsUtil.finish(scope);
		}
	}
}
