package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Track;
import org.unitego.lobecorp.client.photon.playback.spawn.PhotonChildSpawner;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonCallTrack;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildClip;
import org.unitego.lobecorp.client.photon.playback.timeline.PhotonChildTrack;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Completion;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.*;

/// 每个父播放独占窗口；窗口内迟生成的子实例只触发一次，受控与独立播放分开结束。
public class PhotonChildWindows implements AutoCloseable {
	private final FXRuntime source;
	private final @Nullable PhotonNamedHandle parent;
	private final @Nullable PhotonNamedRuntime callHost;
	private final Map<UUID, Window> windows = new HashMap<>();
	private double previous = Double.NEGATIVE_INFINITY;
	public PhotonChildWindows(FXRuntime source, @Nullable PhotonNamedHandle parent) {
		this(source, parent, null);
	}
	public PhotonChildWindows(FXRuntime source, @Nullable PhotonNamedHandle parent, @Nullable PhotonNamedRuntime callHost) {
		this.source = source;
		this.parent = parent;
		this.callHost = callHost;
	}

	private static boolean current(@Nullable PhotonNamedRuntime runtime, @Nullable PhotonNamedHandle handle) {
		return runtime != null && handle != null && !runtime.closed() && !handle.disposed()
				&& runtime.ownsPlayback(handle);
	}

	public void evaluate(List<? extends Track> tracks, double time) {
		if (time < previous) {
			close();
		}

		var seen = new HashSet<UUID>();

		for (var leaf : tracks) {
			if (!(leaf instanceof PhotonChildTrack track) || leaf.mute() || track.clips().isEmpty()) {
				continue;
			}

			var targets = targets(track);

			for (var value : track.clips()) {
				var clip = (PhotonChildClip) value;
				seen.add(clip.id);
				var window = windows.get(clip.id);
				boolean active = time >= clip.start() && time < clip.end();
				boolean crossed = previous < clip.start() && time >= clip.end();
				boolean finishing = window != null && !window.completed && time >= clip.end();

				if (!active && !crossed && !finishing) {
					continue;
				}

				if (window == null) {
					window = new Window(clip);
					windows.put(clip.id, window);
				}

				if (window.completed) {
					continue;
				}

				for (var target : targets.entrySet()) {
					var id = target.getKey();
					if (window.children.containsKey(id)) {
						continue;
					}

					var runtime = target.getValue();

					if (runtime == null) {
						throw new IllegalArgumentException("子动画生成实例缺少控制器：" + id);
					}

					var inputs = PhotonNamedRuntime.inputs(clip.inputs, parent);
					var handle = track instanceof PhotonCallTrack
							? runtime.playProjectCall(clip.animation, clip.mode == PhotonChildClip.Mode.CONTROLLED ? clip.duration() : clip.playbackDuration,
							clip.mode == PhotonChildClip.Mode.CONTROLLED, inputs)
							: clip.mode == PhotonChildClip.Mode.CONTROLLED ? runtime.playControlled(clip.animation, clip.duration(), inputs)
							: runtime.play(clip.animation, clip.playbackDuration, true, inputs);

					if (parent != null) {
						for (var input : clip.inputs) {
							if (input.follow() && !input.source().isEmpty()) {
								handle.follow(input.target(), parent, input.source());
							}
						}
					}

					window.children.put(id, new Child(new WeakReference<>(runtime), new WeakReference<>(handle)));
				}

				window.drive(finishing || crossed ? clip.end() : time, true);

				if (crossed || finishing) {
					window.complete();
				}
			}
		}

		windows.entrySet().removeIf(entry -> {
			if (seen.contains(entry.getKey())) {
				return false;
			}

			entry.getValue().close();
			return true;
		});
		previous = time;
	}

	private Map<UUID, PhotonNamedRuntime> targets(PhotonChildTrack track) {
		if (track instanceof PhotonCallTrack) {
			if (callHost == null || parent == null || !callHost.ownsPreview(parent) && !callHost.ownsAuthoring(parent)) {
				throw new IllegalArgumentException("动画调用只能来自本实例独立的工程测试定义。");
			}

			return Map.of(FXRuntime.ROOT_UUID, callHost);
		}

		if (!(source.objects.get(track.targetId()) instanceof PhotonChildSpawner spawner)) {
			throw new IllegalArgumentException("子动画轨道没有有效的生成器目标：" + track.targetId());
		}

		return spawner.animationSnapshot();
	}

	public void frame(double time) {
		windows.values().forEach(window -> window.drive(time, false));
	}

	@Override
	public void close() {
		windows.values().forEach(Window::close);
		windows.clear();
		previous = Double.NEGATIVE_INFINITY;
	}

	private record Child(WeakReference<PhotonNamedRuntime> runtime, WeakReference<PhotonNamedHandle> handle) {
	}

	private static class Window {
		private final PhotonChildClip clip;
		private final Map<UUID, Child> children = new HashMap<>();
		private boolean completed;

		private Window(PhotonChildClip clip) {
			this.clip = clip;
		}

		private void drive(double time, boolean simulation) {
			if (completed || clip.mode != PhotonChildClip.Mode.CONTROLLED) {
				return;
			}

			for (var child : children.values()) {
				var runtime = child.runtime().get();
				var handle = child.handle().get();

				if (current(runtime, handle)) {
					runtime.driveControlled(handle, clip.windowProgress(time), simulation);
				}
			}
		}

		private void complete() {
			if (completed) {
				return;
			}

			completed = true;

			if (clip.mode == PhotonChildClip.Mode.CONTROLLED) {
				for (var child : children.values()) {
					var runtime = child.runtime().get();
					var handle = child.handle().get();

					if (current(runtime, handle)) {
						var completion = switch (clip.completion) {
							case INHERIT -> handle.definition().completion();
							case HOLD -> Completion.HOLD;
							case RELEASE -> Completion.RELEASE;
						};
						runtime.completeControlled(handle, completion);
					}
				}
			}
		}

		private void close() {
			if (clip.mode == PhotonChildClip.Mode.CONTROLLED) {
				for (var child : children.values()) {
					var runtime = child.runtime().get();
					var handle = child.handle().get();

					if (current(runtime, handle)) {
						runtime.stop(handle);
					}
				}
			}

			children.clear();
			completed = true;
		}
	}
}
