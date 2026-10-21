package xyz.langyo.minecraft.mcp.mod.mixin.client;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.langyo.minecraft.mcp.common.ControlModeHelper;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
	/// 接管期间沿用玩家帧率上限与垂直同步，仅取消无真人输入造成的额外节流。
	@Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
	private void mcpmod$keepControlledClientActive(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> callback) {
		if (!FMLEnvironment.isProduction() && ControlModeHelper.isMcpControlMode()) {
			callback.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
		}
	}
}
