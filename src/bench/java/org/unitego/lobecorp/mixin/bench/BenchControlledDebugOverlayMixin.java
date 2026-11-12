package org.unitego.lobecorp.mixin.bench;

import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.bench.BenchControlledDebugOverlay;

/// 只在指定的受控 Bench 场景装载，不替换调试界面或采集器。
@Mixin(DebugScreenEntryList.class)
public class BenchControlledDebugOverlayMixin {
    @Inject(method = "setOverlayVisible", at = @At("HEAD"), cancellable = true)
    private void lobecorp$keepControlledOverlay(boolean visible, CallbackInfo ci) {
        if (BenchControlledDebugOverlay.rejectOpening(visible)) {
            ci.cancel();
        }
    }
}
