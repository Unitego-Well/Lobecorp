package org.unitego.lobecorp.util.photon.runtime;

import com.lowdragmc.lowdraglib2.math.Transform;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorTrackAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorOptions;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionControlAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivationOverrideAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.UUID;

public class PhotonActivatorTimelineUtil {
	/**
	 * Photon 原生激活行为，用于判断是否需要扩展处理，与编辑器默认值独立。
	 */
	private static final PhotonActivatorOptions NATIVE_OPTIONS = new PhotonActivatorOptions(true, PhotonActivatorOptions.StartBehavior.CONTINUE, true, PhotonActivatorOptions.EndBehavior.ORIGINAL, true);
	private final Map<Track, Boundary> started = new HashMap<>();
	private final Map<Track, Boundary> cleared = new HashMap<>();
	private boolean hadExtendedControl;

	public static PhotonActivatorOptions options(Track track) {
		return ((PhotonActivatorTrackAccess) track).lobecorp$getActivationOptions();
	}

	public void reset() {
		started.clear();
		cleared.clear();
	}

	public static void restart(FXObject object, boolean clear, Runnable original) {
		if (clear) {
			var emitters = new HashSet<PhotonEmissionControlAccess>();
			collect(object.transform(), emitters);
			for (var emitter : emitters) {
				if (emitter instanceof PhotonEmitterSpawner spawner) {
					spawner.lobecorp$clearParticles();
				}
			}

			original.run();
			return;
		}
		var emitters = new HashSet<PhotonEmissionControlAccess>();
		collect(object.transform(), emitters);
		emitters.forEach(emitter -> emitter.lobecorp$setPreserveOnReset(true));
		try {
			original.run();
		} finally {
			emitters.forEach(emitter -> emitter.lobecorp$setPreserveOnReset(false));
		}
	}

	public void evaluate(FXRuntime runtime, Timeline timeline, long time, BiConsumer<FXObject, Clip> restart) {
		var tracks = timeline.leafTracks(false);
		var ownActivations = new HashSet<UUID>();
		for (var track : tracks) {
			if (!track.mute() && track instanceof ActivatorTrack && track.targetId() != null) {
				ownActivations.add(track.targetId());
			}
		}
		for (var object : runtime.objects.values()) {
			if (object instanceof PhotonActivationOverrideAccess access) {
				access.lobecorp$setOwnActivation(ownActivations.contains(object.transform().id()));
			}
		}
		boolean extended = tracks.stream().filter(track -> !track.mute() && track instanceof ActivatorTrack)
				.anyMatch(track -> !options(track).equals(NATIVE_OPTIONS));
		if (!extended && !hadExtendedControl)
			return;
		hadExtendedControl = extended;
		var tracksByTarget = new HashMap<UUID, List<Track>>();
		for (var track : tracks) {
			if (!track.mute() && track instanceof ActivatorTrack && track.targetId() != null) {
				tracksByTarget.computeIfAbsent(track.targetId(), ignored -> new ArrayList<>()).add(track);
			}
		}
		var blocked = new HashSet<PhotonEmissionControlAccess>();
		var seen = new HashSet<Track>();
		for (var entry : tracksByTarget.entrySet()) {
			var activators = entry.getValue();
			if (activators.stream().allMatch(track -> options(track).equals(NATIVE_OPTIONS)))
				continue;
			if (!(runtime.objects.get(entry.getKey()) instanceof FXObject object))
				continue;
			var states = new HashMap<Track, ActivationState>();
			boolean active = false;
			boolean emitting = false;
			for (var track : activators) {
				seen.add(track);
				var state = resolve(track, time);
				states.put(track, state);
				active |= state.active();
				emitting |= state.active() && !state.stopped();
			}
			for (var track : activators) {
				var boundary = states.get(track).clear();
				if (boundary == null) {
					cleared.remove(track);
				} else if (!boundary.equals(cleared.get(track))) {
					boolean otherActive = activators.stream().filter(other -> other != track)
							.anyMatch(other -> resolve(other, boundary.time()).active());
					if (!otherActive) {
						var particles = new HashSet<PhotonEmissionControlAccess>();
						collect(object.transform(), particles);
						particles.forEach(PhotonEmissionControlAccess::lobecorp$clearParticles);
					}
					cleared.put(track, boundary);
				}
			}
			boolean restarted = false;
			for (var track : activators) {
				var boundary = states.get(track).start();
				if (boundary == null) {
					started.remove(track);
				} else if (!boundary.equals(started.get(track))) {
					if (!restarted) {
						restart(object, options(track).clearOnRestart(), () -> restart.accept(object, boundary.clip()));
						restarted = true;
					}
					started.put(track, boundary);
				}
			}
			boolean hasControl = false;
			boolean controlActive = false;
			for (var track : tracks) {
				if (!track.mute() && track instanceof ControlTrack control) {
					hasControl |= control.clips().stream().anyMatch(clip -> entry.getKey().equals(clip.targetId()));
					controlActive |= control.clipForObjectAt(entry.getKey(), time) != null;
				}
			}
			boolean ticking = hasControl ? controlActive : active;
			object.setSelfActive(ticking);
			object.setSelfTimelineVisible(ticking && active);
			if (active && !emitting)
				collectInheritedEmissionStops(object.transform(), blocked);
		}
		started.keySet().retainAll(seen);
		cleared.keySet().retainAll(seen);
		for (var object : runtime.objects.values()) {
			if (object instanceof PhotonEmissionControlAccess emitter) {
				emitter.lobecorp$setEmissionStopped(blocked.contains(emitter));
			}
		}
	}

	private static ActivationState resolve(Track track, double time) {
		var options = options(track);
		var boundaries = new TreeSet<Double>();
		for (var clip : track.clips()) {
			if (clip.duration() > 0) {
				boundaries.add(clip.start());
				boundaries.add(clip.end());
			}
		}
		if (boundaries.isEmpty())
			return new ActivationState(false, false, null, null);
		boolean active = !options.startControl();
		boolean stopped = false;
		Boundary start = null;
		Boundary clear = null;
		Clip previous = null;
		for (double boundary : boundaries) {
			if (boundary > time)
				break;
			var next = track.clipAt(boundary);
			if ((next != null) == (previous != null)) {
				previous = next;
				continue;
			}
			if (previous != null && options.endControl()) {
				switch (options.endBehavior()) {
					case ORIGINAL -> {
						active = false;
						stopped = false;
					}
					case STOP_EMISSION -> {
						active = true;
						stopped = true;
					}
					case CLEAR -> {
						active = false;
						stopped = true;
						clear = new Boundary(previous, boundary);
					}
				}
			}
			if (next != null && options.startControl()) {
				active = true;
				stopped = false;
				if (options.startBehavior() == PhotonActivatorOptions.StartBehavior.RESTART) {
					start = new Boundary(next, boundary);
				}
			}
			previous = next;
		}
		return new ActivationState(active, stopped, start, clear);
	}

	private static void collect(Transform transform, Set<PhotonEmissionControlAccess> result) {
		if (transform.sceneObject() instanceof PhotonEmissionControlAccess emitter)
			result.add(emitter);
		for (var child : transform.children())
			collect(child, result);
	}

	private static void collectInheritedEmissionStops(Transform transform, Set<PhotonEmissionControlAccess> result) {
		if (transform.sceneObject() instanceof PhotonEmissionControlAccess emitter)
			result.add(emitter);
		for (var child : transform.children()) {
			if (child.sceneObject() instanceof PhotonActivationOverrideAccess access && access.lobecorp$hasOwnActivation())
				continue;
			collectInheritedEmissionStops(child, result);
		}
	}

	private record Boundary(Clip clip, double time) {
	}

	private record ActivationState(boolean active, boolean stopped, Boundary start, Boundary clear) {
	}
}
