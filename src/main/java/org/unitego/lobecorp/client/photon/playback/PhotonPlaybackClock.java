package org.unitego.lobecorp.client.photon.playback;

import java.util.Objects;

/// 每个命名句柄独占周期位置；改时长保留归一化位置，暂停不累积待补时间。
public class PhotonPlaybackClock {
	public enum State {
		PLAYING, PAUSED, HOLDING, STOPPED
	}

	public record Snapshot(double duration, double position, double anchor, double elapsed,
	                       boolean looping, State state, State pausedState, boolean completionDelivered) {
	}

	private final boolean looping;
	private double duration;
	private double position;
	private double anchor;
	private double elapsed;
	private State state = State.PLAYING;
	private State pausedState = State.PLAYING;
	private boolean completionDelivered;

	public PhotonPlaybackClock(double duration, boolean looping) {
		this.looping = looping;
		duration(duration);
	}

	public void duration(double value) {
		finiteNonnegative(value);

		if (looping && value == 0) {
			throw new IllegalArgumentException("循环命名播放不能使用零时长。");
		}

		duration = value;
		anchor = position;
		elapsed = 0;
	}

	public double advance(double ticks) {
		finiteNonnegative(ticks);
		double before = position;

		if (state != State.PLAYING) {
			return before;
		}

		elapsed = Math.min(Double.MAX_VALUE, elapsed + ticks);
		position = duration == 0 ? 1 : Math.min(Double.MAX_VALUE, anchor + elapsed / duration);

		if (!looping && position >= 1) {
			position = 1;
			state = State.HOLDING;
		}

		return before;
	}

	public double position(double partial) {
		finiteNonnegative(partial);

		if (state != State.PLAYING || duration == 0) {
			return position;
		}

		double value = Math.min(Double.MAX_VALUE, position + partial / duration);
		return looping ? value : Math.min(1, value);
	}

	public double progress(double partial) {
		double value = position(partial);
		return looping ? value - Math.floor(value) : value;
	}

	public long loops(double partial) {
		return looping ? (long) Math.floor(position(partial)) : 0;
	}

	/// 受控片段驱动位置，不派发完成事件；结束策略由父调用显式提交。
	public void seek(double value) {
		finiteNonnegative(value);
		position = value;
		anchor = value;
		elapsed = 0;
	}

	public void complete() {
		seek(1);
		state = State.HOLDING;
	}

	public boolean takeCompletion() {
		if (looping || state != State.HOLDING || completionDelivered) {
			return false;
		}

		completionDelivered = true;
		return true;
	}

	public void pause() {
		if (state != State.PAUSED && state != State.STOPPED) {
			pausedState = state;
			state = State.PAUSED;
		}
	}

	public void resume() {
		if (state == State.PAUSED) {
			state = pausedState;
		}
	}

	public void stop() {
		state = State.STOPPED;
	}

	public double duration() {
		return duration;
	}

	public State state() {
		return state;
	}

	public boolean looping() {
		return looping;
	}

	public Snapshot snapshot() {
		return new Snapshot(duration, position, anchor, elapsed, looping, state, pausedState, completionDelivered);
	}

	public void restore(Snapshot snapshot) {
		finiteNonnegative(snapshot.duration());
		finiteNonnegative(snapshot.position());
		finiteNonnegative(snapshot.anchor());
		finiteNonnegative(snapshot.elapsed());
		Objects.requireNonNull(snapshot.state());
		Objects.requireNonNull(snapshot.pausedState());

		if (snapshot.looping() != looping || looping && snapshot.duration() == 0) {
			throw new IllegalArgumentException("播放时钟快照不匹配。");
		}

		duration = snapshot.duration();
		position = snapshot.position();
		anchor = snapshot.anchor();
		elapsed = snapshot.elapsed();
		state = snapshot.state();
		pausedState = snapshot.pausedState();
		completionDelivered = snapshot.completionDelivered();
	}

	private static void finiteNonnegative(double value) {
		if (!Double.isFinite(value) || value < 0) {
			throw new IllegalArgumentException("命名播放时间必须有限且非负。");
		}
	}
}
