package org.unitego.lobecorp.client.photon.playback.spawn;

import org.unitego.lobecorp.config.photon.playback.PhotonSpawnConfig.Mode;

import java.util.function.IntConsumer;

/// 半开时间窗调度；只计算发射意图，概率、容量、实例创建由宿主处理。
public class PhotonSpawnSchedule {
	private long cycle = -1;
	private Mode mode;
	private int interval;
	private double next;
	private double remainder;
	private boolean oneShot;

	public void reset() {
		cycle = -1;
		mode = null;
		interval = 0;
		next = 0;
		remainder = 0;
		oneShot = false;
	}

	public void advance(double age, double delta, int duration, int delay, boolean looping, Mode mode,
	                    int interval, double rate, Runnable burst, IntConsumer emission) {
		if (!Double.isFinite(age) || !Double.isFinite(delta) || delta < 0 || duration <= 0 || delay < 0
				|| interval <= 0 || !Double.isFinite(rate) || rate < 0) {
			throw new IllegalArgumentException("子发射调度输入无效。");
		}

		double start = age - delay;
		double end = start + delta;

		if (end <= 0 || end <= Math.max(start, 0)) {
			return;
		}

		if (mode == Mode.ONE_SHOT) {
			if (!oneShot && (looping || Math.max(start, 0) < duration)) {
				oneShot = true;
				burst.run();
			}

			return;
		}

		if (!looping) {
			window(0, Math.max(start, 0), Math.min(end, duration), mode, interval, rate, burst, emission);
			return;
		}

		double position = Math.max(start, 0);

		while (position < end) {
			long current = (long) Math.floor(position / duration);
			double base = (double) current * duration;
			double finish = Math.min(end, base + duration);

			if (finish <= position) {
				throw new IllegalArgumentException("子发射时间精度不足以推进窗口。");
			}

			window(current, position - base, finish - base, mode, interval, rate, burst, emission);
			position = finish;
		}
	}

	private void window(long cycle, double start, double end, Mode mode, int interval, double rate,
	                    Runnable burst, IntConsumer emission) {
		if (end <= start) {
			return;
		}

		if (this.cycle != cycle || this.mode != mode) {
			this.cycle = cycle;
			this.mode = mode;
			this.interval = 0;
			next = 0;
			remainder = 0;
		}

		if (mode == Mode.RATE) {
			double amount = remainder + rate * (end - start);
			double whole = Math.floor(amount);
			remainder = amount - whole;
			emission.accept((int) Math.min(whole, Integer.MAX_VALUE));
			return;
		}

		if (mode == Mode.ONCE) {
			if (next == 0 && start == 0) {
				burst.run();
			}

			next = Double.POSITIVE_INFINITY;
			return;
		}

		if (this.interval != interval || next < start) {
			next = Math.ceil(start / interval) * interval;
		}

		this.interval = interval;

		while (next < end) {
			burst.run();
			next += interval;
		}
	}
}
