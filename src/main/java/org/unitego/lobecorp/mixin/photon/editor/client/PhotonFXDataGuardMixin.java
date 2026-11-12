package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.client.fx.FXData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

import java.util.Collection;

@Mixin(FXData.class)
public abstract class PhotonFXDataGuardMixin {
	@Inject(method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;Ljava/util/Collection;)V", at = @At("HEAD"))
	private void lobecorp$checkBeforeMutation(HolderLookup.Provider provider, CompoundTag tag, Collection<Identifier> as, CallbackInfo ci) {
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
