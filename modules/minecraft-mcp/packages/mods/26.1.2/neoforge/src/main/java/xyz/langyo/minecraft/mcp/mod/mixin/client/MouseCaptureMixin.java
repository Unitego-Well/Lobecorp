package xyz.langyo.minecraft.mcp.mod.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.langyo.minecraft.mcp.common.ControlModeHelper;

@Mixin(MouseHandler.class)
public class MouseCaptureMixin {
    /// 内部模拟回调不重新抓取系统光标；真人点击仍沿用原版捕获流程。
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void mcpmod$keepSyntheticInputVirtual(CallbackInfo callback) {
        if (ControlModeHelper.isSyntheticInput()) {
            callback.cancel();
        }
    }
}
