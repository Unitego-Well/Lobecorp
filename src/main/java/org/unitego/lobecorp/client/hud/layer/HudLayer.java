package org.unitego.lobecorp.client.hud.layer;

import net.minecraft.client.DeltaTracker;
import org.jspecify.annotations.NullMarked;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import com.lowdragmc.lowdraglib2.gui.hud.ModularHudLayer;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/// 单个 HUD 层的会话、字体、屏幕尺寸与位置；组合层统一初始化子层后再绘制。
@NullMarked
public abstract class HudLayer extends UIElement implements ModularHudLayer {
	private @Nullable ModularUI modularUI;
	protected DeltaTracker frameDelta = DeltaTracker.ZERO;

	protected HudLayer() {
		lss("position", "absolute");
		layout(layout -> layout.widthPercent(100).heightPercent(100));
	}

	@Override
	public @Nullable ModularUI getModularUI() {
		return super.getModularUI();
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (minecraft.options.hideGui || minecraft.player == null) {
			return;
		}

		init(graphics, deltaTracker);
		if (modularUI == null) {
			modularUI = new ModularUI(UI.of(this, size -> size));
		}
		ModularHudLayer.super.render(graphics, deltaTracker);
	}

	@Override
	protected void drawBackgroundAdditional(IGUIContext context) {
		if (context instanceof GUIContext gui) {
			renderContents(gui.graphics, frameDelta);
		}
	}

	protected abstract void renderContents(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
	protected final Minecraft minecraft = Minecraft.getInstance();
	protected @Nullable LocalPlayer player;
	protected Font font = minecraft.font;
	protected int screenWidth;
	protected int screenHeight;
	protected int leftPos;
	protected int topPos;

	public void init(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		frameDelta = deltaTracker;
		var nextPlayer = minecraft.player;
		playerChanged(nextPlayer);

		font = minecraft.font;
		int nextWidth = graphics.guiWidth();
		int nextHeight = graphics.guiHeight();
		if (nextWidth != screenWidth || nextHeight != screenHeight) {
			sizeChanged(nextWidth, nextHeight);
		}
	}

	protected void sizeChanged(int width, int height) {
		screenWidth = width;
		screenHeight = height;
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

	public int getLeftPos() {
		return leftPos;
	}

	public int getTopPos() {
		return topPos;
	}

	public void setLeftPos(int left) {
		setPosition(left, topPos);
	}

	public void setTopPos(int top) {
		setPosition(leftPos, top);
	}

	public LocalPlayer requirePlayer() {
		return Objects.requireNonNull(player);
	}

	public abstract int getWidth();

	public abstract int getHeight();

	public void clear() {
		if (modularUI != null) {
			modularUI.onRemoved();
			modularUI = null;
		}
		player = null;
		screenWidth = 0;
		screenHeight = 0;
	}

	protected float deltaTicks(DeltaTracker deltaTracker) {
		return minecraft.isPaused() ? 0 : deltaTracker.getRealtimeDeltaTicks();
	}
}
