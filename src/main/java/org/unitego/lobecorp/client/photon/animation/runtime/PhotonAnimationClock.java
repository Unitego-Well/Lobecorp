package org.unitego.lobecorp.client.photon.animation.runtime;

import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;

import java.util.Objects;

/// 独立动画时钟；位置使用周期数，改时长不改位置。
public class PhotonAnimationClock {
	public enum State {PLAYING, PAUSED, HOLDING, STOPPED}

	/// 编辑器重模拟保留完整时钟，包括暂停前状态和完成事件是否已经取走。
	public record PreviewState(double duration, double position, double anchorPosition, double elapsedTicks,
	                           State state, State pausedState, boolean completed) {
	}

	private double duration;
	private double position;
	/// 改时长时的归一化位置与此后累计 tick，避免逐 tick 相加进度造成边界漂移。
	private double anchorPosition;
	private double elapsedTicks;
	private final boolean looping;
	private State state = State.PLAYING;
	private State pausedState = State.PLAYING;
	private boolean completed;

	public PhotonAnimationClock(double duration, boolean looping) {
		setDuration(duration);

		if (looping && duration == 0) {
			throw new IllegalArgumentException();
		}

		this.looping = looping;
	}

	public void setDuration(double duration) {
		PhotonAnimationDataUtil.duration(duration);

		if (looping && duration == 0) {
			throw new IllegalArgumentException();
		}

		this.duration = duration;
		anchorPosition = position;
		elapsedTicks = 0;
	}

	/// 父时间轴精确驱动归一化位置；完成由片段边界单独提交，拖动不伪造事件。
	public void seek(double position) {
		PhotonAnimationDataUtil.duration(position);
		this.position = position;
		anchorPosition = position;
		elapsedTicks = 0;
	}

	public void complete() {
		position = 1;
		anchorPosition = position;
		elapsedTicks = 0;
		state = State.HOLDING;
	}

	public double advance(double ticks) {
		var previous = position;

		if (state != State.PLAYING) {
			return previous;
		}

		PhotonAnimationDataUtil.duration(Math.max(0, ticks));
		elapsedTicks = Math.min(Double.MAX_VALUE, elapsedTicks + Math.max(0, ticks));
		position = duration == 0 ? 1 : Math.min(Double.MAX_VALUE, anchorPosition + elapsedTicks / duration);

		if (!looping && position >= 1) {
			position = 1;
			state = State.HOLDING;
		}

		return previous;
	}

	public boolean takeCompletion() {
		if (looping || state != State.HOLDING || completed) {
			return false;
		}

		completed = true;
		return true;
	}

	public void pause() {
		if (state == State.PAUSED || state == State.STOPPED) {
			return;
		}

		pausedState = state;
		state = State.PAUSED;
	}

	public void resume() {
		if (state == State.PAUSED) {
			state = pausedState;
		}
	}

	public void stop() {
		state = State.STOPPED;
	}

	public PreviewState previewState() {
		return new PreviewState(duration, position, anchorPosition, elapsedTicks, state, pausedState, completed);
	}

	/// 不推进时间、不生成完成事件；先完整校验，再原样恢复当前周期和累计时间。
	public void restorePreview(PreviewState snapshot) {
		PhotonAnimationDataUtil.duration(snapshot.duration());
		PhotonAnimationDataUtil.duration(snapshot.position());
		PhotonAnimationDataUtil.duration(snapshot.anchorPosition());
		PhotonAnimationDataUtil.duration(snapshot.elapsedTicks());
		Objects.requireNonNull(snapshot.state());
		Objects.requireNonNull(snapshot.pausedState());

		if (looping && snapshot.duration() == 0) {
			throw new IllegalArgumentException();
		}

		duration = snapshot.duration();
		position = snapshot.position();
		anchorPosition = snapshot.anchorPosition();
		elapsedTicks = snapshot.elapsedTicks();
		state = snapshot.state();
		pausedState = snapshot.pausedState();
		completed = snapshot.completed();
	}

	public double samplePosition(double partialTicks) {
		if (state != State.PLAYING || duration == 0) {
			return position;
		}

		var value = Math.min(Double.MAX_VALUE, position + Math.max(0, partialTicks) / duration);
		return looping ? value : Math.min(1, value);
	}

	public double position() {
		return position;
	}

	public double duration() {
		return duration;
	}

	public long loops() {
		return loops(0);
	}

	/// 按当前播放状态查询部分刻后的完整周期数，不推进时钟。
	public long loops(double partialTicks) {
		return looping ? (long) Math.floor(samplePosition(partialTicks)) : 0;
	}

	public double progress() {
		return progress(0);
	}

	/// 按当前播放状态查询部分刻后的周期内进度，不推进时钟。
	public double progress(double partialTicks) {
		var sampled = samplePosition(partialTicks);
		return looping ? sampled - Math.floor(sampled) : sampled;
	}

	public boolean finished() {
		return completed || state == State.HOLDING || state == State.STOPPED
				|| state == State.PAUSED && pausedState == State.HOLDING;
	}

	public State state() {
		return state;
	}
}
