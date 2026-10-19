package org.unitego.lobecorp.client.photon.playback.timeline;

import com.lowdragmc.photon.client.fx.timeline.Clip;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Input;

import java.util.List;
import java.util.UUID;

/// 父轨道窗口与独立播放时长分别保存，受控子片段不借用独立时钟。
public class PhotonChildClip extends Clip {
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
	public double playbackDuration = -1;
	public List<Input> inputs = List.of();
	private CompoundTag source = new CompoundTag();

	public PhotonChildClip(double start, double duration, float speed) {
		super(start, duration, speed);
	}

	public void source(CompoundTag value) {
		source = value.copy();
	}

	public CompoundTag source() {
		return source.copy();
	}

	public void validate() {
		if (!Double.isFinite(start()) || start() < 0 || !Double.isFinite(duration()) || duration() < 0
				|| !Double.isFinite(end()) || !Float.isFinite(speed()) || speed() < 0
				|| !Double.isFinite(playbackDuration) || playbackDuration < 0 && playbackDuration != -1) {
			throw new IllegalArgumentException("子动画片段窗口或播放时长无效。");
		}
	}

	public double windowProgress(double time) {
		return duration() == 0 ? 1 : Math.clamp((time - start()) / duration(), 0, 1);
	}

	@Override
	public PhotonChildClip copy() {
		var result = new PhotonChildClip(start(), duration(), speed());
		result.targetId(targetId()).seed(seed()).randomSeed(randomSeed());
		result.id = id;
		result.animation = animation;
		result.mode = mode;
		result.completion = completion;
		result.playbackDuration = playbackDuration;
		result.inputs = List.copyOf(inputs);
		result.source = source.copy();
		return result;
	}
}
