package org.unitego.lobecorp.client.hud;

import org.jspecify.annotations.NullMarked;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.unitego.lobecorp.client.hud.host.HudSessionTracker;

/// 游戏 HUD 根节点共用的会话、帧增量与渲染入口。
@NullMarked
public abstract class AbstractHud extends ContainerHudRenderer implements GuiLayer {
	protected final HudSessionTracker session = new HudSessionTracker();
	protected float frameDelta;
	private boolean fitToScreen = true;

	public void setFitToScreen(boolean fitToScreen) {
		this.fitToScreen = fitToScreen;
	}

	public void synchronizeSession() {
		var minecraft = Minecraft.getInstance();
		if (session.update(minecraft.level, minecraft.player)) {
			onSessionChanged();
		}
	}

	protected abstract void onSessionChanged();

	protected boolean hudVisible() {
		var minecraft = Minecraft.getInstance();
		return !minecraft.options.hideGui && minecraft.player != null && !minecraft.player.isSpectator();
	}

	protected void prepareFrame(float delta) {
	}

	public void clear() {
		session.clear();
		onSessionChanged();
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		synchronizeSession();
		if (session.unavailable() || !hudVisible()) {
			return;
		}
		var minecraft = Minecraft.getInstance();
		frameDelta = minecraft.isPaused() ? 0 : deltaTracker.getRealtimeDeltaTicks();
		if (fitToScreen) {
			setSize(graphics.guiWidth(), graphics.guiHeight());
		}
		prepareFrame(frameDelta);
		super.render(graphics);
	}
}
