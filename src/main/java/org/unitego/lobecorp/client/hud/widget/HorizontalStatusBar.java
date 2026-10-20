package org.unitego.lobecorp.client.hud.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.util.client.gui.GuiUtil;

/// 旧状态条的背景、浮点填充、损失拖尾和两段闪光；不承担玩家数据或网络状态。
public class HorizontalStatusBar {
	private static final float VALUE_LERP_SPEED = 1.5F;
	private static final float TRAIL_LERP_SPEED = 0.2F;
	private static final float FLASH_TICKS = 2;
	private static final int TRAIL_COLOR = 0xB2FFFFFF;
	private final int width;
	private final int height;
	private TextureLayer bottom;
	private TextureLayer internal;
	private TextureLayer light;
	private float currentValue;
	private float renderedValue;
	private float maximum;
	private float lightWidth;
	private float lightTicks;
	private int x;
	private int y;

	public HorizontalStatusBar(int width, int height, TextureLayer bottom, TextureLayer internal, TextureLayer light) {
		this.width = width;
		this.height = height;
		this.bottom = bottom;
		this.internal = internal;
		this.light = light;
	}

	public void update(float next, float nextMaximum, float delta) {
		maximum = Float.isFinite(nextMaximum) ? Math.max(0, nextMaximum) : 0;
		next = Float.isFinite(next) ? Math.clamp(next, 0, maximum) : 0;
		if (next != currentValue) {
			lightTicks = FLASH_TICKS;
			if (maximum > 0) {
				lightWidth = Math.max(0, lightWidth + (currentValue - next) / maximum * internal.width());
			}

			currentValue = next;
		}

		renderedValue += (next - renderedValue) * Math.clamp(delta * VALUE_LERP_SPEED, 0, 1);
	}

	public void render(GuiGraphicsExtractor graphics, float delta) {
		drawLayer(graphics, bottom);
		renderInternal(graphics);
		if (lightTicks > 1.5F || lightTicks > 0 && lightTicks <= 0.5F) {
			drawLayer(graphics, light);
		}

		lightWidth += -lightWidth * Math.clamp(delta * TRAIL_LERP_SPEED, 0, 1);
		lightTicks = Math.max(0, lightTicks - delta);
	}

	private void renderInternal(GuiGraphicsExtractor graphics) {
		if (maximum <= 0) {
			return;
		}

		float fillWidth = Math.clamp(renderedValue / maximum, 0, 1) * internal.width();
		float left = x + internal.x();
		float top = y + internal.y();
		if (lightWidth > 0) {
			float trailStart = Math.clamp(currentValue / maximum * internal.width(), 0, internal.width());
			float trailEnd = Math.clamp(trailStart + lightWidth, 0, internal.width());
			GuiUtil.fill(graphics, left + trailStart, top, left + trailEnd, top + internal.height(),
					lightTicks > 0 ? -1 : TRAIL_COLOR);
		}

		GuiUtil.blitSprite(graphics, internal.texture(), internal.width(), internal.height(),
				0, 0, left, top, fillWidth, internal.height());
	}

	private void drawLayer(GuiGraphicsExtractor graphics, TextureLayer layer) {
		GuiUtil.blitSprite(graphics, layer.texture(), x + layer.x(), y + layer.y(), layer.width(), layer.height());
	}

	public void setLayers(TextureLayer bottom, TextureLayer internal, TextureLayer light) {
		this.bottom = bottom;
		this.internal = internal;
		this.light = light;
	}

	public void setPosition(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public float getRenderedValue() {
		return renderedValue;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public void clear() {
		currentValue = 0;
		renderedValue = 0;
		maximum = 0;
		lightWidth = 0;
		lightTicks = 0;
	}

	public record TextureLayer(Identifier texture, float x, float y, float width, float height) {
	}
}
