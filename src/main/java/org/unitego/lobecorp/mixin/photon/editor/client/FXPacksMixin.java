package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUtil;

import java.io.File;
import java.nio.file.FileSystem;

/// 保留源码资源包目录扩展，包内容与加载行为由原生管理。
@Mixin(FXPacks.class)
public abstract class FXPacksMixin {

	@ModifyReturnValue(method = "getFxPacksDir", at = @At("RETURN"))
	private static File lobecorp$sourcePacks(File original) {
		return DevelopmentResourceUtil.packsDirectory(original);
	}







	@Inject(method = "removeFx", at = @At("HEAD"))
	private static void lobecorp$checkBeforeRemoving(File file, Identifier id, CallbackInfo ci) {
		PhotonEditorProjectGuardUtil.requireSupportedFile(file);
	}

	@Inject(method = "gc", at = @At("HEAD"))
	private static void lobecorp$checkBeforeSweeping(FileSystem pack, CallbackInfo ci) {
		PhotonEditorProjectGuardUtil.requireSupportedPack(pack);
	}
}
