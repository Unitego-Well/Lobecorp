package org.unitego.lobecorp.mixin.photon.editor.client;

import org.unitego.lobecorp.util.photon.editor.PhotonFxPackExportUtil;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.fxpack.FXPackExporter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.File;
import java.io.IOException;
import java.util.Set;

@Mixin(FXPackExporter.class)
public abstract class PhotonPackWriteGuardMixin {
	@Inject(method = "exportInto", at = @At("HEAD"))
	private static void lobecorp$checkPack(File file, String namespace, String name, FX fx,
	                                       HolderLookup.Provider provider, CallbackInfoReturnable<FXPackExporter.Result> cir) {
		PhotonEditorProjectGuardUtil.requireSupportedFile(file);
		PhotonEditorProjectGuardUtil.requireSupported(fx.serializeNBT(provider));
	}


	@WrapOperation(method = "packSources", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/instance/InstanceBaker;directSources(Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Set;"))
	private Set<Identifier> lobecorp$includeChildSources(CompoundTag fxData, Operation<Set<Identifier>> original) throws IOException {
		return PhotonFxPackExportUtil.instanceSources(fxData, original.call(fxData));
	}


}
