package org.unitego.lobecorp.client.photon.emitter.runtime;

import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnConfig;

/**
 * 用半开时间区间检测触发，支持小数时间步、循环边界和连续速率累计。
 */
public class PhotonEmitterSpawnSchedule {
	private long cycle = -1;
	private PhotonEmitterSpawnConfig.Mode mode;
	private double nextTrigger;
	private double rateRemainder;
	private int previousInterval;
	private boolean emittedOnce;

	public void reset() {
		cycle = -1;
		mode = null;
		previousInterval = 0;
		nextTrigger = 0;
		rateRemainder = 0;
		emittedOnce = false;
	}

	public void update(PhotonEmitterSpawner owner, float dt) {
		double start = owner.getAgeF() - Math.max(0, owner.runtime().startDelay.get());
		var end = start + dt;
		if (end <= 0) {
			return;
		}

		var duration = owner.getLifetime();
		if (owner.config.mode == PhotonEmitterSpawnConfig.Mode.ONE_SHOT) {
			var windowEnd = owner.isLooping() ? end : Math.min(end, duration);
			if (emittedOnce || windowEnd <= Math.max(start, 0)) {
				return;
			}

			// 一次性模式只尝试一次；概率、数量上限或缺失模板不会导致后续自动补发。
			emittedOnce = true;
			owner.emitConfigured();
			return;
		}

		if (!owner.isLooping()) {
			emitWindow(owner, 0, Math.max(start, 0), Math.min(end, duration));
			return;
		}

		var position = Math.max(start, 0);
		while (position < end) {
			var currentCycle = (long) Math.floor(position / duration);
			var base = (double) currentCycle * duration;
			var windowEnd = Math.min(end, base + duration);
			emitWindow(owner, currentCycle, position - base, windowEnd - base);
			position = windowEnd;
		}
	}

	private void emitWindow(PhotonEmitterSpawner owner, long currentCycle, double start, double end) {
		if (end <= start) {
			return;
		}

		if (cycle != currentCycle || mode != owner.config.mode) {
			cycle = currentCycle;
			mode = owner.config.mode;
			previousInterval = 0;
			nextTrigger = 0;
			rateRemainder = 0;
		}

		if (owner.config.mode == PhotonEmitterSpawnConfig.Mode.RATE) {
			var rate = owner.runtime().rate.get().get(owner.getRandomSource(), owner.getT()).doubleValue();
			if (!Double.isFinite(rate) || rate < 0) {
				owner.setSpawnStatus(PhotonEmitterSpawnerTextUtil.STATUS_INVALID);
				return;
			}

			var accumulated = rateRemainder + rate * (end - start);
			var whole = Math.floor(accumulated);
			rateRemainder = accumulated - whole;
			owner.emitNow((int) Math.min(whole, Integer.MAX_VALUE));
			return;
		}

		if (owner.config.mode == PhotonEmitterSpawnConfig.Mode.ONCE) {
			if (nextTrigger == 0 && start == 0) {
				owner.emitConfigured();
			}
			nextTrigger = Double.POSITIVE_INFINITY;
			return;
		}

		var interval = Math.max(1, owner.runtime().interval.get());
		if (previousInterval != interval || nextTrigger < start) {
			nextTrigger = Math.ceil(start / interval) * interval;
		}
		previousInterval = interval;

		while (nextTrigger < end) {
			owner.emitConfigured();
			nextTrigger += interval;
		}
	}
}
