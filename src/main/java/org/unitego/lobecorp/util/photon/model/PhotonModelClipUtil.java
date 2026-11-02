package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import java.util.ArrayList;
import java.util.List;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClip;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClipTrack;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedPlan.ModelClipTarget;

/// 播放贡献由目标实例持有；主时间轴和命名播放通过独立owner清理，不使用全局强引用表。
public class PhotonModelClipUtil {
	/// 主时间轴低于所有显式命名播放的优先级。
	public static final int SCENE_PRIORITY = Integer.MIN_VALUE;

	/// 每个owner独占实际触及目标；同UUID替换后先清理旧model，再解析当前model。
	public static final class ContributionState {
		private final List<PhotonModelOutput> touched = new ArrayList<>();

		public void apply(Object owner, FXRuntime runtime, List<ModelClipTarget> tracks, double time, int priority, long order) {
			clear(owner);

			if (!Double.isFinite(time) || runtime.isDestroyed()) {
				return;
			}

			for (var target : tracks) {
				var track = target.track();

				if (track.mute() || !(runtime.objects.get(target.targetId()) instanceof PhotonModelOutput model)) {
					continue;
				}

				var clip = track.selected(time);

				if (clip == null || !model.supportsAnimation(clip.animation)) {
					continue;
				}

				double sampled = clip.sampleTime(time);

				if (!Double.isFinite(sampled) || Math.abs(sampled) > Float.MAX_VALUE) {
					throw new IllegalArgumentException("模型动画采样超出当前协议数值范围。");
				}

				boolean hold = time >= clip.end() && clip.duration() > 0 && clip.speed() > 0;
				model.playback(owner, new PhotonModelClip.Sample(clip.animation, (float) sampled, clip.loop, hold, priority, order));
				touch(model);
			}
		}

		/// 主时间轴读取可变scene timeline；没有可靠revision时不缓存leaf列表。
		public void apply(Object owner, FXRuntime runtime, Timeline timeline, double time, int priority, long order) {
			clear(owner);

			if (!Double.isFinite(time) || runtime.isDestroyed()) {
				return;
			}

			for (var leaf : timeline.leafTracks(false)) {
				if (!(leaf instanceof PhotonModelClipTrack track) || track.mute()
						|| !(runtime.objects.get(track.targetId()) instanceof PhotonModelOutput model)) {
					continue;
				}

				var clip = track.selected(time);

				if (clip == null || !model.supportsAnimation(clip.animation)) {
					continue;
				}

				double sampled = clip.sampleTime(time);

				if (!Double.isFinite(sampled) || Math.abs(sampled) > Float.MAX_VALUE) {
					throw new IllegalArgumentException("模型动画采样超出当前协议数值范围。");
				}

				boolean hold = time >= clip.end() && clip.duration() > 0 && clip.speed() > 0;
				model.playback(owner, new PhotonModelClip.Sample(clip.animation, (float) sampled, clip.loop, hold, priority, order));
				touch(model);
			}
		}

		public void clear(Object owner) {
			for (var model : touched) {
				model.playback(owner, null);
			}

			touched.clear();
		}

		private void touch(PhotonModelOutput model) {
			if (!touched.contains(model)) {
				touched.add(model);
			}
		}
	}

	public static void apply(Object owner, FXRuntime runtime, Timeline timeline, double time, int priority, long order) {
		for (var object : runtime.objects.values()) {
			if (object instanceof PhotonModelOutput model) {
				model.playback(owner, null);
			}
		}

		if (!Double.isFinite(time) || runtime.isDestroyed()) {
			return;
		}

		for (var leaf : timeline.leafTracks(false)) {
			if (!(leaf instanceof PhotonModelClipTrack track) || track.mute()
					|| !(runtime.objects.get(track.targetId()) instanceof PhotonModelOutput model)) {
				continue;
			}

			var clip = track.selected(time);

			if (clip == null || !model.supportsAnimation(clip.animation)) {
				continue;
			}

			double sampled = clip.sampleTime(time);

			if (!Double.isFinite(sampled) || Math.abs(sampled) > Float.MAX_VALUE) {
				throw new IllegalArgumentException("模型动画采样超出当前协议数值范围。");
			}

			boolean hold = time >= clip.end() && clip.duration() > 0 && clip.speed() > 0;
			model.playback(owner, new PhotonModelClip.Sample(clip.animation, (float) sampled, clip.loop, hold, priority, order));
		}
	}

	public static void clear(Object owner, FXRuntime runtime) {
		for (var object : runtime.objects.values()) {
			if (object instanceof PhotonModelOutput model) {
				model.playback(owner, null);
			}
		}
	}

}
