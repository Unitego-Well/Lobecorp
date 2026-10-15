package org.unitego.lobecorp.config.photon.model;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import org.unitego.lobecorp.config.photon.emitter.model.PhotonModelEmitterConfig;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

/// 模型工程的默认实例播放设置，不受编辑器预览的临时配置影响。
public class PhotonModelPlaybackConfig implements IPersistedSerializable {
	@Persisted
	public boolean enabled = true;
	@Persisted
	public int duration = PhotonModelEmitterConfig.DEFAULT_DURATION;
	@Persisted
	public int startDelay;
	@Persisted
	public boolean looping = true;
	@Persisted
	public boolean paused;
	@Persisted
	public String clip = "";
	@Persisted
	public float playbackSpeed = PhotonModelEmitterConfig.DEFAULT_PLAYBACK_SPEED;
	@Persisted
	public float animationOffset;

	public static PhotonModelPlaybackConfig from(PhotonModelEmitterConfig config) {
		var result = new PhotonModelPlaybackConfig();
		result.enabled = config.enabled;
		result.duration = config.duration;
		result.startDelay = config.startDelay;
		result.looping = config.looping;
		result.paused = config.paused;
		result.clip = config.clip;
		result.playbackSpeed = config.playbackSpeed;
		result.animationOffset = config.animationOffset;
		return result;
	}

	public PhotonModelPlaybackConfig copy() {
		var result = new PhotonModelPlaybackConfig();
		result.enabled = enabled;
		result.duration = duration;
		result.startDelay = startDelay;
		result.looping = looping;
		result.paused = paused;
		result.clip = clip;
		result.playbackSpeed = playbackSpeed;
		result.animationOffset = animationOffset;
		return result;
	}

	public void applyTo(PhotonModelEmitterConfig config) {
		config.enabled = enabled;
		config.duration = duration;
		config.startDelay = startDelay;
		config.looping = looping;
		config.paused = paused;
		config.clip = clip;
		config.playbackSpeed = playbackSpeed;
		config.animationOffset = animationOffset;
	}

	public void validate() {
		if (duration < 1 || startDelay < 0 || !Float.isFinite(playbackSpeed) || playbackSpeed < 0
				|| !Float.isFinite(animationOffset) || clip == null) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("invalid_playback"));
		}
	}

	public void validate(PhotonModelAsset asset) {
		validate();

		if (!clip.isEmpty() && asset.clips.stream().noneMatch(current -> current.id.equals(clip))) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("invalid_default_clip"));
		}
	}
}

