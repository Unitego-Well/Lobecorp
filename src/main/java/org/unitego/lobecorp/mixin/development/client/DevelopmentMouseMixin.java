package org.unitego.lobecorp.mixin.development.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.neoforged.fml.loading.FMLEnvironment;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class DevelopmentMouseMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
	private void lobecorp$preventBackgroundGrab(CallbackInfo ci) {
		// 原生焦点读数优先于可能滞后的 Minecraft 活跃状态；聚焦后的真人点击走原版。
		if (!FMLEnvironment.isProduction()
				&& GLFW.glfwGetWindowAttrib(minecraft.getWindow().handle(), GLFW.GLFW_FOCUSED) != GLFW.GLFW_TRUE) {
			ci.cancel();
		}
	}
}
