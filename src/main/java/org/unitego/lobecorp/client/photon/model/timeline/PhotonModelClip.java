package org.unitego.lobecorp.client.photon.model.timeline;

import com.lowdragmc.photon.client.fx.timeline.Clip;

/// 片段窗口、偏移和速度使用原tick协议；结束保持不会重新进入循环起点。
public class PhotonModelClip extends Clip {
	public enum Completion {
		HOLD, RESTORE
	}

	public String animation = "";
	public double offset;
	public boolean loop;
	public Completion completion = Completion.RESTORE;

	public PhotonModelClip(double start, double duration, float speed) {
		super(start, duration, speed);
	}

	public double sampleTime(double timelineTime) {
		return offset + localTime(timelineTime) * speed();
	}

	public void validate() {
		if (!Double.isFinite(start()) || start() < 0 || !Double.isFinite(duration()) || duration() < 0
				|| !Float.isFinite(speed()) || speed() < 0 || !Double.isFinite(offset)
				|| Math.abs(offset) + duration() * speed() > Float.MAX_VALUE) {
			throw new IllegalArgumentException("模型动画片段时间无效。");
		}
	}

	public record Sample(String animation, float time, boolean loop, boolean holdEnd, int priority, long order) {
		public float assetTime(float duration, boolean assetLoops) {
			if (!loop || !holdEnd || !assetLoops) {
				return time;
			}

			float phase = time % duration;
			return phase == 0 ? duration : phase < 0 ? phase + duration : phase;
		}

		public boolean effectiveLoop() {
			return loop && !holdEnd;
		}
	}

	public void restore(PhotonModelClip source) {
		start(source.start()).duration(source.duration()).speed(source.speed());
		targetId(source.targetId()).seed(source.seed()).randomSeed(source.randomSeed());
		animation = source.animation;
		offset = source.offset;
		loop = source.loop;
		completion = source.completion;
	}

	@Override
	public PhotonModelClip copy() {
		var result = new PhotonModelClip(start(), duration(), speed());
		result.restore(this);
		return result;
	}
}
