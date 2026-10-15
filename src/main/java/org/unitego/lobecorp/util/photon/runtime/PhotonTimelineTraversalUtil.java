package org.unitego.lobecorp.util.photon.runtime;

import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.SignalTrack;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationTrack;

import java.util.List;
import java.util.TreeSet;

/// 只补经过的离散事件边界；曲线仍在原生求值阶段采样，渲染帧不会重复提交。
public class PhotonTimelineTraversalUtil {
	public static List<Double> boundaries(Timeline timeline, double previous, double current) {
		if (!Double.isFinite(previous) || !Double.isFinite(current) || current <= previous) {
			return List.of();
		}

		var times = new TreeSet<Double>();

		for (var start : PhotonActivatorTimelineUtil.crossedEntries(timeline, previous, current)) {
			add(times, start, previous, current);
		}

		for (var track : timeline.leafTracks(false)) {
			if (track.mute() || track instanceof AnimationTrack) {
				continue;
			}

			if (track instanceof SignalTrack signals) {
				signals.signals().forEach(signal -> add(times, signal.time(), previous, current));
			}

			for (var clip : track.clips()) {
				if (track instanceof ActivatorTrack) {
					if (PhotonActivatorTimelineUtil.nativeInterval(track) && clip.duration() > 0
							&& track.clipAt(clip.end()) == null) {
						add(times, clip.start() + clip.duration(), previous, current);
					}

					continue;
				}

				if (clip.duration() > 0 || track instanceof PhotonChildAnimationTrack) {
					add(times, clip.start(), previous, current);
					add(times, clip.start() + clip.duration(), previous, current);
				}
			}
		}

		return List.copyOf(times);
	}

	private static void add(TreeSet<Double> times, double value, double previous, double current) {
		if (Double.isFinite(value) && value > previous && value < current) {
			times.add(value);
		}
	}
}
