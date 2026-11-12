package org.unitego.lobecorp.mixin.bench;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.bench.PhotonQuietMatrixCapture;

/// 仅安静对照场景装载；把可见世界 HEAD、每帧结束与提交观察接到 Quiet 会话。
@Mixin(value = Minecraft.class)
public class PhotonQuietMinecraftMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void lobecorp$quietBeforeFrame(boolean advanceGameTime, CallbackInfo callback) {
        var minecraft = (Minecraft) (Object) this;
        PhotonQuietMatrixCapture.loadingFrame(minecraft);
        if (advanceGameTime && minecraft.isGameLoadFinished()) {
            PhotonQuietMatrixCapture.beforeRender(minecraft);
        }
    }

    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void lobecorp$quietAfterFrame(boolean advanceGameTime, CallbackInfo callback) {
        var minecraft = (Minecraft) (Object) this;
        PhotonQuietMatrixCapture.afterFrame(minecraft);
        PhotonQuietMatrixCapture.observeSubmission(minecraft);
    }
}
