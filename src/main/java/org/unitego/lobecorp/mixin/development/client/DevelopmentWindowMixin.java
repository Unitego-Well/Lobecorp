package org.unitego.lobecorp.mixin.development.client;

import com.mojang.blaze3d.platform.Window;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.development.DevelopmentWindowPolicy;

@Mixin(Window.class)
public abstract class DevelopmentWindowMixin {
	@Unique
	private DevelopmentWindowPolicy lobecorp$windowPolicy;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void lobecorp$initializeWindow(CallbackInfo ci) {
		if (!FMLEnvironment.isProduction()) {
			var window = (Window) (Object) this;
			lobecorp$windowPolicy = DevelopmentWindowPolicy.install(window.handle(), window.isFullscreen());
		}
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void lobecorp$detachWindow(CallbackInfo ci) {
		if (lobecorp$windowPolicy != null) {
			lobecorp$windowPolicy.detach();
		}
	}

	@Inject(method = "close", at = @At("RETURN"))
	private void lobecorp$releaseWindow(CallbackInfo ci) {
		if (lobecorp$windowPolicy != null) {
			lobecorp$windowPolicy.releaseAfterDestroy();
			lobecorp$windowPolicy = null;
		}
	}
}
