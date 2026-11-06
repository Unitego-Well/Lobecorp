package org.unitego.lobecorp.mixin.bench;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/// 仅由首次释放基准的显式窗口保留开关装载；保留 FPS 等游戏选项，不调整 OS 窗口几何。
@Mixin(targets = "com.zhongbai233.bench.runtime.client.ClientGraphicsController", remap = false)
public class ClientGraphicsControllerMixin {
    @WrapWithCondition(method = "applyOnce", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;toggleFullScreen()V"), require = 1, remap = false)
    private boolean lobecorp$preserveFullscreen(Window window) {
        return false;
    }

    @WrapWithCondition(method = "applyOnce", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;setWindowed(II)V"), require = 1, remap = false)
    private boolean lobecorp$preserveWindowSize(Window window, int width, int height) {
        return false;
    }
}
