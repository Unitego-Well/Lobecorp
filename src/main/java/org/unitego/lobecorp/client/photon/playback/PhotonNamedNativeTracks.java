package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.fx.timeline.SpeedTrack;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.unitego.lobecorp.mixin.photon.editor.client.TimelinePlayerAccessor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 每个命名句柄独占原生音频实例；后期贡献沿用宿主逐帧sink，不替换主时间轴。
public class PhotonNamedNativeTracks implements AutoCloseable {
	private final FXRuntime runtime;
	private final List<Track> audioTracks;
	private final TimelinePlayer media;
	private double previousTime = Double.NaN;
	private final boolean hasAudio;
	private final boolean hasPostProcess;

	public PhotonNamedNativeTracks(FXRuntime runtime, Timeline timeline, List<Track> leaves, boolean hasAudio,
	                               boolean hasPostProcess) {
		this.runtime = runtime;
		this.hasAudio = hasAudio;
		this.hasPostProcess = hasPostProcess;
		media = new TimelinePlayer(runtime, timeline);
		audioTracks = hasAudio ? List.copyOf(leaves) : List.of();
	}

	public @Nullable IEffectExecutor executor() {
		return ((TimelinePlayerAccessor) runtime.timelinePlayer).lobecorp$effect();
	}

	public void sample(double time, boolean simulation, boolean playing) {
		if (!hasAudio && !hasPostProcess) {
			return;
		}
		var bridge = (TimelinePlayerAccessor) media;
		bridge.lobecorp$effect(executor());
		if (simulation && hasAudio) {
			if (time < previousTime) {
				media.stopAllAudio();
			}
			previousTime = time;
			media.setAudioDispatch(playing);
			bridge.lobecorp$audio(audioTracks, time);
		} else if (!simulation && hasPostProcess) {
			bridge.lobecorp$postProcess(time);
		}
	}

	@Override
	public void close() {
		media.stop();
	}

	/// 命名速度按优先级/播放顺序覆盖；退出后只恢复仍属于本混合器的贡献。
	public static class Speeds {
		private record Value(float original, float applied) {
		}

		private final FXRuntime runtime;
		private final Map<FXObject, Value> values = new HashMap<>();
		private final Set<FXObject> seen = new HashSet<>();
		private final Map<FXObject, Float> nativeValues = new HashMap<>();

		public Speeds(FXRuntime runtime) {
			this.runtime = runtime;
		}

		public void begin() {
			seen.clear();
			nativeValues.clear();
			double nativeTime = Math.max(0, ((TimelinePlayerAccessor) runtime.timelinePlayer).lobecorp$lastEvaluationTime());
			for (var track : runtime.fxData.timeline().leafTracks(false)) {
				if (track instanceof SpeedTrack speed && runtime.objects.get(speed.targetId()) instanceof FXObject target) {
					nativeValues.put(target, speed.sampleSpeed(nativeTime));
				}
			}
			values.replaceAll((target, value) -> {
				var nativeValue = nativeValues.get(target);
				if (nativeValue != null) {
					target.setSelfTimeScale(nativeValue);
					return new Value(nativeValue, nativeValue);
				}
				float current = target.getSelfTimeScale();
				if (Float.compare(current, value.applied()) == 0) {
					target.setSelfTimeScale(value.original());
					return value;
				}
				return new Value(current, current);
			});
		}

		public void apply(List<SpeedTrack> tracks, double time) {
			for (var speed : tracks) {
				if (speed.mute()) {
					continue;
				}

				if (!(runtime.objects.get(speed.targetId()) instanceof FXObject target)) {
					throw new IllegalArgumentException("速度轨道目标不存在。");
				}

				float value = speed.sampleSpeed(time);

				if (!Float.isFinite(value) || value < 0) {
					throw new IllegalArgumentException("速度轨道采样值必须为有限非负数。");
				}

				var previous = values.get(target);
				float original = previous == null ? nativeValues.getOrDefault(target, target.getSelfTimeScale()) : previous.original();
				values.put(target, new Value(original, value));
				target.setSelfTimeScale(value);
				seen.add(target);
			}
		}

		public void finish() {
			values.keySet().removeIf(target -> !seen.contains(target));
		}

		public void clear() {
			begin();
			values.clear();
			seen.clear();
		}
	}
}
