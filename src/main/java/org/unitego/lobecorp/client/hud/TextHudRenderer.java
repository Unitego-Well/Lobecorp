package org.unitego.lobecorp.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class TextHudRenderer extends BasicHudRenderer {
	private Component text = Component.empty();
	private int color = -1;
	private boolean shadow;
	private Alignment alignment = Alignment.LEFT;

	public void setText(Component text) {
		this.text = text;
	}

	public void setColor(int color) {
		this.color = color;
	}

	public void setShadow(boolean shadow) {
		this.shadow = shadow;
	}

	public void setAlignment(Alignment alignment) {
		this.alignment = alignment;
	}

	@Override
	public void layout() {
		var font = Minecraft.getInstance().font;
		setSize(font.width(text), font.lineHeight);
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor graphics, float x, float y, float width, float height) {
		var font = Minecraft.getInstance().font;
		float offset = switch (alignment) {
			case LEFT -> 0;
			case CENTER -> width / 2;
			case RIGHT -> width;
		};
		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(x - offset, y);
			graphics.text(font, text, 0, 0, color, shadow);
		} finally {
			pose.popMatrix();
		}
	}

	public enum Alignment {LEFT, CENTER, RIGHT}
}
