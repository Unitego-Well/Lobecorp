package org.unitego.lobecorp.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class RectangleHudRenderer extends BasicHudRenderer {
	private int topColor = -1, bottomColor = -1;

	public void setColor(int color) {
		setColors(color, color);
	}

	public void setColors(int top, int bottom) {
		topColor = top;
		bottomColor = bottom;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		if (width <= 0 || height <= 0) {
			return;
		}
		graphics.lobecorp$fillGradient(x, y, x + width, y + height, topColor, bottomColor);
	}
}
