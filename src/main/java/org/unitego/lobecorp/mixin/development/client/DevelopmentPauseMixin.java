package org.unitego.lobecorp.mixin.development.client;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class DevelopmentPauseMixin {
	@Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
	private void lobecorp$disableInactivePause(CallbackInfo ci) {
		if (!FMLEnvironment.isProduction()) {
			ci.cancel();
		}
	}
}
