package org.unitego.lobecorp.mixin.bench;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// 仅由需要保留窗口的 Bench 场景装载，屏蔽尺寸及全屏切换，保留其余图形选项。
@Mixin(targets = "com.zhongbai233.bench.runtime.client.ClientGraphicsController")
public class BenchWindowPreservationMixin {
	@WrapWithCondition(method = "applyOnce", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/Window;toggleFullScreen()V"), require = 1)
	private boolean lobecorp$preserveFullscreen(Window window) {
		return false;
	}

	@WrapWithCondition(method = "applyOnce", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/platform/Window;setWindowed(II)V"), require = 1)
	private boolean lobecorp$preserveWindowSize(Window window, int width, int height) {
		return false;
	}
}
