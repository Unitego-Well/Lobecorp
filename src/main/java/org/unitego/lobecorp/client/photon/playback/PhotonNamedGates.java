package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.TimelineState;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.unitego.lobecorp.util.photon.playback.PhotonActivationStateUtil;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Set;
import java.util.HashSet;
import javax.annotation.Nullable;

/// 统一收集命名轨道的最终激活者；只有胜出的控制片段能够重置子树。
public class PhotonNamedGates {
	private record State(boolean active, boolean visible, @Nullable Object owner, boolean stopped) {
		private static State read(FXObject object) {
			return new State(object.isSelfActive(), object.isSelfTimelineVisible(),
					((PhotonActivationOverrideAccess) object).lobecorp$activationOwner(),
					object instanceof PhotonEmissionAccess emission && emission.lobecorp$emissionStopped());
		}

		private void apply(FXObject object) {
			object.setSelfActive(active);
			object.setSelfTimelineVisible(visible);
			((PhotonActivationOverrideAccess) object).lobecorp$activationOwner(owner);

			if (object instanceof PhotonEmissionAccess emission) {
				emission.lobecorp$emissionStopped(stopped);
			}
		}
	}

	private record Control(UUID handle, Clip clip, long loop, double boundary, boolean clear) {
	}

	private record Selection(boolean active, boolean visible, boolean stopped, Control restart, Control clear) {
	}

	private record Activation(PhotonActivationStateUtil.State state, Control restart, Control clear) {
	}

	private record Signals(boolean active, boolean stopped, Control restart, Control clear) {
	}

	private final FXRuntime runtime;
	private final boolean named;
	private final Map<FXObject, State> before = new LinkedHashMap<>();
	private final Map<FXObject, State> installed = new LinkedHashMap<>();
	private final Map<FXObject, Selection> selected = new LinkedHashMap<>();
	private final Map<FXObject, Control> previousControls = new LinkedHashMap<>();
	private final Map<FXObject, Control> previousClears = new LinkedHashMap<>();
	private final Map<Track, PhotonActivationStateUtil.Prepared> prepared = new WeakHashMap<>();
	private final Set<Control> seenRestarts = new HashSet<>();
	private final Set<Control> seenClears = new HashSet<>();
	private final Set<Control> nextRestarts = new HashSet<>();
	private final Set<Control> nextClears = new HashSet<>();
	private final Map<FXObject, State> nativeBases = new LinkedHashMap<>();
	private final Set<UUID> previousNativeTargets = new HashSet<>();

	public PhotonNamedGates(FXRuntime runtime) {
		this(runtime, true);
	}

	public PhotonNamedGates(FXRuntime runtime, boolean named) {
		this.runtime = runtime;
		this.named = named;
	}

	public boolean named() {
		return named;
	}

	public void begin() {
		installed.forEach((object, value) -> {
			var current = State.read(object);
			if (current.owner() != value.owner()) {
				return;
			}
			var base = nativeBases.getOrDefault(object, before.get(object));
			new State(current.active() == value.active() ? base.active() : current.active(),
					current.visible() == value.visible() ? base.visible() : current.visible(),
					base.owner(),
					current.stopped() == value.stopped() ? base.stopped() : current.stopped()).apply(object);
		});
		before.clear();
		installed.clear();
		selected.clear();
		nextRestarts.clear();
		nextClears.clear();
	}

	public void collect(PhotonNamedPlan plan, PhotonNamedHandle handle, double time) {
		collect(plan.leaves, handle.id(), handle.loops(0), time, true);
	}

	/// 原生求值后记录当帧基底，命名贡献释放时不恢复成上一帧的激活状态。
	public void captureNativeState(Set<UUID> ids, @Nullable PhotonNamedGates nativeGates) {
		var current = new HashSet<>(ids);
		if (nativeGates != null) {
			nativeGates.selected.keySet().forEach(target -> current.add(target.id()));
		}

		var targets = new HashSet<>(previousNativeTargets);
		targets.addAll(current);
		previousNativeTargets.clear();
		previousNativeTargets.addAll(current);
		nativeBases.clear();

		for (var id : targets) {
			if (runtime.objects.get(id) instanceof FXObject target) {
				var saved = before.getOrDefault(target, State.read(target));
				var owner = saved.owner() instanceof PhotonNamedGates ? null : saved.owner();
				nativeBases.put(target, new State(target.isSelfActive(), target.isSelfTimelineVisible(), owner, saved.stopped()));
			}
		}

		if (nativeGates != null) {
			nativeGates.selected.forEach((target, value) -> nativeBases.put(target,
					new State(value.active(), value.visible(), nativeGates, value.stopped())));
		}
	}

	public void collect(List<Track> leaves, UUID owner, long loop, double time, boolean resetControls) {
		var ids = new LinkedHashMap<UUID, Boolean>();

		for (var track : leaves) {
			if (track.mute()) {
				continue;
			}

			if (track instanceof ActivatorTrack && track.targetId() != null) {
				ids.put(track.targetId(), true);
			} else if (track instanceof ControlTrack) {
				track.clips().forEach(clip -> {
					if (clip.targetId() != null) {
						ids.put(clip.targetId(), true);
					}
				});
			}
		}

		for (var id : ids.keySet()) {
			collectTarget(leaves, owner, loop, time, resetControls, id);
		}
	}

	private void collectTarget(List<Track> leaves, UUID owner, long loop, double time, boolean resetControls, UUID id) {
		if (!(runtime.objects.get(id) instanceof FXObject target)) {
			throw new IllegalArgumentException("命名激活目标不存在：" + id);
		}

		boolean hasActivator = false;
		boolean hasControl = false;
		Clip control = null;
		var activations = new LinkedHashMap<Track, Activation>();

		for (var track : leaves) {
			if (track.mute()) {
				continue;
			}

			if (track instanceof ActivatorTrack && id.equals(track.targetId())) {
				var sampled = activation(track, owner, loop, time);
				var activation = sampled.state();

				if (!activation.owned()) {
					continue;
				}

				hasActivator = true;
				activations.put(track, sampled);
			} else if (track instanceof ControlTrack controls) {
				hasControl |= controls.clips().stream().anyMatch(clip -> id.equals(clip.targetId()));

				if (control == null) {
					control = controls.clipForObjectAt(id, time);
				}
			}
		}

		var signals = signals(activations, owner, loop);
		var state = TimelineState.resolve(hasActivator, signals.active(), hasControl, control != null);

		if (!hasActivator && !hasControl) {
			return;
		}

		var restart = control != null ? resetControls ? new Control(owner, control, loop, control.start(), true) : null : signals.restart();
		selected.put(target, new Selection(state.tick(), state.render(), hasActivator && signals.stopped(), restart, signals.clear()));
	}

	private Signals signals(Map<Track, Activation> activations, UUID owner, long loop) {
		var tracks = List.copyOf(activations.keySet());
		var contributions = tracks.stream().map(track -> new PhotonActivationStateUtil.Contribution(
				((PhotonActivationTrackAccess) track).lobecorp$activationOptions(), activations.get(track).state())).toList();
		var combined = PhotonActivationStateUtil.combine(contributions);
		boolean point = combined.command() >= 0 && contributions.get(combined.command()).options().pointControl();
		Control restart = null;
		Control clear = null;

		for (int index = 0; index < tracks.size(); index++) {
			var track = tracks.get(index);
			var sampled = activations.get(track);
			if (sampled.restart() != null) {
				nextRestarts.add(sampled.restart());
			}
			if (sampled.clear() != null) {
				nextClears.add(sampled.clear());
			}
			boolean allowed = contributions.get(index).options().pointControl() ? index == combined.command() : !point;
			if (!allowed) {
				continue;
			}

			if (restart == null && sampled.restart() != null && !seenRestarts.contains(sampled.restart())) {
				restart = sampled.restart();
			}

			if (clear == null && sampled.clear() != null && !seenClears.contains(sampled.clear())) {
				boolean otherActive = !contributions.get(index).options().pointControl() && tracks.stream().anyMatch(other -> {
					var options = ((PhotonActivationTrackAccess) other).lobecorp$activationOptions();
					return other != track && options.startControl() && options.endControl()
							&& activation(other, owner, loop, sampled.clear().boundary()).state().active();
				});
				if (!otherActive) {
					clear = sampled.clear();
				}
			}
		}

		return new Signals(combined.active(), combined.active() && !combined.emitting(), restart, clear);
	}

	private Activation activation(Track track, UUID owner, long loop, double time) {
		var windows = prepared.get(track);
		boolean changed = windows == null || windows.windows().size() != track.clips().size();

		if (!changed) {
			for (int index = 0; index < track.clips().size(); index++) {
				var clip = track.clips().get(index);
				var before = windows.windows().get(index);
				if (clip.start() != before.start() || clip.end() != before.end()) {
					changed = true;
					break;
				}
			}
		}

		if (changed) {
			var ranges = new ArrayList<PhotonActivationStateUtil.Window>();
			for (int index = 0; index < track.clips().size(); index++) {
				var clip = track.clips().get(index);
				ranges.add(new PhotonActivationStateUtil.Window(clip.start(), clip.end(), index));
			}
			windows = PhotonActivationStateUtil.prepare(ranges);
			prepared.put(track, windows);
		}

		var options = ((PhotonActivationTrackAccess) track).lobecorp$activationOptions();
		var state = PhotonActivationStateUtil.evaluate(windows, options, time);
		var restart = state.restart() == null ? null : new Control(owner, track.clips().get(state.restart().index()), loop,
				state.restart().time(), options.clearOnRestart());
		var clear = state.clear() == null ? null : new Control(owner, track.clips().get(state.clear().index()), loop,
				state.clear().time(), true);
		return new Activation(state, restart, clear);
	}

	public void commit() {
		var next = new LinkedHashMap<FXObject, Control>();
		var clears = new LinkedHashMap<FXObject, Control>();

		for (var entry : selected.entrySet()) {
			var target = entry.getKey();
			var selection = entry.getValue();
			var owner = ((PhotonActivationOverrideAccess) target).lobecorp$activationOwner();
			if (owner instanceof PhotonNamedGates other && other != this && other.named) {
				continue;
			}
			var control = selection.restart();
			var clear = selection.clear();
			before.putIfAbsent(target, State.read(target));
			boolean needsRestart = control != null && !control.equals(previousControls.get(target));
			boolean needsClear = clear != null && !clear.equals(previousClears.get(target));

			if (needsClear) {
				clear(target);
			}

			if (control != null) {
				next.put(target, control);

				if (needsRestart) {
					var executor = ((FXObject) runtime.root).getEffectExecutor();
					long seed = control.clip().randomSeed() && executor != null
							? executor.getRandomSource().nextLong() : control.clip().seed();
					reset(target, seed, control.clear());
				}
			}

			if (clear != null) {
				clears.put(target, clear);
			}
			var value = new State(selection.active(), selection.visible(), this, selection.stopped());
			value.apply(target);
			installed.put(target, value);
			emission(target, selection.stopped());
		}

		previousControls.clear();
		previousControls.putAll(next);
		previousClears.clear();
		previousClears.putAll(clears);
		seenRestarts.clear();
		seenRestarts.addAll(nextRestarts);
		seenClears.clear();
		seenClears.addAll(nextClears);
	}

	private void reset(FXObject target, long seed, boolean clear) {
		before.putIfAbsent(target, State.read(target));
		if (target instanceof PhotonEmissionAccess emission) {
			if (clear) {
				emission.lobecorp$clearParticles();
			}
			emission.lobecorp$preserveParticles(!clear);
		}

		try {
			target.reset();
		} finally {
			if (target instanceof PhotonEmissionAccess emission) {
				emission.lobecorp$preserveParticles(false);
			}
		}
		target.setRandomSeed(seed);
		installed.put(target, State.read(target));

		for (var child : List.copyOf(target.transform().children())) {
			if (child.sceneObject() instanceof FXObject object && inherited(object)) {
				reset(object, seed, clear);
			}
		}
	}

	public void clear() {
		begin();
		previousControls.clear();
		previousClears.clear();
		seenRestarts.clear();
		seenClears.clear();
		nativeBases.clear();
		previousNativeTargets.clear();
		prepared.clear();
	}

	private void clear(FXObject target) {
		if (target instanceof PhotonEmissionAccess emission) {
			emission.lobecorp$clearParticles();
		}

		for (var child : List.copyOf(target.transform().children())) {
			if (child.sceneObject() instanceof FXObject object && inherited(object)) {
				clear(object);
			}
		}
	}

	private void emission(FXObject target, boolean stopped) {
		if (target instanceof PhotonEmissionAccess) {
			before.putIfAbsent(target, State.read(target));
			var base = State.read(target);
			var value = new State(base.active(), base.visible(), base.owner(), stopped);
			value.apply(target);
			installed.put(target, value);
		}

		for (var child : List.copyOf(target.transform().children())) {
			if (child.sceneObject() instanceof FXObject object && inherited(object)) {
				emission(object, stopped);
			}
		}
	}

	private boolean inherited(FXObject object) {
		return !selected.containsKey(object) && !((PhotonActivationOverrideAccess) object).lobecorp$ownActivation();
	}
}
