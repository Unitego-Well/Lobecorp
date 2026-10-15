package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationPlayback;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelAnimationClip;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelAnimationTrack;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/// 将原生时间轴的模型片段采样叠加到实例；不创建时钟或改变发射器生命周期。
public class PhotonModelAnimationTimelineUtil {
	public record Sample(String animation, float time, boolean loop, boolean holdEnd, int priority, long order) {
	}

	/// 主时间轴在完整模型片段选择中作为最低优先级的基础播放。
	private static final int SCENE_PRIORITY = Integer.MIN_VALUE;
	private static final Map<TimelinePlayer, WeakReference<PhotonAnimationPlayback>> SOURCES = new WeakHashMap<>();
	/// 双层弱键避免实例、播放器与 FXRuntime 相互引用后延长生命周期。
	private static final Map<PhotonModelEmitter, Map<TimelinePlayer, Sample>> SAMPLES = new WeakHashMap<>();
	private static final Map<TimelinePlayer, List<WeakReference<PhotonModelEmitter>>> TARGETS = new WeakHashMap<>();
	private static final Comparator<Sample> PRECEDENCE = Comparator.comparingInt(Sample::priority).thenComparingLong(Sample::order);

	public static void register(TimelinePlayer player, PhotonAnimationPlayback source) {
		stop(player);
		SOURCES.put(player, new WeakReference<>(source));
	}

	public static void evaluate(TimelinePlayer player, FXRuntime runtime, Timeline timeline, double time) {
		stop(player);

		if (!Double.isFinite(time) || runtime.isDestroyed()) {
			return;
		}

		var reference = SOURCES.get(player);
		var source = reference == null ? null : reference.get();

		if (reference != null && (source == null || !source.current())) {
			return;
		}

		var priority = source == null ? SCENE_PRIORITY : source.definition.priority;
		var order = source == null ? 0 : source.order;
		var targets = new ArrayList<WeakReference<PhotonModelEmitter>>();

		for (var leaf : timeline.leafTracks(false)) {
			if (leaf.mute() || !(leaf instanceof PhotonModelAnimationTrack track)
					|| !(runtime.objects.get(track.targetId()) instanceof PhotonModelEmitter model)) {
				continue;
			}

			var clip = selected(track, time);

			if (clip == null || !model.hasAnimationClip(clip.animation)) {
				continue;
			}

			var sampleTime = clip.sampleTime(time);

			if (!Double.isFinite(sampleTime) || Math.abs(sampleTime) > Float.MAX_VALUE) {
				continue;
			}

			var samples = SAMPLES.computeIfAbsent(model, _ -> new WeakHashMap<>());
			var holdEnd = time >= clip.end() && clip.duration() > 0 && clip.speed() > 0;
			samples.put(player, new Sample(clip.animation, (float) sampleTime, clip.loop, holdEnd, priority, order));
			targets.add(new WeakReference<>(model));
		}

		if (!targets.isEmpty()) {
			TARGETS.put(player, targets);
		}
	}

	private static @Nullable PhotonModelAnimationClip selected(PhotonModelAnimationTrack track, double time) {
		PhotonModelAnimationClip selected = null;

		for (var value : track.clips()) {
			if (value instanceof PhotonModelAnimationClip clip && clip.start() <= time
					&& (selected == null || clip.start() >= selected.start())) {
				selected = clip;
			}
		}

		if (selected == null || time >= selected.end()
				&& selected.completion == PhotonModelAnimationClip.Completion.RESTORE) {
			return null;
		}

		return selected;
	}

	public static @Nullable Sample sample(PhotonModelEmitter emitter) {
		var samples = SAMPLES.get(emitter);
		return samples == null ? null : samples.values().stream().max(PRECEDENCE).orElse(null);
	}

	public static void clear(PhotonModelEmitter emitter) {
		SAMPLES.remove(emitter);
	}

	public static void stop(TimelinePlayer player) {
		var targets = TARGETS.remove(player);

		if (targets == null) {
			return;
		}

		for (var reference : targets) {
			var model = reference.get();
			var samples = model == null ? null : SAMPLES.get(model);

			if (samples != null) {
				samples.remove(player);

				if (samples.isEmpty()) {
					SAMPLES.remove(model);
				}
			}
		}
	}

	public static void forget(TimelinePlayer player) {
		stop(player);
		SOURCES.remove(player);
	}
}
