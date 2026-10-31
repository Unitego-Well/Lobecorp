package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.client.fx.FX;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.unitego.lobecorp.util.photon.PhotonFXResourceUtil;
import com.lowdragmc.photon.client.fx.fxpack.FXPackExporter;
import net.minecraft.core.HolderLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import net.minecraft.resources.Identifier;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.model.PhotonModelPackExportUtil;

@Mixin(FXPackExporter.class)
public abstract class PhotonPackWriteGuardMixin {
	@ModifyExpressionValue(method = "packSources", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FXHelper;fileLocation(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/resources/Identifier;"))
	private Identifier lobecorp$resolveModelSource(Identifier original) {
		var manager = Minecraft.getInstance().getResourceManager();
		return PhotonFXResourceUtil.resolve(original, id -> manager.getResource(id).isPresent());
	}

	@WrapOperation(method = "packSources", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/instance/InstanceBaker;directSources(Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Set;"))
	private Set<Identifier> lobecorp$includeChildSources(CompoundTag fxData, Operation<Set<Identifier>> original) throws IOException {
		return PhotonModelPackExportUtil.instanceSources(fxData, original.call(fxData));
	}

	@Inject(method = "walk", at = @At("HEAD"))
	private void lobecorp$prepareModelAssets(CompoundTag tag, CallbackInfo ci) throws IOException {
		PhotonModelPackExportUtil.prepareModelObject(tag);
	}

	@Inject(method = "exportInto", at = @At("HEAD"))
	private static void lobecorp$checkPack(File file, String namespace, String name, FX fx,
	                                       HolderLookup.Provider provider, CallbackInfoReturnable<FXPackExporter.Result> cir) {
		PhotonEditorProjectGuardUtil.requireSupportedFile(file);
		PhotonEditorProjectGuardUtil.requireSupported(fx.serializeNBT(provider));
	}
}
