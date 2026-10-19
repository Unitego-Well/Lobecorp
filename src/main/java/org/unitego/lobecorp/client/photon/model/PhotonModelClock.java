package org.unitego.lobecorp.client.photon.model;

/// 每个模型独占的tick时钟；柔和停止在当前周期末清理，暂停不累积欠账。
public class PhotonModelClock {
	public record Settings(boolean enabled, boolean paused, boolean loop, int duration, int startDelay, float speed) {
		public Settings {
			if (duration <= 0 || startDelay < 0 || !Float.isFinite(speed) || speed < 0) {
				throw new IllegalArgumentException("模型生命周期配置无效。");
			}
		}
	}

	private float age;
	private float animation;
	private float stopAge = Float.POSITIVE_INFINITY;
	private boolean started;
	private boolean cleared;
	private boolean stopped;

	public void reset(Settings settings, boolean preserve) {
		boolean keep = preserve && started && !cleared;
		float time = animation;
		age = 0;
		animation = keep ? time : 0;
		started = keep || settings.enabled() && settings.startDelay() == 0;
		cleared = false;
		stopped = false;
		stopAge = Float.POSITIVE_INFINITY;
	}

	public void advance(float dt, Settings settings) {
		if (!Float.isFinite(dt) || dt <= 0 || settings.paused() || cleared) {
			return;
		}

		float previous = age;
		age += dt;

		if (age >= settings.startDelay() && !started && !stopped && settings.enabled()) {
			started = true;
		}

		if (started) {
			animation += (Math.max(0, age - settings.startDelay()) - Math.max(0, previous - settings.startDelay())) * settings.speed();
		}

		if ((!settings.loop() && age - settings.startDelay() >= settings.duration()) || age >= stopAge) {
			clear();
		}
	}

	public void stop(Settings settings, boolean force) {
		stopped = true;

		if (force) {
			clear();
		} else {
			float active = Math.max(0, age - settings.startDelay());
			stopAge = Math.min(stopAge, settings.startDelay() + ((float) Math.floor(active / settings.duration()) + 1) * settings.duration());
		}
	}

	public void clear() {
		started = false;
		cleared = true;
	}

	public boolean visible(Settings settings) {
		return started && !cleared && settings.enabled();
	}

	public boolean finished() {
		return cleared;
	}

	public float age() {
		return age;
	}

	public float animationTime(float partial, float timeScale, Settings settings) {
		float delta = settings.paused() || cleared ? 0 : Math.clamp(partial, 0, 1) * timeScale;
		float active = Math.max(0, age + delta - settings.startDelay()) - Math.max(0, age - settings.startDelay());
		return animation + (started ? active * settings.speed() : 0);
	}

	public float progress(float partial, float timeScale, Settings settings) {
		float delta = settings.paused() || cleared ? 0 : Math.clamp(partial, 0, 1) * timeScale;
		float active = Math.max(0, age + delta - settings.startDelay());
		return settings.loop() ? active % settings.duration() / settings.duration() : Math.clamp(active / settings.duration(), 0, 1);
	}
}
