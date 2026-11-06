package org.unitego.lobecorp.client.hud.status.bar;

import java.util.concurrent.ThreadLocalRandom;

/// 整条状态框的短时往返位移；只提供 pose 偏移，不修改布局。
public class StatusBarShake {
	private static final float DURATION_TICKS = 8;
	/// 每减少 1% 对应 0.1px，整个抖动向量长度不超过 10px。
	private static final float PIXELS_PER_PERCENT = 0.1F;
	private static final float MAX_AMPLITUDE = 10;
	private float elapsed = DURATION_TICKS;
	private float amplitude;
	private float directionX;
	private float directionY;

	public void advance(float delta) {
		elapsed = Math.min(DURATION_TICKS, elapsed + Math.max(0, delta));
	}

	public void trigger(float lostFraction) {
		if (!Float.isFinite(lostFraction) || lostFraction <= 0) {
			return;
		}
		amplitude = Math.min(MAX_AMPLITUDE, lostFraction * 100 * PIXELS_PER_PERCENT);
		double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
		directionX = (float) Math.cos(angle);
		directionY = (float) Math.sin(angle);
		elapsed = 0;
	}

	private float displacement() {
		float progress = elapsed / DURATION_TICKS;
		float wave;
		if (progress <= 0.25F) {
			wave = (float) Math.sin(progress * Math.PI * 2);
		} else if (progress <= 0.625F) {
			float t = (progress - 0.25F) / 0.375F;
			wave = 0.25F + 0.75F * (float) Math.cos(t * Math.PI);
		} else {
			float t = (progress - 0.625F) / 0.375F;
			wave = -0.25F * (1 + (float) Math.cos(t * Math.PI));
		}
		return amplitude * wave;
	}

	public float x() {
		return directionX * displacement();
	}

	public float y() {
		return directionY * displacement();
	}

	public void clear() {
		elapsed = DURATION_TICKS;
		amplitude = 0;
	}
}
