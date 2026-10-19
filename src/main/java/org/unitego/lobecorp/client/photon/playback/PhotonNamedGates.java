package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.ControlTrack;
import com.lowdragmc.photon.client.fx.timeline.TimelineState;
import com.lowdragmc.photon.client.gameobject.FXObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 统一收集命名轨道的最终激活者；只有胜出的控制片段能够重置子树。
public class PhotonNamedGates {
	private record State(boolean active, boolean visible) {
		static State read(FXObject object) {
			return new State(object.isSelfActive(), object.isSelfTimelineVisible());
		}

		void apply(FXObject object) {
			object.setSelfActive(active);
			object.setSelfTimelineVisible(visible);
		}
	}

	private record Control(UUID handle, Clip clip, long loop) {
	}

	private record Selection(boolean active, boolean visible, Control control) {
	}

	private final FXRuntime runtime;
	private final Map<FXObject, State> before = new LinkedHashMap<>();
	private final Map<FXObject, State> installed = new LinkedHashMap<>();
	private final Map<FXObject, Selection> selected = new LinkedHashMap<>();
	private final Map<FXObject, Control> previousControls = new LinkedHashMap<>();

	public PhotonNamedGates(FXRuntime runtime) {
		this.runtime = runtime;
	}

	public void begin() {
		installed.forEach((object, value) -> {
			if (value.equals(State.read(object))) {
				before.get(object).apply(object);
			}
		});
		before.clear();
		installed.clear();
		selected.clear();
	}

	void collect(PhotonNamedPlan plan, PhotonNamedHandle handle, double time) {
		var ids = new LinkedHashMap<UUID, Boolean>();

		for (var track : plan.leaves) {
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
			if (!(runtime.objects.get(id) instanceof FXObject target)) {
				throw new IllegalArgumentException("命名激活目标不存在：" + id);
			}

			boolean hasActivator = false;
			boolean active = false;
			boolean hasControl = false;
			Clip control = null;

			for (var track : plan.leaves) {
				if (track.mute()) {
					continue;
				}

				if (track instanceof ActivatorTrack && id.equals(track.targetId())) {
					hasActivator = true;
					active |= track.clipAt(time) != null;
				} else if (track instanceof ControlTrack controls) {
					hasControl |= controls.clips().stream().anyMatch(clip -> id.equals(clip.targetId()));

					if (control == null) {
						control = controls.clipForObjectAt(id, time);
					}
				}
			}

			var state = TimelineState.resolve(hasActivator, active, hasControl, control != null);
			selected.put(target, new Selection(state.tick(), state.render(),
					control == null ? null : new Control(handle.id(), control, handle.loops(0))));
		}
	}

	public void commit() {
		var next = new LinkedHashMap<FXObject, Control>();

		for (var entry : selected.entrySet()) {
			var target = entry.getKey();
			var selection = entry.getValue();
			var control = selection.control();

			if (control != null) {
				next.put(target, control);

				if (!control.equals(previousControls.get(target))) {
					var executor = ((FXObject) runtime.root).getEffectExecutor();
					long seed = control.clip().randomSeed() && executor != null
							? executor.getRandomSource().nextLong() : control.clip().seed();
					reset(target, seed);
				}
			}

			before.put(target, State.read(target));
			var value = new State(selection.active(), selection.visible());
			value.apply(target);
			installed.put(target, value);
		}

		previousControls.clear();
		previousControls.putAll(next);
	}

	private static void reset(FXObject target, long seed) {
		target.reset();
		target.setRandomSeed(seed);

		for (var child : List.copyOf(target.transform().children())) {
			if (child.sceneObject() instanceof FXObject object) {
				reset(object, seed);
			}
		}
	}

	public void clear() {
		begin();
		previousControls.clear();
	}
}
