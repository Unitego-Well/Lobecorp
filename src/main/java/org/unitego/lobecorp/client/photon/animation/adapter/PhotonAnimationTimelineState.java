package org.unitego.lobecorp.client.photon.animation.adapter;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.SpeedTrack;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivationOverrideAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionControlAccess;
import org.unitego.lobecorp.util.photon.runtime.PhotonActivatorTimelineUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/// 原 TL 与命名 TL 共享对象时，统一还原状态和子激活标记，轨道仍由原播放器执行。
public class PhotonAnimationTimelineState {
	private record Base(boolean active, boolean visible, float speed) {
		private void restore(FXObject target) {
			target.setSelfActive(active);
			target.setSelfTimelineVisible(visible);
			target.setSelfTimeScale(speed);
		}
	}

	private final FXRuntime runtime;
	private final Map<FXObject, Base> bases = new HashMap<>();
	private final Map<PhotonEmissionControlAccess, Boolean> emissions = new HashMap<>();
	private final Map<UUID, Timeline> activationOwners = new HashMap<>();
	private boolean coordinating;

	public PhotonAnimationTimelineState(FXRuntime runtime) {
		this.runtime = runtime;
	}

	public void begin(List<Timeline> timelines, ToDoubleFunction<Timeline> sampleTime) {
		restore();
		ownership(timelines, sampleTime);
		coordinating = true;
	}

	@SuppressWarnings("IfCanBeSwitch") // 条件同时依赖轨道类型和绑定目标。
	public void ownership(List<Timeline> timelines, ToDoubleFunction<Timeline> sampleTime) {
		var own = new HashSet<UUID>();
		activationOwners.clear();

		for (var timeline : timelines) {
			for (var track : timeline.leafTracks(false)) {
				if (track.mute()) {
					continue;
				}

				if (track instanceof ActivatorTrack && track.targetId() != null
						&& PhotonActivatorTimelineUtil.controlsAt(track, sampleTime.applyAsDouble(timeline))) {
					own.add(track.targetId());
					activationOwners.put(track.targetId(), timeline);
					capture(track.targetId());
				} else if (track instanceof SpeedTrack && track.targetId() != null) {
					capture(track.targetId());
				} else if (track instanceof ControlTrack) {
					track.clips().forEach(clip -> {
						capture(clip.targetId());

						if (clip.targetId() != null) {
							activationOwners.put(clip.targetId(), timeline);
						}
					});
				}
			}
		}

		for (var object : runtime.objects.values()) {
			if (object instanceof PhotonActivationOverrideAccess access) {
				access.lobecorp$setOwnActivation(own.contains(object.id()));
			}
		}

	}

	private void capture(UUID id) {
		if (id != null && runtime.objects.get(id) instanceof FXObject target) {
			bases.computeIfAbsent(target, object -> new Base(object.isSelfActive(),
					object.isSelfTimelineVisible(), object.getSelfTimeScale()));
		}
	}

	public boolean coordinating() {
		return coordinating;
	}

	public boolean allowsReset(Timeline timeline, UUID target) {
		var owner = activationOwners.get(target);
		return !coordinating || owner == null || owner == timeline;
	}

	public void end() {
		coordinating = false;
	}

	public void emission(Set<PhotonEmissionControlAccess> owned, Set<PhotonEmissionControlAccess> blocked) {
		for (var emitter : owned) {
			emissions.putIfAbsent(emitter, emitter.lobecorp$isEmissionStopped());
			emitter.lobecorp$setEmissionStopped(blocked.contains(emitter));
		}
	}

	public void manualEmission(PhotonEmissionControlAccess emitter, boolean stopped) {
		if (emissions.containsKey(emitter)) {
			emissions.put(emitter, stopped);
		}

		emitter.lobecorp$setEmissionStopped(stopped);
	}

	public void restore() {
		bases.forEach((object, base) -> base.restore(object));
		emissions.forEach(PhotonEmissionControlAccess::lobecorp$setEmissionStopped);
	}

	public void close() {
		restore();
		bases.clear();
		emissions.clear();
		activationOwners.clear();

		for (var object : runtime.objects.values()) {
			if (object instanceof PhotonActivationOverrideAccess access) {
				access.lobecorp$setOwnActivation(false);
			}
		}

		coordinating = false;
	}
}
