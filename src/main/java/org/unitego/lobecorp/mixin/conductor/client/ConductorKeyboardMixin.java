package org.unitego.lobecorp.mixin.conductor.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.conductor.ConductorControls;

@Mixin(KeyboardHandler.class)
public class ConductorKeyboardMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void lobecorp$handleConductorKey(long handle, int action, KeyEvent event, CallbackInfo ci) {
		if (ConductorControls.handleKeyboard(handle, event, action))
			ci.cancel();
	}

	@WrapWithCondition(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;set(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V"))
	private boolean lobecorp$allowKeyState(InputConstants.Key key, boolean down) {
		return !down || !ConductorControls.active() || key == Minecraft.getInstance().options.keyDebugModifier.getKey();
	}

	@WrapWithCondition(method = "keyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V"))
	private boolean lobecorp$allowGameClick(InputConstants.Key key) {
		return !ConductorControls.active() || key == Minecraft.getInstance().options.keyDebugModifier.getKey();
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void lobecorp$handleConductorChar(long handle, CharacterEvent event, CallbackInfo ci) {
		if (ConductorControls.handleCharTyped(handle, event))
			ci.cancel();
	}
}
