package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import org.jspecify.annotations.NullMarked;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.jspecify.annotations.Nullable;

/// 单个 HUD 层的会话、字体、屏幕尺寸与位置；组合层统一初始化子层后再绘制。
@NullMarked
public abstract class HudLayer implements GuiLayer {
	protected final Minecraft minecraft = Minecraft.getInstance();
	protected @Nullable LocalPlayer player;
	protected Font font = minecraft.font;
	protected int screenWidth;
	protected int screenHeight;
	protected int leftPos;
	protected int topPos;

	public void init(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		var nextPlayer = minecraft.player;
		playerChanged(nextPlayer);

		font = minecraft.font;
		screenWidth = graphics.guiWidth();
		screenHeight = graphics.guiHeight();
	}

	public void playerChanged(@Nullable LocalPlayer nextPlayer) {
		if (player != nextPlayer) {
			clear();
			player = nextPlayer;
		}
	}

	public void setPosition(int left, int top) {
		leftPos = left;
		topPos = top;
	}

	public abstract int getWidth();

	public abstract int getHeight();

	public void clear() {
		player = null;
		screenWidth = 0;
		screenHeight = 0;
	}

	protected float deltaTicks(DeltaTracker deltaTracker) {
		return minecraft.isPaused() ? 0 : deltaTracker.getRealtimeDeltaTicks();
	}
}
