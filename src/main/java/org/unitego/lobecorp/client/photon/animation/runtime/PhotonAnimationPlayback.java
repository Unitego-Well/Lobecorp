package org.unitego.lobecorp.client.photon.animation.runtime;

import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationNativePlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// 一个动画的一次播放；形参按动画签名校验并以稳定 ID 保存。
public class PhotonAnimationPlayback {
	private record Follow(WeakReference<PhotonAnimationPlayback> source, String parameter) {
	}

	public final PhotonAnimationDefinition definition;
	public final PhotonAnimationClock clock;
	public final long order;
	public @Nullable PhotonAnimationNativePlayer nativePlayer;
	public boolean timelinePreview;
	private final double authoredDuration;
	/// 原生预览先求值再推进，制作时间单位为 tick。
	private double previewNextTime;
	private double previewSampleTime;
	private boolean previewTicked;
	private final boolean looping;
	private final Map<String, float[]> parameters = new HashMap<>();
	private final Map<String, Follow> following = new HashMap<>();
	/// 播放结束时的形参快照，用于跟随冻结；后续改动保持末帧时不再传播。
	private @Nullable Map<String, float[]> endedParameters;
	private @Nullable PhotonAnimationRuntime owner;
	private @Nullable Object controlOwner;
	/// 父播放器暂停音频时，仅静默该片段仍拥有的受控播放。
	private boolean controlAudioPaused;
	private @Nullable PhotonAnimationDefinition.Completion controlCompletion;
	private double controlFramePosition;
	private boolean durationOverridden;
	private boolean disposed;
	private boolean refreshing;

	public PhotonAnimationPlayback(PhotonAnimationDefinition definition, double duration, long order,
	                               Map<String, float[]> localParameters) {
		this(definition, duration, order, localParameters, definition.loop);
	}

	public PhotonAnimationPlayback(PhotonAnimationDefinition definition, double duration, long order,
	                               Map<String, float[]> localParameters, boolean looping) {
		if (!Double.isFinite(duration)) {
			throw new IllegalArgumentException(definition.name);
		}

		this.definition = definition;
		this.order = order;
		authoredDuration = definition.authoredDuration();
		this.looping = looping;
		defaultParameters();
		parameters.putAll(validate(localParameters));
		durationOverridden = duration >= 0;
		clock = new PhotonAnimationClock(durationOverridden ? duration : parameterDuration(), looping);
	}

	private void defaultParameters() {
		Set<String> names = new HashSet<>();

		for (var parameter : definition.parameters) {
			var key = parameter.id.toString();

			if (parameters.containsKey(key) || !names.add(parameter.name)) {
				throw new IllegalArgumentException(parameter.name);
			}

			parameters.put(key, parameter.validate(parameter.defaults));
		}
	}

	private Map<String, float[]> validate(Map<String, float[]> values) {
		var validated = new HashMap<String, float[]>();
		values.forEach((name, value) -> {
			var parameter = definition.parameter(name);

			if (parameter == null || validated.putIfAbsent(parameter.id.toString(), parameter.validate(value)) != null) {
				throw new IllegalArgumentException(name);
			}
		});
		return validated;
	}

	public void attach(PhotonAnimationRuntime owner) {
		if (this.owner != null && this.owner != owner) {
			throw new IllegalStateException();
		}

		this.owner = owner;
	}

	public boolean current() {
		return !disposed && (owner == null || owner.isCurrent(this));
	}

	private void checkCurrent() {
		if (!current()) {
			throw new IllegalStateException(definition.name);
		}
	}

	public float[] parameter(String name) {
		checkCurrent();
		refreshParameters();
		var parameter = definition.parameter(name);

		if (parameter == null) {
			throw new IllegalArgumentException(name);
		}

		return parameters.get(parameter.id.toString()).clone();
	}

	public Map<String, float[]> parameters() {
		checkCurrent();
		refreshParameters();
		var values = new HashMap<String, float[]>();
		parameters.forEach((name, value) -> values.put(name, value.clone()));
		return Map.copyOf(values);
	}

	/// 只保存已校验形参和播放值，不带旧播放器、运行态或形参跟随来源引用。
	public PhotonAnimationRuntime.PreviewPlayback capturePreview() {
		var inputs = parameters();
		return new PhotonAnimationRuntime.PreviewPlayback(definition.id, inputs, clock.previewState(), looping,
				order, durationOverridden, controlCompletion, endedParameters);
	}

	/// 新播放尚未安装时恢复；结束形参仍冻结在原完成时刻，新增形参使用当前定义默认值。
	public void restorePreview(PhotonAnimationRuntime.PreviewPlayback snapshot) {
		var ended = snapshot.endedInputs();
		Map<String, float[]> restoredEnded = null;

		if (ended != null) {
			restoredEnded = new HashMap<>(parameters);
			restoredEnded.putAll(validate(ended));
		}

		clock.restorePreview(snapshot.clock());
		durationOverridden = snapshot.durationOverridden();
		controlCompletion = snapshot.completion();
		endedParameters = restoredEnded == null ? null : Map.copyOf(restoredEnded);
	}

	public void setParameter(String name, float... value) {
		setParameters(Map.of(name, value));
	}

	/// 完整校验后一起提交；显式写入解除对应形参的持续跟随。
	public void setParameters(Map<String, float[]> values) {
		setParameters(values, -1);
	}

	/// 继续播放的输入与时长作为一次提交，失败不改变原形参或时间进度。
	public void setParameters(Map<String, float[]> values, double duration) {
		checkCurrent();

		if (!Double.isFinite(duration) || duration >= 0 && (controlled() || looping && duration == 0)) {
			throw new IllegalArgumentException(definition.name);
		}

		var validated = validate(values);
		freezeEndedParameters();

		if (duration >= 0) {
			clock.setDuration(duration);
			durationOverridden = true;
			parameters.putAll(validated);
		} else {
			applyParameters(validated);
		}

		validated.keySet().forEach(following::remove);
	}

	public void follow(String target, PhotonAnimationPlayback source, String sourceParameter) {
		checkCurrent();

		if (source == this) {
			throw new IllegalArgumentException(target);
		}

		var parameter = definition.parameter(target);

		if (parameter == null) {
			throw new IllegalArgumentException(target);
		}

		source.checkCurrent();
		var value = parameter.validate(source.followValue(sourceParameter));
		freezeEndedParameters();
		applyParameters(Map.of(parameter.id.toString(), value));

		if (!source.clock.finished()) {
			following.put(parameter.id.toString(), new Follow(new WeakReference<>(source), sourceParameter));
		} else {
			following.remove(parameter.id.toString());
		}
	}

	/// 来源退出后保留最后有效值，解除引用；不隐式停止目标动画。
	public void refreshParameters() {
		if (refreshing) {
			throw new IllegalStateException(definition.name);
		}

		refreshing = true;

		try {
			for (var entry : Set.copyOf(following.entrySet())) {
				refresh(entry.getKey(), entry.getValue());
			}

			updateDuration();
			freezeEndedParameters();
		} finally {
			refreshing = false;
		}
	}

	private void refresh(String key, Follow follow) {
		var source = follow.source().get();

		if (source == null) {
			following.remove(key);
			return;
		}

		var parameter = definition.parameter(key);

		try {
			if (parameter == null) {
				following.remove(key);
				return;
			}

			applyParameters(Map.of(key, parameter.validate(source.followValue(follow.parameter()))));

			if (!source.current() || source.clock.finished()) {
				following.remove(key);
			}
		} catch (IllegalArgumentException | IllegalStateException invalidSource) {
			following.remove(key);
		}
	}

	private float[] followValue(String name) {
		if (current()) {
			refreshParameters();
		}

		freezeEndedParameters();
		var parameter = definition.parameter(name);

		if (parameter == null) {
			throw new IllegalArgumentException(name);
		}

		return (endedParameters == null ? parameters : endedParameters).get(parameter.id.toString()).clone();
	}

	private void freezeEndedParameters() {
		if (clock == null || !clock.finished() || endedParameters != null) {
			return;
		}

		var snapshot = new HashMap<String, float[]>();
		parameters.forEach((name, value) -> snapshot.put(name, value.clone()));
		endedParameters = Map.copyOf(snapshot);
	}

	private double parameterDuration() {
		return parameterDuration(Map.of());
	}

	private double parameterDuration(Map<String, float[]> values) {
		if (definition.durationParameter.isEmpty()) {
			return definition.playbackDuration();
		}

		var parameter = definition.parameter(definition.durationParameter);
		var selected = parameter == null ? null : values.getOrDefault(parameter.id.toString(), parameters.get(parameter.id.toString()));

		if (parameter == null || !parameter.type.isNumeric() || selected == null || selected.length != 1
				|| !Float.isFinite(selected[0]) || selected[0] < 0 || looping && selected[0] == 0) {
			throw new IllegalArgumentException(definition.durationParameter);
		}

		return selected[0];
	}

	/// 时长关联值在提交前校验，失败时保持形参和原时钟不变。
	private void applyParameters(Map<String, float[]> values) {
		if (!durationOverridden && controlOwner == null) {
			var duration = parameterDuration(values);

			if (Double.compare(duration, clock.duration()) != 0) {
				clock.setDuration(duration);
			}
		}

		parameters.putAll(values);
	}

	private void updateDuration() {
		if (!durationOverridden && controlOwner == null && clock != null) {
			var duration = parameterDuration();

			if (Double.compare(duration, clock.duration()) != 0) {
				clock.setDuration(duration);
			}
		}
	}

	public void setDuration(double duration) {
		checkCurrent();

		if (controlOwner != null) {
			throw new IllegalStateException(definition.name);
		}

		clock.setDuration(duration);
		durationOverridden = true;
	}

	public void control(Object owner, double duration) {
		checkCurrent();

		if (controlOwner != null && controlOwner != owner) {
			throw new IllegalStateException(definition.name);
		}

		controlOwner = Objects.requireNonNull(owner);
		durationOverridden = true;

		if (Double.compare(duration, clock.duration()) != 0) {
			clock.setDuration(duration);
		}
	}

	public boolean controlledBy(Object owner) {
		return current() && controlOwner != null && controlOwner == owner;
	}

	public boolean controlled() {
		return controlOwner != null;
	}

	public void setControlAudioPaused(Object owner, boolean paused) {
		if (!controlledBy(owner) || controlAudioPaused == paused) {
			return;
		}

		controlAudioPaused = paused;

		if (paused && nativePlayer != null) {
			nativePlayer.silence();
		}
	}

	public boolean controlAudioPaused() {
		return controlAudioPaused;
	}

	public void drive(Object owner, double progress) {
		if (!controlledBy(owner)) {
			throw new IllegalStateException(definition.name);
		}

		clock.seek(Math.clamp(progress, 0, 1));
		controlFramePosition = clock.position();
		refreshParameters();
	}

	public void frame(Object owner, double progress) {
		if (controlledBy(owner)) {
			controlFramePosition = Math.clamp(progress, 0, 1);
		}
	}

	public void complete(Object owner, PhotonAnimationDefinition.Completion completion) {
		if (!controlledBy(owner)) {
			return;
		}

		controlCompletion = completion;
		controlFramePosition = 1;
		clock.complete();
		freezeEndedParameters();
	}

	public void completion(PhotonAnimationDefinition.Completion completion) {
		checkCurrent();
		controlCompletion = completion;
	}

	public PhotonAnimationDefinition.Completion completion() {
		return controlCompletion == null ? definition.completion : controlCompletion;
	}

	public void dispose() {
		freezeEndedParameters();
		disposed = true;
		following.clear();
		controlOwner = null;
		owner = null;

		if (nativePlayer != null) {
			nativePlayer.close();
			nativePlayer = null;
		}
	}

	public double time(double partialTicks) {
		if (timelinePreview) {
			return previewSampleTime + (previewRunning() ? Math.max(0, partialTicks) : 0);
		}

		return progress(partialTicks) * authoredDuration;
	}

	public double advance(double ticks) {
		refreshParameters();

		if (controlled()) {
			return clock.position();
		}

		if (timelinePreview && previewRunning()) {
			previewSampleTime = previewNextTime;
			previewNextTime += Math.max(0, ticks);
			previewTicked = true;
		}

		var previous = clock.advance(ticks);
		freezeEndedParameters();
		return previous;
	}

	public void previewAt(double authoredTime) {
		if (previewTicked) {
			return;
		}

		previewSampleTime = Math.max(0, authoredTime);
		previewNextTime = previewSampleTime;
	}

	public boolean previewRunning() {
		return clock.state() == PhotonAnimationClock.State.PLAYING
				|| timelinePreview && clock.state() == PhotonAnimationClock.State.HOLDING;
	}

	public double authoredDuration() {
		return authoredDuration;
	}

	public double progress(double partialTicks) {
		if (timelinePreview) {
			return authoredDuration == 0 ? 0 : time(partialTicks) / authoredDuration;
		}

		return controlled() ? controlFramePosition : clock.progress(partialTicks);
	}

	public long loops(double partialTicks) {
		return controlled() ? 0 : clock.loops(partialTicks);
	}

	public boolean looping() {
		return looping;
	}
}
