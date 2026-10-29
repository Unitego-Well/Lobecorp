package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

import java.io.InputStream;

/// 仅由显式 cast 诊断开关加载；原加载器仍拥有异常处理、缓存和流关闭。
@Mixin(FXHelper.class)
public abstract class PhotonFXLoadDiagnosticsMixin {
	@WrapMethod(method = "getFX(Lnet/minecraft/resources/Identifier;Z)Lcom/lowdragmc/photon/client/fx/FX;")
	private static FX lobecorp$query(Identifier fxLocation, boolean useCache, Operation<FX> original) {
		if (!PhotonCastProfiler.enabled()) {
			return original.call(fxLocation, useCache);
		}

		var scope = PhotonFXLoadDiagnosticsUtil.begin(Stage.QUERY, fxLocation, useCache);
		try {
			var result = original.call(fxLocation, useCache);
			if (scope != null) {
				scope.returned(result);
			}
			return result;
		} finally {
			PhotonFXLoadDiagnosticsUtil.finish(scope);
		}
	}

	@WrapMethod(method = "loadFX(Lnet/minecraft/resources/Identifier;)Lcom/lowdragmc/photon/client/fx/FX;")
	private static FX lobecorp$load(Identifier fxLocation, Operation<FX> original) {
		if (!PhotonCastProfiler.enabled()) {
			return original.call(fxLocation);
		}

		var scope = PhotonFXLoadDiagnosticsUtil.begin(Stage.LOAD, fxLocation, false);
		try {
			var result = original.call(fxLocation);
			if (scope != null) {
				scope.returned(result);
			}
			return result;
		} finally {
			PhotonFXLoadDiagnosticsUtil.finish(scope);
		}
	}

	@WrapOperation(method = "loadFX", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/packs/resources/ResourceManager;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;"))
	private static InputStream lobecorp$open(ResourceManager manager, Identifier location, Operation<InputStream> original) {
		if (!PhotonCastProfiler.enabled()) {
			return original.call(manager, location);
		}

		var scope = PhotonFXLoadDiagnosticsUtil.begin(Stage.OPEN);
		PhotonFXLoadDiagnosticsUtil.openedResource(location);
		try {
			var result = original.call(manager, location);
			scope.returned(result);
			return result;
		} finally {
			PhotonFXLoadDiagnosticsUtil.finish(scope);
		}
	}

	@WrapOperation(method = "loadFX", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/nbt/NbtIo;readCompressed(Ljava/io/InputStream;Lnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/CompoundTag;"))
	private static CompoundTag lobecorp$read(InputStream in, NbtAccounter accounter, Operation<CompoundTag> original) {
		if (!PhotonCastProfiler.enabled()) {
			return original.call(in, accounter);
		}

		var scope = PhotonFXLoadDiagnosticsUtil.begin(Stage.READ_COMPRESSED);
		try {
			var result = original.call(in, accounter);
			scope.returned(result);
			return result;
		} finally {
			PhotonFXLoadDiagnosticsUtil.finish(scope);
		}
	}

	@WrapOperation(method = "loadFX", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FXHelper;applyVersionFixes(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"))
	private static CompoundTag lobecorp$fix(CompoundTag tag, Operation<CompoundTag> original) {
		if (!PhotonCastProfiler.enabled()) {
			return original.call(tag);
		}

		var scope = PhotonFXLoadDiagnosticsUtil.begin(Stage.VERSION_FIXES);
		try {
			var result = original.call(tag);
			scope.returned(result);
			return result;
		} finally {
			PhotonFXLoadDiagnosticsUtil.finish(scope);
		}
	}

	@WrapOperation(method = "loadFX", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FX;deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V"))
	private static void lobecorp$deserialize(FX fx, HolderLookup.Provider provider, CompoundTag tag, Operation<Void> original) {
		if (!PhotonCastProfiler.enabled()) {
			original.call(fx, provider, tag);
			return;
		}

		var scope = PhotonFXLoadDiagnosticsUtil.begin(Stage.DESERIALIZE);
		try {
			original.call(fx, provider, tag);
			scope.succeeded();
		} finally {
			PhotonFXLoadDiagnosticsUtil.finish(scope);
		}
	}
}
