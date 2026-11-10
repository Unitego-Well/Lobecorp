package org.unitego.lobecorp.client.encounter.midnightcleanup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.client.hud.encounter.EncounterHudComponent;
import org.unitego.lobecorp.config.client.HudStyle;
import org.unitego.lobecorp.config.client.PlayerHudConfig;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;
import org.jspecify.annotations.NullMarked;

/// 深夜清扫倒计时；父容器提供堆叠Y，默认框单独贴屏幕左边。
@NullMarked
public class MidnightCleanupHud extends EncounterHudComponent {
	private int seconds;
	private boolean colonVisible = true;

	public MidnightCleanupHud() {
		layout();
	}

	private static void draw(GuiGraphicsExtractor graphics, Identifier texture, float x, float y,
	                         int u, int width, int height, int textureWidth, int textureHeight) {
		graphics.lobecorp$blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, 0, width, height,
				width, height, textureWidth, textureHeight, -1);
	}

	public void update(double remainingTicks, double eventTicks) {
		seconds = (int) Math.clamp(Math.ceil(remainingTicks / 20), 0, MidnightCleanupEncounter.MAX_SECONDS);
		colonVisible = Math.floorMod((long) Math.floor(eventTicks), MidnightCleanupEncounter.HUD_COLON_PERIOD_TICKS) < MidnightCleanupEncounter.HUD_COLON_VISIBLE_TICKS;
	}

	@Override
	public int height() {
		layout();
		return super.height();
	}

	@Override
	public void layout() {
		boolean modern = PlayerHudConfig.TIMER_STYLE.get() == HudStyle.NEW;
		setSize(modern ? MidnightCleanupEncounter.HUD_NEW_WIDTH : MidnightCleanupEncounter.HUD_DEFAULT_WIDTH,
				modern ? MidnightCleanupEncounter.HUD_NEW_HEIGHT : MidnightCleanupEncounter.HUD_DEFAULT_HEIGHT);
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui || minecraft.player == null || minecraft.player.isSpectator()) {
			return;
		}

		boolean modern = PlayerHudConfig.TIMER_STYLE.get() == HudStyle.NEW;
		float left = modern ? x : 0;
		int boxWidth = modern ? MidnightCleanupEncounter.HUD_NEW_WIDTH : MidnightCleanupEncounter.HUD_DEFAULT_WIDTH;
		int boxHeight = modern ? MidnightCleanupEncounter.HUD_NEW_HEIGHT : MidnightCleanupEncounter.HUD_DEFAULT_HEIGHT;
		draw(graphics, modern ? MidnightCleanupEncounter.NEW_TIMER : MidnightCleanupEncounter.TIMER, left, y, 0, boxWidth, boxHeight, boxWidth, boxHeight);
		if (!modern) {
			draw(graphics, MidnightCleanupEncounter.TEXT_BOX, left + MidnightCleanupEncounter.HUD_TEXT_BOX_X, y + MidnightCleanupEncounter.HUD_TEXT_BOX_Y, 0,
					MidnightCleanupEncounter.HUD_TEXT_BOX_WIDTH, MidnightCleanupEncounter.HUD_TEXT_BOX_HEIGHT, MidnightCleanupEncounter.HUD_TEXT_BOX_WIDTH,
					MidnightCleanupEncounter.HUD_TEXT_BOX_HEIGHT);
		}

		int glyphHeight = modern ? MidnightCleanupEncounter.HUD_NEW_GLYPH_HEIGHT : MidnightCleanupEncounter.HUD_DEFAULT_GLYPH_HEIGHT;
		int[] glyphs = {seconds / 60 / 10 + 1, seconds / 60 % 10 + 1, 0,
				seconds % 60 / 10 + 1, seconds % 10 + 1};
		for (int index = 0; index < glyphs.length; index++) {
			if (index == 2 && !colonVisible) {
				continue;
			}

			boolean narrowColon = !modern && index == 2;
			int u = glyphs[index] * MidnightCleanupEncounter.GLYPH_STEP + (narrowColon ? MidnightCleanupEncounter.HUD_COLON_U_OFFSET : 0);
			draw(graphics, modern ? MidnightCleanupEncounter.NEW_DIGITS : MidnightCleanupEncounter.DIGITS, left + MidnightCleanupEncounter.hudDigitX(modern, index),
					y + (modern ? MidnightCleanupEncounter.HUD_NEW_GLYPH_Y : MidnightCleanupEncounter.HUD_DEFAULT_GLYPH_Y),
					u, narrowColon ? MidnightCleanupEncounter.HUD_COLON_WIDTH : MidnightCleanupEncounter.GLYPH_WIDTH, glyphHeight, MidnightCleanupEncounter.SHEET_WIDTH, glyphHeight);
		}
	}

}
