package org.unitego.lobecorp.client.hud.host;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// 一次同步 HUD 提取所需的当帧上下文。
///
/// `graphics` 与 `deltaTracker` 只在该次提取调用内有效，不得保存到跨帧字段。
@NullMarked
public record HudFrame(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker,
                       @Nullable ClientLevel level, @Nullable LocalPlayer player,
                       int screenWidth, int screenHeight) {
	/// 当前帧对应的会话身份。
	public HudSession session() {
		return new HudSession(level, player);
	}
}
