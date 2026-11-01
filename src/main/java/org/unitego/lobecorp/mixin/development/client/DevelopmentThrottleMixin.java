package org.unitego.lobecorp.mixin.development.client;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import com.mojang.blaze3d.platform.FramerateLimitTracker.FramerateThrottleReason;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public abstract class DevelopmentThrottleMixin {
	@Inject(method = "getThrottleReason", at = @At("RETURN"), cancellable = true)
	private void lobecorp$disableInactivityThrottle(CallbackInfoReturnable<FramerateThrottleReason> cir) {
		if (FMLEnvironment.isProduction()) {
			return;
		}

		switch (cir.getReturnValue()) {
			case WINDOW_ICONIFIED, LONG_AFK, SHORT_AFK -> cir.setReturnValue(FramerateThrottleReason.NONE);
			default -> {
			}
		}
	}
}
