package org.unitego.lobecorp.client.hud.status.bar;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.util.client.gui.GuiUtil;

/// 状态条的背景、浮点填充、损失拖尾和两段闪光；不承担玩家数据或宿主生命周期。
///
/// 坐标由调用方按组件布局传入，本类只绘制相对该坐标的局部内容。
@NullMarked
public class StatusGauge {
	private static final float VALUE_LERP_SPEED = 1.5F;
	private static final float TRAIL_LERP_SPEED = 0.2F;
	private static final float FLASH_TICKS = 2;
	private static final int TRAIL_COLOR = 0xB2FFFFFF;
	private static final float TRAIL_VALUE_THRESHOLD = 1;
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
	private float pendingVisibleDelta;
	private boolean hasPendingVisibleDelta;

	public StatusGauge(int width, int height, TextureLayer bottom, TextureLayer internal, TextureLayer light) {
		this.width = width;
		this.height = height;
		this.bottom = bottom;
		this.internal = internal;
		this.light = light;
	}

	public void advance(float next, float nextMaximum, float delta, boolean visible) {
		settleVisibleFrame();
		maximum = Float.isFinite(nextMaximum) ? Math.max(0, nextMaximum) : 0;
		next = Float.isFinite(next) ? Math.clamp(next, 0, maximum) : 0;
		if (next != currentValue) {
			lightTicks = FLASH_TICKS;
			setLightWidth(currentValue, next);
		}

		renderedValue += (next - renderedValue) * Math.clamp(delta * VALUE_LERP_SPEED, 0, 1);
		if (renderedValue <= TRAIL_VALUE_THRESHOLD) {
			lightWidth = 0;
		}

		if (visible) {
			pendingVisibleDelta = delta;
			hasPendingVisibleDelta = true;
		}
	}

	private void settleVisibleFrame() {
		if (!hasPendingVisibleDelta) {
			return;
		}

		hasPendingVisibleDelta = false;
		lightWidth += -lightWidth * Math.clamp(pendingVisibleDelta * TRAIL_LERP_SPEED, 0, 1);
		lightTicks = Math.max(0, lightTicks - pendingVisibleDelta);
		pendingVisibleDelta = 0;
	}

	private void setLightWidth(float oldValue, float newValue) {
		if (!Float.isFinite(oldValue) || !Float.isFinite(newValue)) {
			return;
		}

		if (maximum <= 0) {
			lightWidth = 0;
			currentValue = 0;
			return;
		}

		float change = Math.clamp((oldValue - newValue) / maximum * internal.width(), -internal.width(), internal.width());
		lightWidth = Math.max(0, lightWidth + change);
		currentValue = Math.clamp(newValue, 0, maximum);
	}

	public void render(GuiGraphicsExtractor graphics, float x, float y) {
		drawLayer(graphics, bottom, x, y);
		renderInternal(graphics, x, y);
		if (lightTicks > 1.5F || lightTicks > 0 && lightTicks <= 0.5F) {
			drawLayer(graphics, light, x, y);
		}
	}

	private void renderInternal(GuiGraphicsExtractor graphics, float x, float y) {
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

	private void drawLayer(GuiGraphicsExtractor graphics, TextureLayer layer, float x, float y) {
		GuiUtil.blitSprite(graphics, layer.texture(), x + layer.x(), y + layer.y(), layer.width(), layer.height());
	}

	public void setLayers(TextureLayer bottom, TextureLayer internal, TextureLayer light) {
		this.bottom = bottom;
		this.internal = internal;
		this.light = light;
	}

	public float getRenderedValue() {
		return renderedValue;
	}

	public float getLightWidth() {
		return lightWidth;
	}

	public float getLightTicks() {
		return lightTicks;
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
		pendingVisibleDelta = 0;
		hasPendingVisibleDelta = false;
	}

	public record TextureLayer(Identifier texture, float x, float y, float width, float height) {
	}
}
