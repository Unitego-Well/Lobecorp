package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

@Mixin(IFXObject.class)
public interface PhotonObjectDeserializeDiagnosticsMixin {
	@WrapMethod(method = "deserializeWrapper")
	private static IFXObject lobecorp$object(Tag tag, Operation<IFXObject> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(tag);
		}

		String type = "";
		String name = "";
		if (tag instanceof CompoundTag compound) {
			if (compound.get("type") instanceof StringTag(var value)) {
				type = value;
			}
			if (compound.get("data") instanceof CompoundTag data && data.get("name") instanceof StringTag(var value)) {
				name = value;
			}
		}
		var scope = PhotonFXLoadDiagnosticsUtil.beginObject(type, name);
		return PhotonFXLoadDiagnosticsUtil.call(scope, original, tag);
	}

	@WrapOperation(method = "lambda$static$2", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/FXObjectType;codec()Lcom/mojang/serialization/Codec;"))
	private static Codec<IFXObject> lobecorp$codec(FXObjectType type, Operation<Codec<IFXObject>> original) {
		if (PhotonFXLoadDiagnosticsUtil.skipDetails()) {
			return original.call(type);
		}

		return PhotonFXLoadDiagnosticsUtil.call(
				PhotonFXLoadDiagnosticsUtil.beginDetail(Stage.CODEC, type.getClass().getName(), ""), original, type);
	}
}
