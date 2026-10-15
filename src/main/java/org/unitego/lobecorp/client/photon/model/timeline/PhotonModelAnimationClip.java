package org.unitego.lobecorp.client.photon.model.timeline;

import com.lowdragmc.photon.client.fx.timeline.Clip;

/// 原生时间轴上的模型动画片段，采样时间由时间轴决定，不创建独立播放器。
public class PhotonModelAnimationClip extends Clip {
	public enum Completion {
		HOLD, RESTORE
	}

	public String animation = "";
	/// 相对于模型动画起点的偏移，单位为 tick。
	public double offset;
	public boolean loop;
	public Completion completion = Completion.RESTORE;

	public PhotonModelAnimationClip() {
		super();
	}

	public PhotonModelAnimationClip(double start, double duration, float speed) {
		super(start, duration, speed);
	}

	/// 原生本地时间钳制到片段窗口，结束后调用仍返回同一末尾采样时间。
	public double sampleTime(double timelineTime) {
		return offset + localTime(timelineTime) * speed();
	}

	public void restoreFrom(PhotonModelAnimationClip value) {
		start(value.start()).duration(value.duration()).speed(value.speed());
		targetId(value.targetId()).seed(value.seed()).randomSeed(value.randomSeed());
		animation = value.animation;
		offset = value.offset;
		loop = value.loop;
		completion = value.completion;
	}

	@Override
	public PhotonModelAnimationClip copy() {
		var result = new PhotonModelAnimationClip(start(), duration(), speed());
		result.restoreFrom(this);
		return result;
	}
}
