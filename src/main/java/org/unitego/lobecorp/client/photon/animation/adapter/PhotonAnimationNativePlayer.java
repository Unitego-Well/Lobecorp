package org.unitego.lobecorp.client.photon.animation.adapter;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.TimelinePlayer;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationPlayback;
import org.unitego.lobecorp.util.photon.model.PhotonModelAnimationTimelineUtil;

/// 每个播放实例复用一份完整原生时间轴，属性通过原生采样作用域参与合成。
public class PhotonAnimationNativePlayer implements AutoCloseable {
	private final FXRuntime runtime;
	private final PhotonAnimationPlayback playback;
	private final Timeline timeline;
	private final TimelinePlayer player;
	private final PhotonAnimationPlayerAccess access;
	private final IEffectExecutor effect;
	private long cycle;
	private boolean initialized;
	private boolean audio;
	private boolean live;
	private boolean signals;
	private boolean closed;

	public PhotonAnimationNativePlayer(FXRuntime runtime, PhotonAnimationPlayback playback, IEffectExecutor effect) {
		this.runtime = runtime;
		this.playback = playback;
		this.effect = effect;
		timeline = playback.definition.timeline().copy();
		player = new TimelinePlayer(runtime, timeline);
		access = (PhotonAnimationPlayerAccess) player;
		access.lobecorp$animation(playback.definition.id.toString());
		PhotonChildAnimationPlayer.register(player, playback);
		PhotonModelAnimationTimelineUtil.register(player, playback);
	}

	public Timeline timeline() {
		return timeline;
	}

	/// 借用播放器；生命周期仍归本实例，调用者不得单独关闭。
	public TimelinePlayer player() {
		return player;
	}

	public void sync(boolean live, boolean editorPreview) {
		sync(live, editorPreview, live);
	}

	/// 重模拟保留内部信号编排；外部回调由预览作用域门控，音频仍静默。
	public void sync(boolean live, boolean editorPreview, boolean internalSignals) {
		if (closed) {
			return;
		}

		this.live = live;
		signals = internalSignals;
		player.setEditorPreview(editorPreview);
		var nextCycle = playback.loops(0);
		var time = playback.time(0);

		if (!initialized) {
			restart(nextCycle);
			access.lobecorp$lastSignalTick(signals && time == 0 ? -1 : time);
		}

		player.setSignalDispatch(signals);
		updateAudio();

		while (cycle < nextCycle) {
			access.lobecorp$evaluateAt(playback.authoredDuration());
			restart(cycle + 1);
			updateAudio();
		}

		access.lobecorp$evaluateAt(time);

		if (!signals) {
			access.lobecorp$lastSignalTick(time);
		}
	}

	private void updateAudio() {
		// 遵循当前 FX 的音频派发策略，独立无声夹具不会访问客户端声卡。
		var nextAudio = live && playback.previewRunning() && !playback.controlAudioPaused()
				&& ((PhotonAnimationPlayerAccess) runtime.timelinePlayer).lobecorp$audioEnabled();

		if (nextAudio != audio) {
			audio = nextAudio;
			player.setAudioDispatch(audio);
		}
	}

	private void restart(long cycle) {
		this.cycle = cycle;
		player.setSignalDispatch(false);
		player.setAudioDispatch(false);
		audio = false;
		player.begin(effect);
		access.lobecorp$duration(playback.authoredDuration());
		initialized = true;

		if (signals) {
			player.setSignalDispatch(true);
			access.lobecorp$lastSignalTick(-1);
		}
	}

	public void frame(double partialTicks) {
		frameAt(playback.time(partialTicks));
	}

	public void frameAt(double time) {
		if (!closed && initialized) {
			access.lobecorp$frameAt(time);
		}
	}

	public boolean hasFuture() {
		return !closed && (playback.looping() || playback.time(0) < playback.authoredDuration());
	}

	public void silence() {
		player.setAudioDispatch(false);
		audio = false;
		PhotonChildAnimationPlayer.pause(player, true);
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}

		closed = true;
		player.stop();
		PhotonChildAnimationPlayer.forget(player);
		PhotonModelAnimationTimelineUtil.forget(player);
	}
}
