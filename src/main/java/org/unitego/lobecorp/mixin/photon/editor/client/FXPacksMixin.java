package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.unitego.lobecorp.util.photon.model.PhotonModelFileUtil;

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.Path;

/// 模型包沿用原生发现、锁检测和依赖回收，只按归档后缀选择定义格式。
@Mixin(FXPacks.class)
public abstract class FXPacksMixin {

	@WrapOperation(method = "lambda$mountFolderPacks$0",
			at = @At(value = "INVOKE", target = "Ljava/lang/String;endsWith(Ljava/lang/String;)Z"))
	private static boolean lobecorp$discoverModelFiles(String name, String suffix, Operation<Boolean> original,
	                                                   @Local(name = "file", argsOnly = true) File file) {
		return original.call(name, suffix) || PhotonModelFileUtil.isModelPack(file);
	}

	@WrapOperation(method = "mountModPacks",
			at = @At(value = "INVOKE", target = "Ljava/lang/String;endsWith(Ljava/lang/String;)Z"))
	private static boolean lobecorp$discoverModelPacks(String name, String suffix, Operation<Boolean> original) {
		return original.call(name, suffix) || name.endsWith(PhotonModelFileUtil.PACK_SUFFIX);
	}

	@ModifyConstant(method = "extractToCache", constant = @Constant(stringValue = PhotonModelFileUtil.FXPACK_SUFFIX))
	private static String lobecorp$modelCacheSuffix(String suffix, @Local(name = "source", argsOnly = true) Path source) {
		return PhotonModelFileUtil.isModelPack(source) ? PhotonModelFileUtil.PACK_SUFFIX : suffix;
	}

	@ModifyConstant(method = "lambda$listFxEntries$0", constant = @Constant(stringValue = PhotonModelFileUtil.FX_SUFFIX))
	private static String lobecorp$modelEntrySuffix(String suffix, @Local(name = "p", argsOnly = true) Path path) {
		return PhotonModelFileUtil.isModelPack(path.getFileSystem()) ? PhotonModelFileUtil.DEFINITION_SUFFIX : suffix;
	}

	@ModifyConstant(method = "listFxEntries", constant = @Constant(stringValue = PhotonModelFileUtil.FX_SUFFIX))
	private static String lobecorp$modelEntryIdSuffix(String suffix, @Local(name = "zip", argsOnly = true) FileSystem zip) {
		return PhotonModelFileUtil.isModelPack(zip) ? PhotonModelFileUtil.DEFINITION_SUFFIX : suffix;
	}

	@WrapOperation(method = "removeFx",
			at = @At(value = "INVOKE", target = "Ljava/nio/file/FileSystem;getPath(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"))
	private static Path lobecorp$removeModelDefinition(FileSystem zip, String first, String[] more, Operation<Path> original) {
		if (PhotonModelFileUtil.isModelPack(zip)) {
			more = more.clone();
			var last = more.length - 1;
			more[last] = PhotonModelFileUtil.definitionPath(zip, more[last]);
		}

		return original.call(zip, first, more);
	}

	@WrapOperation(method = "gc",
			at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/fxpack/FXPacks;entryKey(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"))
	private static String lobecorp$keepModelDefinition(String namespace, String path, Operation<String> original,
	                                                   @Local(name = "zip", argsOnly = true) FileSystem zip) {
		return original.call(namespace, PhotonModelFileUtil.definitionPath(zip, path));
	}
}
