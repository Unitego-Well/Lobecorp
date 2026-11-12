package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.fx.fxpack.FXPacks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.resource.DevelopmentResourceUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/// 保留源码资源包目录扩展，包内容与加载行为由原生管理。
@Mixin(FXPacks.class)
public abstract class FXPacksMixin {
	@Shadow
	private static void mark(CompoundTag tag, FileSystem zip, Set<String> referenced, Set<String> visited) {
		throw new AssertionError();
	}

	@WrapOperation(method = "gc", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/fxpack/FXPacks;mark(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/FileSystem;Ljava/util/Set;Ljava/util/Set;)V"))
	private static void lobecorp$markComposition(CompoundTag tag, FileSystem zip, Set<String> referenced,
	                                             Set<String> visited, Operation<Void> original) throws IOException {
		var key = "assets/lobecorp/compositions/owned_laser.nbt";
		var manifest = zip.getPath(key);
		if (Files.isRegularFile(manifest) && referenced.add(key)) {
			try (var input = Files.newInputStream(manifest)) {
				mark(NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap()), zip, referenced, visited);
			}
		}
		original.call(tag, zip, referenced, visited);
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
