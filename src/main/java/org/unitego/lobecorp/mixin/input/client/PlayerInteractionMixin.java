package org.unitego.lobecorp.mixin.input.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.visual.input.PlayerInteractionControl;

@Mixin(Minecraft.class)
public class PlayerInteractionMixin {
	@Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
	private void lobecorp$blockInteraction(CallbackInfo callback) {
		if (!PlayerInteractionControl.keyboardBlocked()) {
			return;
		}
		KeyMapping.releaseAll();
		var minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.gameMode != null) {
			if (minecraft.player.isUsingItem()) {
				minecraft.gameMode.releaseUsingItem(minecraft.player);
			}
			minecraft.gameMode.stopDestroyBlock();
		}
		callback.cancel();
	}
}
