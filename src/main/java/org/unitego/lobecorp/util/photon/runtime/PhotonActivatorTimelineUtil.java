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
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationRuntimeAccess;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationTimelineState;
import org.jetbrains.annotations.Nullable;

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
	/// Photon 原生激活行为，用于判断是否需要扩展处理，与编辑器默认值独立。
	private static final PhotonActivatorOptions NATIVE_OPTIONS = new PhotonActivatorOptions(true, PhotonActivatorOptions.StartBehavior.CONTINUE, true, PhotonActivatorOptions.EndBehavior.ORIGINAL, true);
	private final Map<Track, Boundary> started = new HashMap<>();
	private final Map<Track, Boundary> cleared = new HashMap<>();
	/// 无命名控制器时，只还原本播放器接管过的发射状态，避免未到点覆盖外部停止状态。
	private final Map<PhotonEmissionControlAccess, Boolean> emissionBases = new HashMap<>();
	/// 单边点的本地激活快照；倒退到点前或移除点时恢复，原生区间和控制轨道仍优先。
	private final Map<FXObject, ObjectBase> activationBases = new HashMap<>();
	private boolean hadExtendedControl;

	public static PhotonActivatorOptions options(Track track) {
		return ((PhotonActivatorTrackAccess) track).lobecorp$getActivationOptions();
	}

	/// 原生区间求值只负责同时控制开始和结束的激活轨道。
	public static boolean nativeInterval(Track track) {
		return track instanceof ActivatorTrack && options(track).intervalControl();
	}

	/// 单边命令到达有效点后才取得所有权，未到点时不覆盖其他时间轴的状态。
	public static boolean controlsAt(Track track, double time) {
		if (!(track instanceof ActivatorTrack) || track.mute()) {
			return false;
		}

		var options = options(track);
		return options.intervalControl() || options.pointControl() && latestPoint(track, time, options) != null;
	}

	/// 返回经过的有效入口或单边点；区间重叠只进入一次，只结束模式使用片段终点。
	public static List<Double> crossedEntries(Timeline timeline, double previousTime, double time) {
		if (!Double.isFinite(previousTime) || !Double.isFinite(time) || time <= previousTime) {
			return List.of();
		}

		var entries = new TreeSet<Double>();

		for (var track : timeline.leafTracks(false)) {
			if (track.mute() || !(track instanceof ActivatorTrack)) {
				continue;
			}

			var options = options(track);

			if (!options.startControl() && !options.endControl()) {
				continue;
			}

			for (var clip : track.clips()) {
				var point = options.startControl() ? clip.start() : clip.end();

				if (!validClip(clip, options.pointControl()) || point <= previousTime || point > time) {
					continue;
				}

				if (options.pointControl() || track.clipAt(Math.nextDown(point)) == null) {
					entries.add(point);
				}
			}
		}

		return List.copyOf(entries);
	}

	public void reset() {
		started.clear();
		cleared.clear();
		emissionBases.forEach(PhotonEmissionControlAccess::lobecorp$setEmissionStopped);
		emissionBases.clear();
		activationBases.forEach((object, base) -> base.restore(object));
		activationBases.clear();
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

	public void evaluate(FXRuntime runtime, Timeline timeline, double time, BiConsumer<FXObject, Clip> restart) {
		var tracks = timeline.leafTracks(false);
		var coordination = coordination(runtime);
		var owned = new HashSet<PhotonEmissionControlAccess>();
		collectOwnership(runtime, tracks, time, coordination, owned);
		boolean extended = tracks.stream().filter(track -> !track.mute() && track instanceof ActivatorTrack)
				.anyMatch(track -> !options(track).equals(NATIVE_OPTIONS));

		if (!extended && !hadExtendedControl) {
			if (coordination != null) {
				coordination.emission(owned, Set.of());
			}

			return;
		}

		hadExtendedControl = extended;
		var tracksByTarget = new HashMap<UUID, List<Track>>();

		for (var track : tracks) {
			if (controlsAt(track, time) && track.targetId() != null) {
				tracksByTarget.computeIfAbsent(track.targetId(), _ -> new ArrayList<>()).add(track);
			}
		}

		var blocked = new HashSet<PhotonEmissionControlAccess>();
		var seen = new HashSet<Track>();
		var pointOwners = new HashSet<FXObject>();

		for (var entry : tracksByTarget.entrySet()) {
			var activators = entry.getValue();

			if (activators.stream().allMatch(track -> options(track).equals(NATIVE_OPTIONS))) {
				continue;
			}

			if (!(runtime.objects.get(entry.getKey()) instanceof FXObject object)) {
				continue;
			}

			seen.addAll(activators);

			if (coordination == null && activators.stream().anyMatch(track -> options(track).pointControl())) {
				pointOwners.add(object);
				activationBases.putIfAbsent(object, new ObjectBase(object.isSelfActive(), object.isSelfTimelineVisible()));
			}

			evaluateObject(object, timeline, tracks, activators, time, coordination, blocked, restart);
		}

		started.keySet().retainAll(seen);
		cleared.keySet().retainAll(seen);

		if (coordination != null) {
			coordination.emission(owned, blocked);
			return;
		}

		restoreReleasedActivation(tracks, pointOwners);
		applyEmission(owned, blocked);
	}

	private void restoreReleasedActivation(List<Track> tracks, Set<FXObject> pointOwners) {
		activationBases.entrySet().removeIf(entry -> {
			var object = entry.getKey();

			if (pointOwners.contains(object)) {
				return false;
			}

			if (!nativeControlsObject(tracks, object.id())) {
				entry.getValue().restore(object);
			}

			return true;
		});
	}

	private static boolean nativeControlsObject(List<Track> tracks, UUID target) {
		for (var track : tracks) {
			if (track.mute()) {
				continue;
			}

			if (nativeInterval(track) && target.equals(track.targetId())
					|| track instanceof ControlTrack control && control.clips().stream().anyMatch(clip -> target.equals(clip.targetId()))) {
				return true;
			}
		}

		return false;
	}

	private void applyEmission(Set<PhotonEmissionControlAccess> owned, Set<PhotonEmissionControlAccess> blocked) {
		emissionBases.entrySet().removeIf(entry -> {
			if (owned.contains(entry.getKey())) {
				return false;
			}

			entry.getKey().lobecorp$setEmissionStopped(entry.getValue());
			return true;
		});

		for (var emitter : owned) {
			emissionBases.putIfAbsent(emitter, emitter.lobecorp$isEmissionStopped());
			emitter.lobecorp$setEmissionStopped(blocked.contains(emitter));
		}
	}

	private void evaluateObject(FXObject object, Timeline timeline, List<Track> tracks, List<Track> activators,
	                            double time, @Nullable PhotonAnimationTimelineState coordination,
	                            Set<PhotonEmissionControlAccess> blocked, BiConsumer<FXObject, Clip> restart) {
		var states = new HashMap<Track, ActivationState>();
		activators.forEach(track -> states.put(track, resolve(track, time)));
		var result = combine(activators, states);
		applyClears(object, timeline, activators, states, result.command(), coordination);
		applyRestarts(object, timeline, activators, states, result.command(), coordination, restart);
		boolean hasControl = false;
		boolean controlActive = false;

		for (var track : tracks) {
			if (!track.mute() && track instanceof ControlTrack control) {
				hasControl |= control.clips().stream().anyMatch(clip -> object.id().equals(clip.targetId()));
				controlActive |= control.clipForObjectAt(object.id(), time) != null;
			}
		}

		boolean ticking = hasControl ? controlActive : result.active();
		object.setSelfActive(ticking);
		object.setSelfTimelineVisible(ticking && result.active());

		if (result.active() && !result.emitting()) {
			collectInheritedEmissionStops(object.transform(), blocked);
		}
	}

	/// 普通区间沿用 OR；最新单边命令覆盖持续区间，后续真实区间入口才可恢复。
	private static TargetState combine(List<Track> activators, Map<Track, ActivationState> states) {
		boolean active = false;
		boolean emitting = false;
		Track command = null;
		Boundary latest = null;

		for (var track : activators) {
			var state = states.get(track);

			if (options(track).intervalControl()) {
				active |= state.active();
				emitting |= state.active() && !state.stopped();
			}

			var event = state.event();

			if (event != null && (latest == null || event.time() >= latest.time())) {
				command = track;
				latest = event;
			}
		}

		if (command != null && options(command).pointControl()) {
			var state = states.get(command);
			active = state.active();
			emitting = active && !state.stopped();
		}

		return new TargetState(active, emitting, command);
	}

	private void applyClears(FXObject object, Timeline timeline, List<Track> activators,
	                         Map<Track, ActivationState> states, @Nullable Track command,
	                         @Nullable PhotonAnimationTimelineState coordination) {
		for (var track : activators) {
			var boundary = states.get(track).clear();

			if (boundary == null) {
				cleared.remove(track);
				continue;
			}

			if (boundary.equals(cleared.get(track))) {
				continue;
			}

			boolean otherActive = activators.stream().filter(other -> other != track && options(other).intervalControl())
					.anyMatch(other -> resolve(other, boundary.time()).active());

			if (allowsBoundary(track, command) && (options(track).pointControl() || !otherActive)
					&& (coordination == null || coordination.allowsReset(timeline, object.id()))) {
				var particles = new HashSet<PhotonEmissionControlAccess>();
				collect(object.transform(), particles);
				particles.forEach(PhotonEmissionControlAccess::lobecorp$clearParticles);
			}

			cleared.put(track, boundary);
		}
	}

	private void applyRestarts(FXObject object, Timeline timeline, List<Track> activators,
	                           Map<Track, ActivationState> states, @Nullable Track command,
	                           @Nullable PhotonAnimationTimelineState coordination, BiConsumer<FXObject, Clip> restart) {
		boolean restarted = false;

		for (var track : activators) {
			var boundary = states.get(track).start();

			if (boundary == null) {
				started.remove(track);
				continue;
			}

			if (boundary.equals(started.get(track))) {
				continue;
			}

			if (!restarted && allowsBoundary(track, command)
					&& (coordination == null || coordination.allowsReset(timeline, object.id()))) {
				restart(object, options(track).clearOnRestart(), () -> restart.accept(object, boundary.clip()));
				restarted = true;
			}

			started.put(track, boundary);
		}
	}

	private static boolean allowsBoundary(Track track, @Nullable Track command) {
		if (options(track).pointControl()) {
			return track == command;
		}

		return command == null || !options(command).pointControl();
	}

	private static void collectOwnership(FXRuntime runtime, List<Track> tracks,
	                                     double time,
	                                     @Nullable PhotonAnimationTimelineState coordination,
	                                     Set<PhotonEmissionControlAccess> owned) {
		var ownActivations = new HashSet<UUID>();

		for (var track : tracks) {
			if (controlsAt(track, time) && track.targetId() != null) {
				ownActivations.add(track.targetId());
			}
		}

		if (coordination == null) {
			for (var object : runtime.objects.values()) {
				if (object instanceof PhotonActivationOverrideAccess access) {
					access.lobecorp$setOwnActivation(ownActivations.contains(object.id()));
				}
			}
		}

		for (var id : ownActivations) {
			if (runtime.objects.get(id) instanceof FXObject object) {
				collectInheritedEmissionStops(object.transform(), owned);
			}
		}
	}

	@SuppressWarnings("resource") // FXRuntime 拥有控制器生命周期，此处只借用状态协调器。
	private static @Nullable PhotonAnimationTimelineState coordination(FXRuntime runtime) {
		if (!(runtime instanceof PhotonAnimationRuntimeAccess access)) {
			return null;
		}

		var animations = access.lobecorp$animations();

		if (animations == null) {
			return null;
		}

		var state = animations.timelineState();
		return state.coordinating() ? state : null;
	}

	private static ActivationState resolve(Track track, double time) {
		var options = options(track);

		if (options.pointControl()) {
			return resolvePoint(track, time, options);
		}

		var boundaries = new TreeSet<Double>();

		for (var clip : track.clips()) {
			if (validClip(clip, false)) {
				boundaries.add(clip.start());
				boundaries.add(clip.end());
			}
		}

		if (boundaries.isEmpty()) {
			return new ActivationState(false, false, null, null, null);
		}

		boolean active = false;
		boolean stopped = false;
		Boundary start = null;
		Boundary clear = null;
		Boundary event = null;
		Clip previous = null;

		for (double boundary : boundaries) {
			if (boundary > time) {
				break;
			}

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
				event = new Boundary(next, boundary);

				if (options.startBehavior() == PhotonActivatorOptions.StartBehavior.RESTART) {
					start = new Boundary(next, boundary);
				}
			}

			previous = next;
		}

		return new ActivationState(active, stopped, start, clear, event);
	}

	private static ActivationState resolvePoint(Track track, double time, PhotonActivatorOptions options) {
		var point = latestPoint(track, time, options);

		if (point == null) {
			return new ActivationState(false, false, null, null, null);
		}

		if (options.startControl()) {
			var restart = options.startBehavior() == PhotonActivatorOptions.StartBehavior.RESTART ? point : null;
			return new ActivationState(true, false, restart, null, point);
		}

		return switch (options.endBehavior()) {
			case ORIGINAL -> new ActivationState(false, false, null, null, point);
			case STOP_EMISSION -> new ActivationState(true, true, null, null, point);
			case CLEAR -> new ActivationState(false, true, null, point, point);
		};
	}

	private static @Nullable Boundary latestPoint(Track track, double time, PhotonActivatorOptions options) {
		Boundary latest = null;

		for (var clip : track.clips()) {
			var point = options.startControl() ? clip.start() : clip.end();

			if (!validClip(clip, true) || point > time || !Double.isFinite(time)) {
				continue;
			}

			if (latest == null || point > latest.time()) {
				latest = new Boundary(clip, point);
			}
		}

		return latest;
	}

	private static boolean validClip(Clip clip, boolean point) {
		return Double.isFinite(clip.start()) && Double.isFinite(clip.duration()) && Double.isFinite(clip.end())
				&& (point ? clip.duration() >= 0 : clip.duration() > 0);
	}

	private static void collect(Transform transform, Set<PhotonEmissionControlAccess> result) {
		if (transform.sceneObject() instanceof PhotonEmissionControlAccess emitter) {
			result.add(emitter);
		}

		for (var child : transform.children()) {
			collect(child, result);
		}
	}

	private static void collectInheritedEmissionStops(Transform transform, Set<PhotonEmissionControlAccess> result) {
		if (transform.sceneObject() instanceof PhotonEmissionControlAccess emitter) {
			result.add(emitter);
		}

		for (var child : transform.children()) {
			if (child.sceneObject() instanceof PhotonActivationOverrideAccess access && access.lobecorp$hasOwnActivation()) {
				continue;
			}
			collectInheritedEmissionStops(child, result);
		}
	}

	private record Boundary(Clip clip, double time) {
	}

	private record ActivationState(boolean active, boolean stopped, @Nullable Boundary start,
	                               @Nullable Boundary clear, @Nullable Boundary event) {
	}

	private record TargetState(boolean active, boolean emitting, @Nullable Track command) {
	}

	private record ObjectBase(boolean active, boolean visible) {
		private void restore(FXObject object) {
			object.setSelfActive(active);
			object.setSelfTimelineVisible(visible);
		}
	}
}
