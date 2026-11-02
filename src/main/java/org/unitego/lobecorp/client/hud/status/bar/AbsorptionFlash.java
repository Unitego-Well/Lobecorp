package org.unitego.lobecorp.client.hud.status.bar;

import org.jspecify.annotations.NullMarked;

/// 吸收生命的闪光计时；只在可见帧结算上一帧的衰减，隐藏帧不推进。
@NullMarked
public class AbsorptionFlash {
	private static final float FLASH_TICKS = 2;
	private float absorption;
	private float lightTicks;
	private float pendingVisibleDelta;
	private boolean hasPendingVisibleDelta;

	/// 先进结此前可见帧的衰减，再采样本次吸收值，并为本次可见帧登记待结算时间。
	public void prepare(float next, float delta, boolean visible) {
		settleVisibleFrame();
		if (next != absorption) {
			absorption = next;
			lightTicks = next > 0 ? FLASH_TICKS : 0;
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
		lightTicks = Math.max(0, lightTicks - pendingVisibleDelta);
		pendingVisibleDelta = 0;
	}

	public float absorption() {
		return absorption;
	}

	public float lightTicks() {
		return lightTicks;
	}

	public void clear() {
		absorption = 0;
		lightTicks = 0;
		pendingVisibleDelta = 0;
		hasPendingVisibleDelta = false;
	}
}
