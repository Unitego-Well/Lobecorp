package org.unitego.lobecorp.mixin.photon.creation.client;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;

import java.util.Collection;

@Mixin(value = FXHelper.class)
public class FXHelperCreationMixin {
	@Inject(method = "getFX(Lnet/minecraft/resources/Identifier;Z)Lcom/lowdragmc/photon/client/fx/FX;", at = @At("RETURN"))
	private static void lobecorp$recordResourceSource(Identifier id, boolean cached, CallbackInfoReturnable<FX> result) {
		if (result.getReturnValue() != null) {
			PhotonChildSpawner.bindPreloadDefinition(result.getReturnValue().getFxData());
		}
		if (cached && result.getReturnValue() != null) {
			PhotonRuntimeFactory.registerResource(result.getReturnValue(), id.toString());
		}
	}

	@Inject(method = "publish", at = @At("RETURN"))
	private static void lobecorp$recordPreloadedSource(Identifier id, CompoundTag tag, CallbackInfoReturnable<FX> result) {
		var fx = result.getReturnValue();
		if (fx != null) {
			PhotonChildSpawner.bindPreloadDefinition(fx.getFxData());
			PhotonRuntimeFactory.registerResource(fx, id.toString());
		}
	}

	@Inject(method = "invalidate(Ljava/util/Collection;)V",
			at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/client/fx/FXPreloader;onInvalidated()V"))
	private static void lobecorp$invalidateResourceDefinitions(Collection<Identifier> locations, CallbackInfo callback) {
		PhotonRuntimeFactory.invalidateResources(locations);
	}

	@Inject(method = "clearCache", at = @At("HEAD"))
	private static void lobecorp$invalidateCompiledDefinitions(CallbackInfoReturnable<Integer> result) {
		PhotonRuntimeFactory.invalidateAll();
	}
}
