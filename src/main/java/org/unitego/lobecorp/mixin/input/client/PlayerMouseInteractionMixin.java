package org.unitego.lobecorp.mixin.input.client;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.visual.input.PlayerInteractionControl;

@Mixin(MouseHandler.class)
public class PlayerMouseInteractionMixin {
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void lobecorp$blockButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo callback) {
		if (action != 0 && PlayerInteractionControl.mouseBlocked()) {
			callback.cancel();
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void lobecorp$blockScroll(long handle, double xoffset, double yoffset, CallbackInfo callback) {
		if (PlayerInteractionControl.mouseBlocked()) {
			callback.cancel();
		}
	}

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void lobecorp$blockTurn(double mousea, CallbackInfo callback) {
		if (PlayerInteractionControl.turnBlocked()) {
			accumulatedDX = accumulatedDY = 0;
			callback.cancel();
		}
	}
}
