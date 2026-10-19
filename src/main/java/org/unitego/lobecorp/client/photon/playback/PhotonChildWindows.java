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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 每个父播放独占窗口；窗口内迟生成的子实例只触发一次，受控与独立播放分开结束。
public class PhotonChildWindows implements AutoCloseable {
	private record Child(WeakReference<PhotonNamedRuntime> runtime, WeakReference<PhotonNamedHandle> handle) {
	}

	private static class Window {
		private final PhotonChildClip clip;
		private final Map<UUID, Child> children = new HashMap<>();
		private boolean completed;

		private Window(PhotonChildClip clip) {
			this.clip = clip;
		}

		private void drive(double time) {
			if (completed || clip.mode != PhotonChildClip.Mode.CONTROLLED) {
				return;
			}

			for (var child : children.values()) {
				var runtime = child.runtime().get();
				var handle = child.handle().get();

				if (current(runtime, handle)) {
					handle.drive(clip.windowProgress(time));
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

	private final FXRuntime source;
	private final @Nullable PhotonNamedHandle parent;
	private final Map<UUID, Window> windows = new HashMap<>();
	private double previous = Double.NEGATIVE_INFINITY;

	public PhotonChildWindows(FXRuntime source, @Nullable PhotonNamedHandle parent) {
		this.source = source;
		this.parent = parent;
	}

	public void evaluate(List<Track> tracks, double time) {
		if (time < previous) {
			close();
		}

		var seen = new HashSet<UUID>();

		for (var leaf : tracks) {
			if (leaf.mute() || !(leaf instanceof PhotonChildTrack track) || track.clips().isEmpty()) {
				continue;
			}

			if (track instanceof PhotonCallTrack) {
				throw new IllegalArgumentException("工程动画调用轨道尚待测试时间轴入口适配。");
			}

			if (!(source.objects.get(track.targetId()) instanceof PhotonChildSpawner spawner)) {
				throw new IllegalArgumentException("子动画轨道没有有效的生成器目标：" + track.targetId());
			}

			for (var value : track.clips()) {
				var clip = (PhotonChildClip) value;
				seen.add(clip.id);
				var window = windows.get(clip.id);
				boolean active = time >= clip.start() && time < clip.end();
				boolean crossed = previous < clip.start() && time >= clip.end();

				if (!active && !crossed) {
					if (window != null && time >= clip.end()) {
						window.complete();
					}

					continue;
				}

				if (window == null) {
					window = new Window(clip);
					windows.put(clip.id, window);
				}

				if (window.completed) {
					continue;
				}

				for (var id : spawner.instanceIds()) {
					if (window.children.containsKey(id)) {
						continue;
					}

					var runtime = spawner.animations(id);

					if (runtime == null) {
						throw new IllegalArgumentException("子动画生成实例缺少控制器：" + id);
					}

					var inputs = PhotonNamedRuntime.inputs(clip.inputs, parent);
					var handle = clip.mode == PhotonChildClip.Mode.CONTROLLED
							? runtime.playControlled(clip.animation, clip.duration(), inputs)
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

				window.drive(time);

				if (crossed) {
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

	public void frame(double time) {
		windows.values().forEach(window -> window.drive(time));
	}

	private static boolean current(@Nullable PhotonNamedRuntime runtime, @Nullable PhotonNamedHandle handle) {
		return runtime != null && handle != null && !runtime.closed() && !handle.disposed()
				&& runtime.playback(handle.definition().id().toString()) == handle;
	}

	@Override
	public void close() {
		windows.values().forEach(Window::close);
		windows.clear();
		previous = Double.NEGATIVE_INFINITY;
	}
}
