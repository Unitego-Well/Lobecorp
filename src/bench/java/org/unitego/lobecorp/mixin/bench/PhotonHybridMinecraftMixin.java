package org.unitego.lobecorp.mixin.bench;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.bench.PhotonHybridMatrixCapture;

/// 仅新进入场景装载；采集入口早于Bench在世界可用后的setup。
@Mixin(value = Minecraft.class)
public class PhotonHybridMinecraftMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void lobecorp$hybridLoading(boolean advance, CallbackInfo callback) {
        var minecraft = (Minecraft) (Object) this;
        PhotonHybridMatrixCapture.loadingFrame(minecraft);
        if (advance && minecraft.isGameLoadFinished()) {
            PhotonHybridMatrixCapture.beforeRender(minecraft);
        }
    }

    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void lobecorp$hybridAfterFrame(boolean advance, CallbackInfo callback) {
        PhotonHybridMatrixCapture.afterFrame((Minecraft) (Object) this);
    }
}
