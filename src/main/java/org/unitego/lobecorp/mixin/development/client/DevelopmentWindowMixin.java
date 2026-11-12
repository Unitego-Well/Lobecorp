package org.unitego.lobecorp.mixin.development.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuBackend;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Shadow;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.development.DevelopmentWindowPolicy;
import com.mojang.logging.LogUtils;

@Mixin(Window.class)
public abstract class DevelopmentWindowMixin {
	@Shadow private int width;
	@Shadow private int height;
	@Shadow private int x;
	@Shadow private int y;
	@Shadow private boolean isResized;
	@Shadow private boolean focused;

	@Unique
	private DevelopmentWindowPolicy lobecorp$windowPolicy;

	@Inject(method = "createGlfwWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
	private static void lobecorp$creationHints(int width, int height, String title, long monitor, GpuBackend backend, CallbackInfoReturnable<Long> cir) {
		DevelopmentWindowPolicy.prepareCreation(monitor);
	}

	@WrapWithCondition(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;setMode()V"))
	private boolean lobecorp$preserveCreatedMaximized(Window window) {
		return !DevelopmentWindowPolicy.nativeCreated(window.handle());
	}

	/// 仅测试诊断入口记录真实调用栈，不改变窗口模式、缓存或输入状态。
	@Inject(method = "setMode", at = @At("HEAD"))
	private void lobecorp$traceMode(CallbackInfo ci) {
		lobecorp$traceWindowCall("setMode");
	}

	@Inject(method = "setWindowed", at = @At("HEAD"))
	private void lobecorp$traceWindowed(int requestedWidth, int requestedHeight, CallbackInfo ci) {
		lobecorp$traceWindowCall("setWindowed:" + requestedWidth + "x" + requestedHeight);
	}

	@Unique
	private void lobecorp$traceWindowCall(String method) {
		if (Boolean.getBoolean("lobecorp.developmentWindowTrace")) {
			LogUtils.getLogger().info("event=window-call method={} actual={}x{} position={},{}", method,
					width, height, x, y, new Throwable("测试窗口调用来源"));
		}
	}

	@Inject(method = "updateFullscreenIfChanged", at = @At("RETURN"))
	private void lobecorp$verifyVisible(CallbackInfo ci) {
		if (lobecorp$windowPolicy != null) {
			lobecorp$windowPolicy.verifyVisible(((Window) (Object) this).handle());
		}
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void lobecorp$initializeWindow(CallbackInfo ci) {
		if (!FMLEnvironment.isProduction()) {
			var window = (Window) (Object) this;
			lobecorp$windowPolicy = DevelopmentWindowPolicy.install(window.handle(), window.isFullscreen());
			if (DevelopmentWindowPolicy.nativeCreated(window.handle())) {
				try (var stack = MemoryStack.stackPush()) {
					var first = stack.mallocInt(1);
					var second = stack.mallocInt(1);
					GLFW.glfwGetWindowSize(window.handle(), first, second);
					width = first.get(0);
					height = second.get(0);
					GLFW.glfwGetWindowPos(window.handle(), first, second);
					x = first.get(0);
					y = second.get(0);
					isResized = true;
					focused = GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
				}
			}
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
