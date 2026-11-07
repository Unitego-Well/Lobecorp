package org.unitego.lobecorp.client.hud.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.config.client.HudStyle;
import org.unitego.lobecorp.config.client.PlayerHudConfig;
import org.unitego.lobecorp.Lobecorp;

/// 深夜清扫倒计时；父容器提供堆叠Y，默认框单独贴屏幕左边。
public class MidnightCleanupHud extends EventHudComponent {
	private static final Identifier DIGITS = texture(false, "digits");
	private static final Identifier TEXT_BOX = texture(false, "text_box");
	private static final Identifier TIMER = texture(false, "timer");
	private static final Identifier NEW_DIGITS = texture(true, "digits");
	private static final Identifier NEW_TIMER = texture(true, "timer_box");
	/// 精灵按冒号、0至9排列，格宽7、间隔1，纹理总宽87。
	private static final int GLYPH_WIDTH = 7, GLYPH_STEP = 8, SHEET_WIDTH = 87;
	private static final int[] DEFAULT_X = {20, 27, 34, 37, 44};
	private static final int[] NEW_X = {12, 22, 30, 38, 48};
	/// 两位分钟的显示上限；超过时保持99:59，不回绕成00:00。
	private static final int MAX_SECONDS = 99 * 60 + 59;
	private int seconds;
	private boolean colonVisible = true;

	public MidnightCleanupHud() {
		layout();
	}

	public void update(double remainingTicks, double eventTicks) {
		seconds = (int) Math.clamp(Math.ceil(remainingTicks / 20), 0, MAX_SECONDS);
		colonVisible = Math.floorMod((long) Math.floor(eventTicks), 20) < 10;
	}

	@Override
	public int height() {
		layout();
		return super.height();
	}

	@Override
	public void layout() {
		boolean modern = PlayerHudConfig.MIDNIGHT_CLEANUP_STYLE.get() == HudStyle.NEW;
		setSize(modern ? 67 : 61, modern ? 48 : 31);
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		var minecraft = Minecraft.getInstance();
		if (minecraft.options.hideGui || minecraft.player == null || minecraft.player.isSpectator()) {
			return;
		}

		boolean modern = PlayerHudConfig.MIDNIGHT_CLEANUP_STYLE.get() == HudStyle.NEW;
		float left = modern ? x : 0;
		int boxWidth = modern ? 67 : 61;
		int boxHeight = modern ? 48 : 31;
		draw(graphics, modern ? NEW_TIMER : TIMER, left, y, 0, boxWidth, boxHeight, boxWidth, boxHeight);
		if (!modern) {
			draw(graphics, TEXT_BOX, left + 12, y + 21, 0, 17, 10, 17, 10);
		}

		int glyphHeight = modern ? 8 : 11;
		int[] positions = modern ? NEW_X : DEFAULT_X;
		int[] glyphs = {seconds / 60 / 10 + 1, seconds / 60 % 10 + 1, 0,
				seconds % 60 / 10 + 1, seconds % 10 + 1};
		for (int index = 0; index < glyphs.length; index++) {
			if (index == 2 && !colonVisible) {
				continue;
			}

			boolean narrowColon = !modern && index == 2;
			int u = glyphs[index] * GLYPH_STEP + (narrowColon ? 2 : 0);
			draw(graphics, modern ? NEW_DIGITS : DIGITS, left + positions[index], y + (modern ? 20 : 7),
					u, narrowColon ? 3 : GLYPH_WIDTH, glyphHeight, SHEET_WIDTH, glyphHeight);
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, Identifier texture, float x, float y,
	                         int u, int width, int height, int textureWidth, int textureHeight) {
		graphics.lobecorp$blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, 0, width, height,
				width, height, textureWidth, textureHeight, -1);
	}

	private static Identifier texture(boolean modern, String name) {
		return Lobecorp.id("textures/gui/sprites/" + (modern ? "newhud" : "hud")
				+ "/event/midnight_cleanup/" + name + ".png");
	}
}
