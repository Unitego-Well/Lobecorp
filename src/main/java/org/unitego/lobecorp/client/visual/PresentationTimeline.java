package org.unitego.lobecorp.client.visual;

/// 调用方提供游戏时钟；暂停、跳转、完成和取消共用同一播放位置。
public class PresentationTimeline {

	private double began, offset, duration;
	private boolean running;
	private Runnable cleanup = () -> {};

	public void start(double now, double length, Runnable onFinish) {
		cancel();
		began = now;
		offset = 0;
		duration = length;
		cleanup = onFinish;
		running = true;
	}

	public float time(double now) {
		return (float) Math.clamp(now - began + offset, 0, duration);
	}

	public boolean active(double now) {
		if (running && time(now) >= duration) {
			cancel();
		}
		return running;
	}

	public void seek(double now, double target) {
		if (running) {
			began = now;
			offset = Math.clamp(target, 0, duration);
		}
	}

	public void cancel() {
		if (!running) {
			return;
		}
		running = false;
		var callback = cleanup;
		cleanup = () -> {};
		callback.run();
	}
}
