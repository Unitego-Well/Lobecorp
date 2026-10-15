package org.unitego.lobecorp.client.photon.animation.timeline;

import com.lowdragmc.photon.client.fx.timeline.Clip;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 子动画调用片段；原生 start/duration 表示父时间轴上的控制窗口。
public class PhotonChildAnimationClip extends Clip {
	public enum Mode {
		CONTROLLED, INDEPENDENT
	}

	public enum Completion {
		INHERIT, HOLD, RELEASE
	}

	public UUID id = UUID.randomUUID();
	public String animation = "";
	public Mode mode = Mode.CONTROLLED;
	public Completion completion = Completion.INHERIT;
	/// 独立运行时的播放时长覆盖，-1 沿用子动画定义。
	public double playbackDuration = -1;
	public final List<PhotonAnimationInput> inputs = new ArrayList<>();

	public PhotonChildAnimationClip() {
		super();
	}

	public PhotonChildAnimationClip(double start, double duration, float speed) {
		super(start, duration, speed);
	}

	/// 撤销时恢复同一个片段对象，保留原生选择与拖动状态使用的对象身份。
	public void restoreFrom(PhotonChildAnimationClip value) {
		start(value.start()).duration(value.duration()).speed(value.speed());
		targetId(value.targetId()).seed(value.seed()).randomSeed(value.randomSeed());
		id = value.id;
		animation = value.animation;
		mode = value.mode;
		completion = value.completion;
		playbackDuration = value.playbackDuration;
		inputs.clear();
		value.inputs.forEach(input -> inputs.add(PhotonAnimationInput.read(input.write())));
	}

	@Override
	public PhotonChildAnimationClip copy() {
		var result = new PhotonChildAnimationClip(start(), duration(), speed());
		result.targetId(targetId()).seed(seed()).randomSeed(randomSeed());
		result.animation = animation;
		result.mode = mode;
		result.completion = completion;
		result.playbackDuration = playbackDuration;
		inputs.forEach(input -> result.inputs.add(PhotonAnimationInput.read(input.write())));
		return result;
	}
}
