package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

@Mixin(targets = "com.lowdragmc.photon.client.gameobject.FXObjectType$1", remap = false)
public abstract class PhotonVersionDeserializeDiagnosticsMixin {
	@WrapOperation(method = "decode", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/FXObjectType;fixData(Lnet/minecraft/nbt/CompoundTag;I)Lnet/minecraft/nbt/CompoundTag;"))
	private CompoundTag lobecorp$fix(FXObjectType type, CompoundTag data, int oldVersion, Operation<CompoundTag> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(type, data, oldVersion);
		}

		return PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.OBJECT_VERSION_FIX, type.getClass().getName(), ""), original, type, data, oldVersion);
	}
}
