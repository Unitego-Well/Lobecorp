package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NullMarked;

/// 统一处理隐藏 GUI 与无玩家时的跳过，再执行子层初始化和绘制。
@NullMarked
public abstract class BasicHudLayer extends HudLayer {
	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (minecraft.options.hideGui || minecraft.player == null) {
			return;
		}

		init(graphics, deltaTracker);
		renderLayer(graphics, deltaTracker);
	}

	protected abstract void renderLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
}
