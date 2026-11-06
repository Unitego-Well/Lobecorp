package org.unitego.lobecorp.client.hud.status.bar;

import org.jspecify.annotations.NullMarked;

/// 状态条的插值、拖尾和闪光状态；只保存数字时间与宽度，不持有纹理或绘制对象。
@NullMarked
public class StatusGauge {
	private static final float VALUE_LERP_SPEED = 1.5F;
	private static final float TRAIL_LERP_SPEED = 0.2F;
	private static final float FLASH_TICKS = 2;
	private static final int TRAIL_COLOR = 0xB2FFFFFF;
	private static final float TRAIL_VALUE_THRESHOLD = 1;
	private final int width;
	private final int height;
	private final int internalWidth;
	private float currentValue;
	private float renderedValue;
	private float maximum;
	private float lightWidth;
	private float lightTicks;
	private float pendingVisibleDelta;
	private boolean hasPendingVisibleDelta;

	public StatusGauge(int width, int height, int internalWidth) {
		this.width = width;
		this.height = height;
		this.internalWidth = internalWidth;
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

		float change = Math.clamp((oldValue - newValue) / maximum * internalWidth, -internalWidth, internalWidth);
		lightWidth = Math.max(0, lightWidth + change);
		currentValue = Math.clamp(newValue, 0, maximum);
	}

	/// 当前填充比例，范围 0～1。
	public float getFill() {
		return maximum > 0 ? Math.clamp(renderedValue / maximum, 0, 1) : 0;
	}

	/// 损失拖尾起点，相对填充区域宽度。
	public float getTrailStart() {
		return maximum > 0 ? Math.clamp(currentValue / maximum * internalWidth, 0, internalWidth) : 0;
	}

	/// 当前损失拖尾宽度，已限制在填充区域内。
	public float getTrailWidth() {
		return Math.clamp(getTrailStart() + lightWidth, 0, internalWidth) - getTrailStart();
	}

	/// 拖尾颜色：闪光期间使用白色，普通损失使用半透明白色。
	public int getTrailColor() {
		return lightTicks > 0 ? -1 : TRAIL_COLOR;
	}

	/// 闪光在当前帧是否可见，保持旧状态条两段闪烁。
	public boolean isFlashing() {
		return lightTicks > 1.5F || lightTicks > 0 && lightTicks <= 0.5F;
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

}
