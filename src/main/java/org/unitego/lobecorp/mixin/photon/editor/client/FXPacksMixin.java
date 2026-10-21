package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.RepositorySource;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUtil;

import java.io.File;
import java.nio.file.FileSystem;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.unitego.lobecorp.util.photon.model.PhotonModelPackExportUtil;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;


/// 保留源码资源包目录扩展，包内容与加载行为由原生管理。
@Mixin(FXPacks.class)
public abstract class FXPacksMixin {
	@Inject(method = "gc", at = @At("HEAD"), cancellable = true)
	private static void lobecorp$preserveModelPackEntries(FileSystem pack, CallbackInfo ci) {
		if (PhotonModelPackExportUtil.preservingPack() || lobecorp$hasModelEntries(pack)) {
			ci.cancel();
		}
	}

	@Unique
	private static boolean lobecorp$hasModelEntries(FileSystem pack) {
		var root = pack.getPath("assets");
		if (!Files.isDirectory(root)) {
			return false;
		}
		try (var files = Files.walk(root)) {
			return files.anyMatch(path -> path.toString().endsWith(".mfx"));
		} catch (IOException invalid) {
			return true;
		}
	}

	@ModifyReturnValue(method = "listFx", at = @At("RETURN"))
	private static List<Identifier> lobecorp$listModels(List<Identifier> original, File file) throws IOException {
		var result = new ArrayList<>(original);
		try (var pack = FileSystems.newFileSystem(file.toPath())) {
			var assets = pack.getPath("assets");
			if (!Files.isDirectory(assets)) {
				return List.copyOf(result);
			}
			try (var files = Files.walk(assets)) {
				for (var path : files.filter(value -> value.toString().endsWith(".mfx")).toList()) {
					var relative = assets.relativize(path);
					if (relative.getNameCount() < 3 || !relative.getName(1).toString().equals("fx")) {
						continue;
					}
					var namespace = relative.getName(0).toString();
					var name = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
					var id = Identifier.fromNamespaceAndPath(namespace, name.substring(0, name.length() - 4));
					if (!result.contains(id)) {
						result.add(id);
					}
				}
			}
		}
		return List.copyOf(result);
	}

	@Inject(method = "removeFx", at = @At("HEAD"), cancellable = true)
	private static void lobecorp$removeModelDefinition(File file, Identifier id, CallbackInfo ci) throws IOException {
		PhotonEditorProjectGuardUtil.requireSupportedFile(file);
		try (var pack = FileSystems.newFileSystem(file.toPath())) {
			var prefix = "assets/" + id.getNamespace() + "/fx/" + id.getPath();
			if (!Files.exists(pack.getPath(prefix + ".fx")) && Files.exists(pack.getPath(prefix + ".mfx"))) {
				Files.delete(pack.getPath(prefix + ".mfx"));
				ci.cancel();
			}
		}
	}

	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Ljava/lang/String;endsWith(Ljava/lang/String;)Z"))
	private static boolean lobecorp$includeModelPacks(String name, String suffix, Operation<Boolean> original) {
		return original.call(name, suffix) || suffix.equals(FXPacks.SUFFIX) && name.endsWith(".mfxpack");
	}

	@ModifyReturnValue(method = "getFxPacksDir", at = @At("RETURN"))
	private static File lobecorp$sourcePacks(File original) {
		return DevelopmentResourceUtil.packsDirectory(original);
	}

	@ModifyReturnValue(method = "repositorySource", at = @At("RETURN"))
	private static RepositorySource lobecorp$isolatePackSource(RepositorySource original) {
		return DevelopmentResourceUtil.isolatedFxPacks(original);
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
