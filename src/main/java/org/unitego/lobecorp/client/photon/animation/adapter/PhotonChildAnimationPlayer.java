package org.unitego.lobecorp.client.photon.animation.adapter;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationPlayback;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationClip;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationTrack;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonAnimationCallTrack;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUtil;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/// 原生播放器的子动画片段桥；只操作发射器已有实例，不创建新的播放器或子特效。
public class PhotonChildAnimationPlayer {
	private record Child(WeakReference<PhotonAnimationRuntime> runtime,
	                     WeakReference<PhotonAnimationPlayback> playback) {
	}

	/// 原生播放器的窗口状态，弱键避免销毁后的播放器被静态表保留。
	private static final Map<TimelinePlayer, State> STATES = new WeakHashMap<>();
	/// 命名播放器的形参来源，跨 begin 重建窗口时保留，同样不延长句柄寿命。
	private static final Map<TimelinePlayer, WeakReference<PhotonAnimationPlayback>> SOURCES = new WeakHashMap<>();

	/// 普通时间轴按树直接检查，不创建窗口状态或临时叶轨道集合。
	private static boolean hasChildren(List<Track> tracks) {
		for (var track : tracks) {
			if (track.mute()) {
				continue;
			}

			if (track instanceof PhotonChildAnimationTrack
					|| track instanceof TrackGroup group && hasChildren(group.children())) {
				return true;
			}
		}

		return false;
	}

	private static double progress(PhotonChildAnimationClip clip, double time) {
		return clip.duration() == 0 ? 1 : Math.clamp((time - clip.start()) / clip.duration(), 0, 1);
	}

	public static void register(TimelinePlayer player, PhotonAnimationPlayback source) {
		stop(player);
		SOURCES.put(player, new WeakReference<>(source));
	}

	public static void evaluate(TimelinePlayer player, FXRuntime runtime, Timeline timeline, double time) {
		if (!Double.isFinite(time) || runtime.isDestroyed()) {
			stop(player);
			return;
		}

		if (!hasChildren(timeline.tracks())) {
			stop(player);
			return;
		}

		var source = SOURCES.get(player);
		var state = STATES.computeIfAbsent(player, _ -> new State(source));
		state.pause(!((PhotonAnimationPlayerAccess) player).lobecorp$audioEnabled());
		state.evaluate(runtime, timeline, time);
	}

	@SuppressWarnings("unused") // 与求值桥保持统一签名；渲染阶段只使用已建立的片段状态。
	public static void frame(TimelinePlayer player, FXRuntime runtime, Timeline timeline, double time) {
		var state = STATES.get(player);

		if (state != null && Double.isFinite(time) && !runtime.isDestroyed()) {
			state.pause(!((PhotonAnimationPlayerAccess) player).lobecorp$audioEnabled());
			state.frame(time);
		}
	}

	public static void pause(TimelinePlayer player, boolean paused) {
		var state = STATES.get(player);

		if (state != null) {
			state.pause(paused);
		}
	}

	public static void stop(TimelinePlayer player) {
		var state = STATES.remove(player);

		if (state != null) {
			state.close();
		}
	}

	public static void forget(TimelinePlayer player) {
		stop(player);
		SOURCES.remove(player);
	}

	public static List<String> problems(TimelinePlayer player) {
		var state = STATES.get(player);
		return state == null ? List.of() : List.copyOf(state.problems);
	}

	private static class State {
		private final @Nullable WeakReference<PhotonAnimationPlayback> source;
		private final Map<UUID, Window> windows = new HashMap<>();
		private final Set<String> problems = new HashSet<>();
		private double previous = Double.NEGATIVE_INFINITY;
		private boolean paused;

		private State(@Nullable WeakReference<PhotonAnimationPlayback> source) {
			this.source = source;
		}

		private @Nullable PhotonAnimationPlayback source() {
			var value = source == null ? null : source.get();
			return value == null || !value.current() ? null : value;
		}

		private void evaluate(FXRuntime runtime, Timeline timeline, double time) {
			if (time < previous) {
				close();
				windows.clear();
			}

			Set<UUID> seen = new HashSet<>();

			for (var leaf : timeline.leafTracks(false)) {
				if (leaf.mute() || !(leaf instanceof PhotonChildAnimationTrack track)) {
					continue;
				}

				for (var value : track.clips()) {
					if (value instanceof PhotonChildAnimationClip clip) {
						seen.add(clip.id);
						evaluate(runtime, track, clip, time);
					}
				}
			}

			for (var entry : List.copyOf(windows.entrySet())) {
				if (!seen.contains(entry.getKey())) {
					entry.getValue().close();
					windows.remove(entry.getKey());
				}
			}

			previous = time;
		}

		private void evaluate(FXRuntime runtime, PhotonChildAnimationTrack track, PhotonChildAnimationClip clip, double time) {
			var end = clip.start() + clip.duration();
			var active = time >= clip.start() && time < end;
			var crossed = previous < clip.start() && time >= end && previous < end;
			var window = windows.get(clip.id);

			if (!active && !crossed) {
				if (window != null && !window.completed && time >= end) {
					window.complete();
				}

				return;
			}

			if (window == null) {
				window = new Window(clip, source(), paused);
				windows.put(clip.id, window);
			}

			if (track instanceof PhotonAnimationCallTrack) {
				window.evaluateLocal(runtime, progress(clip, time), this);
			} else if (track.targetId() != null
					&& runtime.getSceneObject(track.targetId()) instanceof PhotonEmitterSpawner spawner) {
				window.evaluate(runtime, spawner, progress(clip, time), this);
			} else {
				problem(runtime, String.valueOf(track.targetId()));
				return;
			}

			if (crossed) {
				window.complete();
			}
		}

		private void frame(double time) {
			windows.values().forEach(window -> window.frame(time));
		}

		private void pause(boolean paused) {
			this.paused = paused;
			windows.values().forEach(window -> window.pause(paused));
		}

		@SuppressWarnings("resource") // 借用宿主控制器，关闭责任仍归 FXRuntime。
		private void problem(FXRuntime runtime, String problem) {
			if (!problems.add(problem)) {
				return;
			}

			var animations = PhotonAnimationUtil.of(runtime);

			if (animations != null) {
				animations.reportProblem(problem);
			}
		}

		private void close() {
			windows.values().forEach(Window::close);
		}
	}

	private static class Window {
		private final PhotonChildAnimationClip clip;
		private final UUID token = UUID.randomUUID();
		private final @Nullable WeakReference<PhotonAnimationPlayback> source;
		private final Set<UUID> fired = new HashSet<>();
		private final List<Child> children = new ArrayList<>();
		private boolean completed;
		private boolean paused;

		private Window(PhotonChildAnimationClip clip, @Nullable PhotonAnimationPlayback source, boolean paused) {
			this.clip = clip;
			this.source = source == null ? null : new WeakReference<>(source);
			this.paused = paused;
		}

		private @Nullable PhotonAnimationPlayback source() {
			var value = source == null ? null : source.get();
			return value == null || !value.current() ? null : value;
		}

		private void evaluate(FXRuntime runtime, PhotonEmitterSpawner spawner, double progress, State state) {
			if (completed) {
				return;
			}

			for (var handle : spawner.instanceHandles()) {
				if (!fired.add(handle)) {
					continue;
				}

				try {
					start(spawner, handle);
				} catch (IllegalArgumentException | IllegalStateException invalidCall) {
					state.problem(runtime, String.join(":", spawner.id().toString(), handle.toString(), clip.animation));
				}
			}

			drive(progress);
		}

		// 本实例调用仍借用宿主控制器，关闭责任不转移。
		private void evaluateLocal(FXRuntime runtime, double progress, State state) {
			var target = PhotonAnimationUtil.of(runtime);
			var parent = source();

			if (completed || target == null || parent == null || !target.isTestTimeline(parent)) {
				return;
			}

			if (fired.add(clip.id)) {
				try {
					start(target);
				} catch (IllegalArgumentException | IllegalStateException invalidCall) {
					state.problem(runtime, clip.animation);
				}
			}

			drive(progress);
		}

		private void drive(double progress) {
			if (clip.mode == PhotonChildAnimationClip.Mode.CONTROLLED) {
				for (var child : children) {
					var target = child.runtime().get();
					var playback = child.playback().get();

					if (target != null && playback != null && target.isCurrent(playback) && playback.controlledBy(token)) {
						target.driveControlled(playback, token, progress);
					}
				}
			}
		}

		private void start(PhotonEmitterSpawner spawner, UUID handle) {
			var target = spawner.instanceAnimations(handle);

			if (target == null) {
				throw new IllegalArgumentException(clip.animation);
			}

			start(target);
		}

		private void start(PhotonAnimationRuntime target) {
			var inputs = inputs();
			var playback = clip.mode == PhotonChildAnimationClip.Mode.CONTROLLED
					? target.playControlled(clip.animation, clip.duration(), inputs, token)
					: target.play(clip.animation, clip.playbackDuration, true, inputs);
			playback.setControlAudioPaused(token, paused);
			var source = source();

			for (var input : clip.inputs) {
				if (input.follow && !input.source.isEmpty() && source != null) {
					playback.follow(input.target, source, input.source);
				}
			}

			if (clip.mode == PhotonChildAnimationClip.Mode.CONTROLLED
					&& clip.completion != PhotonChildAnimationClip.Completion.INHERIT) {
				playback.completion(completion(playback));
			}

			children.add(new Child(new WeakReference<>(target), new WeakReference<>(playback)));
		}

		private Map<String, float[]> inputs() {
			var values = new HashMap<String, float[]>();
			var source = source();

			for (var input : clip.inputs) {
				var value = input.source.isEmpty() ? input.value.clone()
						: source == null ? null : source.parameter(input.source);

				if (value == null || input.target.isEmpty() || values.putIfAbsent(input.target, value) != null) {
					throw new IllegalArgumentException(input.target);
				}
			}

			return values;
		}

		private PhotonAnimationDefinition.Completion completion(PhotonAnimationPlayback playback) {
			return switch (clip.completion) {
				case INHERIT -> playback.definition.completion;
				case HOLD -> PhotonAnimationDefinition.Completion.HOLD;
				case RELEASE -> PhotonAnimationDefinition.Completion.RELEASE;
			};
		}

		private void pause(boolean paused) {
			this.paused = paused;

			if (clip.mode != PhotonChildAnimationClip.Mode.CONTROLLED) {
				return;
			}

			for (var child : children) {
				var playback = child.playback().get();

				if (playback != null) {
					playback.setControlAudioPaused(token, paused);
				}
			}
		}

		private void frame(double time) {
			if (completed || clip.mode != PhotonChildAnimationClip.Mode.CONTROLLED) {
				return;
			}

			var progress = progress(clip, time);

			for (var child : children) {
				var playback = child.playback().get();

				if (playback != null) {
					playback.frame(token, progress);
				}
			}
		}

		private void complete() {
			if (completed) {
				return;
			}

			completed = true;

			if (clip.mode != PhotonChildAnimationClip.Mode.CONTROLLED) {
				return;
			}

			for (var child : children) {
				var runtime = child.runtime().get();
				var playback = child.playback().get();

				if (runtime != null && playback != null) {
					runtime.completeControlled(playback, token, completion(playback));
				}
			}
		}

		private void close() {
			if (clip.mode != PhotonChildAnimationClip.Mode.CONTROLLED) {
				return;
			}

			completed = true;

			for (var child : children) {
				var runtime = child.runtime().get();
				var playback = child.playback().get();

				if (runtime != null && playback != null && playback.controlledBy(token)) {
					runtime.stop(playback);
				}
			}
		}
	}
}
