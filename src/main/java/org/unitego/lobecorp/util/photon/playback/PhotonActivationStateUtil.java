package org.unitego.lobecorp.util.photon.playback;

import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.TreeSet;

/// 按制作时间纯求值；区间并集只产生一次入口，单边命令到点后才取得所有权。
public class PhotonActivationStateUtil {
	/// 普通区间取并集；最新单边命令覆盖持续区间，之后的新区间入口才能恢复。
	public static Combined combine(List<Contribution> contributions) {
		boolean active = false;
		boolean emitting = false;
		int command = -1;
		double latest = Double.NEGATIVE_INFINITY;

		for (int index = 0; index < contributions.size(); index++) {
			var contribution = contributions.get(index);
			var options = contribution.options();
			var state = contribution.state();

			if (options.startControl() && options.endControl()) {
				active |= state.active();
				emitting |= state.active() && !state.stopped();
			}

			if (state.event() != null && state.event().time() >= latest) {
				command = index;
				latest = state.event().time();
			}
		}

		if (command >= 0 && contributions.get(command).options().pointControl()) {
			var state = contributions.get(command).state();
			active = state.active();
			emitting = active && !state.stopped();
		}

		return new Combined(active, emitting, command);
	}

	public static State evaluate(List<Window> windows, PhotonActivationOptions options, double time) {
		return evaluate(prepare(windows), options, time);
	}

	public static Prepared prepare(List<Window> windows) {
		var boundaries = new TreeSet<Double>();

		for (var window : windows) {
			if (window.end() > window.start()) {
				boundaries.add(window.start());
				boundaries.add(window.end());
			}
		}

		return new Prepared(windows, List.copyOf(boundaries));
	}

	public static State evaluate(Prepared prepared, PhotonActivationOptions options, double time) {
		var windows = prepared.windows();
		if (!Double.isFinite(time) || time < 0) {
			throw new IllegalArgumentException("激活采样时间无效。");
		}

		if (!options.startControl() && !options.endControl()) {
			return new State(false, false, false, null, null, null);
		}

		if (options.pointControl()) {
			return point(windows, options, time);
		}

		boolean active = false;
		boolean stopped = false;
		Boundary restart = null;
		Boundary clear = null;
		Boundary event = null;
		Window previous = null;

		for (double boundary : prepared.boundaries()) {
			if (boundary > time) {
				break;
			}
			var next = at(windows, boundary);

			if ((next != null) == (previous != null)) {
				previous = next;
				continue;
			}

			if (previous != null) {
				active = options.endBehavior() == PhotonActivationOptions.EndBehavior.STOP_EMISSION;
				stopped = options.endBehavior() != PhotonActivationOptions.EndBehavior.ORIGINAL;

				if (options.endBehavior() == PhotonActivationOptions.EndBehavior.CLEAR) {
					clear = new Boundary(boundary, previous.index());
				}
			}

			if (next != null) {
				event = new Boundary(boundary, next.index());
				active = true;
				stopped = false;

				if (options.startBehavior() == PhotonActivationOptions.StartBehavior.RESTART) {
					restart = new Boundary(boundary, next.index());
				}
			}

			previous = next;
		}

		return new State(true, active, stopped, restart, clear, event);
	}

	private static @Nullable Window at(List<Window> windows, double time) {
		for (var window : windows) {
			if (window.start() <= time && time < window.end()) {
				return window;
			}
		}

		return null;
	}

	private static State point(List<Window> windows, PhotonActivationOptions options, double time) {
		Boundary latest = null;

		for (var window : windows) {
			double point = options.startControl() ? window.start() : window.end();

			if (point <= time && (latest == null || point > latest.time())) {
				latest = new Boundary(point, window.index());
			}
		}

		if (latest == null) {
			return new State(false, false, false, null, null, null);
		}

		boolean start = options.startControl();
		return new State(true, start || options.endBehavior() == PhotonActivationOptions.EndBehavior.STOP_EMISSION,
				!start && options.endBehavior() != PhotonActivationOptions.EndBehavior.ORIGINAL,
				start && options.startBehavior() == PhotonActivationOptions.StartBehavior.RESTART ? latest : null,
				!start && options.endBehavior() == PhotonActivationOptions.EndBehavior.CLEAR ? latest : null, latest);
	}

	public record Window(double start, double end, int index) {
		public Window {
			if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end < start) {
				throw new IllegalArgumentException("激活片段范围无效。");
			}
		}
	}

	public record Boundary(double time, int index) {
	}

	public record Prepared(List<Window> windows, List<Double> boundaries) {
		public Prepared {
			windows = List.copyOf(windows);
			boundaries = List.copyOf(boundaries);
		}
	}

	public record State(boolean owned, boolean active, boolean stopped, @Nullable Boundary restart,
	                    @Nullable Boundary clear, @Nullable Boundary event) {
	}

	public record Contribution(PhotonActivationOptions options, State state) {
	}

	public record Combined(boolean active, boolean emitting, int command) {
	}
}
