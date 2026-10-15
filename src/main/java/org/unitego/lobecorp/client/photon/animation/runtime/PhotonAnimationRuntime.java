package org.unitego.lobecorp.client.photon.animation.runtime;

import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.ParticleTickHost;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationNativePlayer;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationInput;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPlayerAccess;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPreviewScope;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationTimelineState;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationLifecycleUtil;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationMixer;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionControlAccess;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationRule;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/// 每个 FXRuntime 独占的客户端控制器，不保存全局实例或修改资源配置。
public class PhotonAnimationRuntime implements AutoCloseable {
	public record Event(PhotonAnimationRule.Event type, String animation, String channel,
	                    String name, CompoundTag data, Map<String, float[]> inputs) {
		public Event {
			data = data.copy();
			inputs = copyValues(inputs);
		}

		public Event(PhotonAnimationRule.Event type, String animation, String channel, String name, CompoundTag data) {
			this(type, animation, channel, name, data, Map.of());
		}
	}

	/// 项目组合测试的内存快照；形参数组始终复制，不保存资源或旧运行实例引用。
	public record PreviewPlayback(UUID animation, Map<String, float[]> inputs, PhotonAnimationClock.PreviewState clock,
	                              boolean looping, long order, boolean durationOverridden,
	                              @Nullable PhotonAnimationDefinition.Completion completion,
	                              @Nullable Map<String, float[]> endedInputs) {
		public PreviewPlayback {
			Objects.requireNonNull(animation);
			Objects.requireNonNull(clock);
			inputs = copyValues(inputs);
			endedInputs = endedInputs == null ? null : copyValues(endedInputs);
		}

		@Override
		public Map<String, float[]> inputs() {
			return copyValues(inputs);
		}

		@Override
		public @Nullable Map<String, float[]> endedInputs() {
			return endedInputs == null ? null : copyValues(endedInputs);
		}
	}

	private record PendingEvent(Event event, @Nullable PhotonAnimationPlayback source, Map<String, float[]> inputs) {
	}

	private static Map<String, float[]> copyValues(Map<String, float[]> values) {
		var copied = new HashMap<String, float[]>();
		values.forEach((name, value) -> copied.put(name, value.clone()));
		return Map.copyOf(copied);
	}

	private final FXRuntime runtime;
	private final PhotonAnimationController controller;
	private final PhotonAnimationLibrary library;
	private final PhotonAnimationMixer mixer;
	private final PhotonAnimationTimelineState timelineState;
	private final Map<FXObject, BooleanSupplier> originalRetention = new HashMap<>();
	private boolean previewDispatch;
	private boolean previewRecording;
	private boolean editorPreview;
	private boolean serializing;
	/// 本实例测试片段可能启动或释放同一控制器中的动画；延后重采样以保护当前合成作用域。
	private boolean applying;
	private boolean applyPending;
	private boolean replacingPreview;
	private final ArrayDeque<Runnable> afterApply = new ArrayDeque<>();
	private final Map<UUID, PhotonAnimationPlayback> playing = new LinkedHashMap<>();
	/// 恢复的原生播放器先无事件定位，避免初次求值补发已经经过的零时刻信号。
	private final HashSet<PhotonAnimationPlayback> restoredPreviews = new HashSet<>();
	private final ArrayDeque<PendingEvent> events = new ArrayDeque<>();
	private final List<String> problems = new ArrayList<>();
	private final FXObject root;
	private final @Nullable Runnable previousTick;
	private final @Nullable Consumer<Float> previousFrame;
	private final Runnable tickCallback = this::tick;
	private final Consumer<Float> frameCallback = this::frame;
	private boolean started;
	private boolean closed;
	private long sequence;
	private double effectTime;
	private DoubleSupplier timeScale = () -> 1;

	public PhotonAnimationRuntime(FXRuntime runtime, PhotonAnimationController controller) {
		this.runtime = runtime;
		this.controller = controller;
		library = controller.library();
		library.requireMigrated();

		mixer = new PhotonAnimationMixer(runtime, this::recordProblem);
		timelineState = new PhotonAnimationTimelineState(runtime);
		root = (FXObject) runtime.getRoot();
		previousTick = root.getOnUpdateTick();
		previousFrame = root.getOnUpdateFrame();
		root.setOnUpdateTick(tickCallback);
		root.setOnUpdateFrame(frameCallback);
		controller.attach(this);
		PhotonAnimationLifecycleUtil.track(this);
		runtime.objects.values().forEach(object -> {
			if (object instanceof FXObject target) {
				retain(target);
			}
		});
	}

	private void begin() {
		if (started || closed) {
			return;
		}

		started = true;
		playDefault();
		events.addFirst(new PendingEvent(new Event(PhotonAnimationRule.Event.START, "", "", "", new CompoundTag()), null, Map.of()));
	}

	private void playDefault() {
		if (library.defaultAnimation.isEmpty()) {
			return;
		}

		if (library.find(library.defaultAnimation) == null) {
			recordProblem(library.defaultAnimation);
			return;
		}

		if (playback(library.defaultAnimation) == null) {
			play(library.defaultAnimation);
		}
	}

	private void tick() {
		releasePose();
		timelineState.begin(timelines(), this::timelineTime);

		try {
			tickTimelines();
		} finally {
			timelineState.end();
		}
	}

	private void tickTimelines() {
		if (previousTick != null) {
			runNative(previousTick);
		}

		if (closed) {
			return;
		}

		var dt = delta();

		if (dt <= 0) {
			if (started) {
				apply();
			}

			return;
		}

		if (!started) {
			begin();
			drain();

			if (!closed) {
				apply();
			}

			return;
		}

		effectTime += dt;

		for (var animation : List.copyOf(playing.values())) {
			var previous = animation.advance(dt);
			markers(animation, previous, animation.clock.position());
		}

		apply();
		var released = completeAnimations();
		var hadEvents = !events.isEmpty();
		drain();

		if (!closed && (released || hadEvents)) {
			apply();
		}
	}

	private boolean completeAnimations() {
		boolean released = false;

		for (var animation : List.copyOf(playing.values())) {
			if (!animation.clock.takeCompletion()) {
				continue;
			}

			queue(new Event(PhotonAnimationRule.Event.COMPLETE, animation.definition.id.toString(),
					"", "", new CompoundTag()));

			if (!animation.timelinePreview && animation.completion() == PhotonAnimationDefinition.Completion.RELEASE) {
				playing.remove(animation.definition.id);
				restoredPreviews.remove(animation);
				animation.dispose();
				released = true;
			}
		}

		return released;
	}

	private void frame(float partialTicks) {
		releasePose();

		if (previousFrame != null) {
			runNative(() -> previousFrame.accept(partialTicks));
		}

		if (started && !closed) {
			apply(partialTicks * delta(), false);
		}
	}

	/// 设置实例宿主的模拟倍率；普通 FX 默认每个引擎 tick 推进一个动画 tick。
	public void setTimeScale(DoubleSupplier supplier) {
		checkOpen();
		timeScale = Objects.requireNonNull(supplier);
	}

	private double delta() {
		var value = timeScale.getAsDouble();
		return Double.isFinite(value) ? Math.max(0, value) : 0;
	}

	/// 与逐帧属性采样共用实例倍率，返回命名动画在原始时间轴上的插值时间（tick）。
	public double playbackTime(String idOrName, float partialTicks) {
		var animation = playback(idOrName);

		if (animation == null) {
			throw new IllegalArgumentException(idOrName);
		}

		return playbackTime(animation, partialTicks);
	}

	public double playbackTime(PhotonAnimationPlayback animation, float partialTicks) {
		checkPlayback(animation);
		return animation.time(partialTicks * delta());
	}

	private void apply() {
		apply(0, true);
	}

	private boolean recordingFrozen() {
		return editorPreview && previewRecording;
	}

	private void releasePose() {
		if (!recordingFrozen()) {
			mixer.release();
		}
	}

	@SuppressWarnings("unused") // 作用域仅用于冻结属性调用和自动恢复调用范围。
	private void runNative(Runnable action) {
		if (!recordingFrozen()) {
			action.run();
			return;
		}

		try (var scope = mixer.recordingScope()) {
			action.run();
		}
	}

	private void apply(double partialTicks, boolean evaluate) {
		if (closed) {
			return;
		}

		if (applying) {
			applyPending = true;
			return;
		}

		applying = true;

		try {
			var remaining = PhotonAnimationDataUtil.EVENT_BUDGET;
			do {
				applyPending = false;
				applyPass(partialTicks, evaluate);
			} while (!closed && applyPending && --remaining > 0);
		} finally {
			applying = false;
			applyPending = false;
		}

		var remaining = PhotonAnimationDataUtil.EVENT_BUDGET;

		while (!closed && !afterApply.isEmpty() && remaining-- > 0) {
			afterApply.removeFirst().run();
		}
	}

	private void applyPass(double partialTicks, boolean evaluate) {
		var sorted = orderedPlaybacks();

		try {
			mixer.begin(effectTime, partialTicks, editorPreview && previewRecording);
			applyNative(sorted, partialTicks, evaluate);
			mixer.commit();
		} catch (IllegalArgumentException failure) {
			recordProblem(failure.getMessage());
		}
	}

	@SuppressWarnings({"resource", "unused"}) // 播放实例拥有原生播放器；采样作用域仅用于自动关闭。
	private void applyNative(List<PhotonAnimationPlayback> poses, double partialTicks, boolean evaluate) {
		var ownedScope = evaluate && !timelineState.coordinating();

		if (ownedScope) {
			timelineState.begin(timelines(), this::timelineTime);
			var original = (PhotonAnimationPlayerAccess) runtime.timelinePlayer;
			runNative(() -> original.lobecorp$evaluate(original.lobecorp$lastEvalTime()));
		}

		try {
			if (evaluate) {
				poses.forEach(this::nativePlayer);
				timelineState.ownership(timelines(), this::timelineTime);
			}

			for (var pose : poses) {
				if (!isCurrent(pose)) {
					continue;
				}

				var player = nativePlayer(pose);

				if (player == null) {
					if (!recordingFrozen()) {
						mixer.samplePose(pose);
					}

					continue;
				}

				player.player().setRecording(editorPreview && previewRecording);

				try (var sampling = recordingFrozen() ? mixer.recordingScope() : mixer.scope(pose)) {
					if (evaluate) {
						if (restoredPreviews.remove(pose)) {
							player.sync(false, true, false);
						}

						player.sync(dispatching(), editorPreview || PhotonAnimationPreviewScope.active(),
								!editorPreview || previewDispatch || PhotonAnimationPreviewScope.active());
					} else {
						player.frame(partialTicks);
					}
				}
			}
		} finally {
			if (ownedScope) {
				timelineState.end();
			}
		}
	}

	private @Nullable PhotonAnimationNativePlayer nativePlayer(PhotonAnimationPlayback pose) {
		if (pose.nativePlayer == null) {
			var effect = root.getEffectExecutor();

			if (effect == null) {
				return null;
			}

			pose.nativePlayer = new PhotonAnimationNativePlayer(runtime, pose, effect);
		}

		return pose.nativePlayer;
	}

	private List<Timeline> timelines() {
		var result = new ArrayList<Timeline>();
		result.add(runtime.fxData.timeline());

		for (var animation : orderedPlaybacks()) {
			result.add(animation.nativePlayer == null ? animation.definition.timeline() : animation.nativePlayer.timeline());
		}

		return result;
	}

	private double timelineTime(Timeline timeline) {
		if (timeline == runtime.fxData.timeline()) {
			var original = (PhotonAnimationPlayerAccess) runtime.timelinePlayer;
			return original.lobecorp$sampleTime(original.lobecorp$lastEvalTime());
		}

		for (var animation : orderedPlaybacks()) {
			var active = animation.nativePlayer == null ? animation.definition.timeline() : animation.nativePlayer.timeline();

			if (active == timeline) {
				return animation.time(0);
			}
		}

		return Double.NEGATIVE_INFINITY;
	}

	private List<PhotonAnimationPlayback> orderedPlaybacks() {
		return playing.values().stream().sorted(Comparator
				.comparingInt((PhotonAnimationPlayback value) -> value.definition.priority)
				.thenComparingLong(value -> value.order)).toList();
	}

	public PhotonAnimationTimelineState timelineState() {
		return timelineState;
	}

	public void retain(FXObject target) {
		if (originalRetention.containsKey(target)) {
			return;
		}

		var original = target.getKeepAlive();
		originalRetention.put(target, original);
		target.setKeepAlive(() -> original != null && original.getAsBoolean() || hasFuture());
	}

	private boolean hasFuture() {
		// 编辑器测试定义不在动画库中；编辑预览同样保留对象，直到显式结束。
		return !closed && (editorPreview || !library.animations.isEmpty());
	}

	private void recordProblem(@Nullable String problem) {
		var value = problem == null ? PhotonAnimationRuntime.class.getSimpleName() : problem;

		if (!problems.contains(value)) {
			problems.add(value);
		}
	}

	public PhotonAnimationPlayback play(String name) {
		return play(name, Map.of());
	}

	public PhotonAnimationPlayback play(String name, Map<String, float[]> inputs) {
		return play(name, -1, true, inputs);
	}

	public boolean isCurrent(PhotonAnimationPlayback playback) {
		return !closed && playing.get(playback.definition.id) == playback;
	}

	/// 动画调用轨道仅属于项目内独立测试时间轴，不允许在可导出动画中自递归运行。
	public boolean isTestTimeline(PhotonAnimationPlayback playback) {
		return editorPreview && isCurrent(playback) && playback.timelinePreview
				&& library.find(playback.definition.id.toString()) == null;
	}

	public PhotonAnimationPlayback play(String name, double duration, boolean restart, Map<String, float[]> inputs) {
		checkOpen();
		var definition = library.find(name);

		if (definition == null) {
			throw new IllegalArgumentException(name);
		}

		var current = playing.get(definition.id);

		if (current != null && !restart) {
			current.setParameters(inputs, duration);

			current.clock.resume();
			return current;
		}

		return install(new PhotonAnimationPlayback(definition, duration, sequence++, inputs), current);
	}

	/// 受控播放只由所属父片段驱动；片段长度优先于子形参时长和循环配置。
	public PhotonAnimationPlayback playControlled(String name, double duration, Map<String, float[]> inputs, Object token) {
		checkOpen();
		Objects.requireNonNull(token);
		PhotonAnimationDataUtil.duration(duration);
		var definition = library.find(name);

		if (definition == null) {
			throw new IllegalArgumentException(name);
		}

		var current = playing.get(definition.id);
		var playback = install(new PhotonAnimationPlayback(definition, duration, sequence++, inputs, false), current);
		playback.control(token, duration);
		return playback;
	}

	private PhotonAnimationPlayback install(PhotonAnimationPlayback playback, @Nullable PhotonAnimationPlayback current) {
		if (current != null) {
			restoredPreviews.remove(current);
			current.dispose();
		}

		playback.attach(this);
		playing.put(playback.definition.id, playback);
		applyPending |= applying;

		for (var marker : playback.definition.markers) {
			if (marker.time() == 0) {
				queue(new Event(PhotonAnimationRule.Event.MARKER, playback.definition.id.toString(), "", marker.name(), marker.data()));
			}
		}

		return playback;
	}

	public void driveControlled(PhotonAnimationPlayback playback, Object token, double progress) {
		checkPlayback(playback);
		var previous = playback.clock.position();
		playback.drive(token, progress);
		markers(playback, previous, playback.clock.position());
	}

	public void completeControlled(PhotonAnimationPlayback playback, Object token,
	                               PhotonAnimationDefinition.Completion completion) {
		if (!isCurrent(playback) || !playback.controlledBy(token)) {
			return;
		}

		if (applying) {
			afterApply.add(() -> completeControlled(playback, token, completion));
			return;
		}

		var previous = playback.clock.position();
		playback.complete(token, completion);
		markers(playback, previous, playback.clock.position());
		apply();
		var released = completeAnimations();
		var hadEvents = !events.isEmpty();
		drain();

		if (!closed && (released || hadEvents)) {
			apply();
		}
	}

	public void stop(PhotonAnimationPlayback playback) {
		if (isCurrent(playback)) {
			stopPlayback(playback.definition.id);
		}
	}

	public void reportProblem(String problem) {
		recordProblem(problem);
	}

	public void stop(String name) {
		var animation = library.find(name);

		if (animation == null) {
			return;
		}

		stopPlayback(animation.id);
	}

	private void stopPlayback(UUID id) {
		var playback = playing.remove(id);

		if (playback != null) {
			restoredPreviews.remove(playback);
			playback.clock.stop();
			playback.dispose();
		}

		if (applying || replacingPreview) {
			applyPending |= applying;
			return;
		}

		mixer.release();

		if (!closed) {
			apply();
		}
	}

	public @Nullable PhotonAnimationPlayback playback(String name) {
		var definition = library.find(name);
		return definition == null ? null : playing.get(definition.id);
	}

	/// 编辑器测试定义不进入动画库，使用实际播放身份查询当前实例。
	public @Nullable PhotonAnimationPlayback playback(UUID id) {
		return playing.get(id);
	}

	/// 仅保存编辑器中普通命名播放；受控片段和制作时间轴继续由原生重模拟重建。
	public @Nullable PreviewPlayback capturePreview(UUID id) {
		var playback = playing.get(id);

		if (!editorPreview || closed || playback == null || playback.controlled() || playback.timelinePreview
				|| library.find(id.toString()) == null) {
			return null;
		}

		return playback.capturePreview();
	}

	/// 按新库的稳定身份恢复组合测试；校验成功后才替换句柄，不重新派发启动、标记或完成。
	public @Nullable PhotonAnimationPlayback restorePreview(PreviewPlayback snapshot) {
		checkOpen();

		if (!editorPreview) {
			throw new IllegalStateException();
		}

		var definition = library.find(snapshot.animation().toString());

		if (definition == null) {
			return null;
		}

		var duration = snapshot.durationOverridden() ? snapshot.clock().duration() : -1;
		var playback = new PhotonAnimationPlayback(definition, duration, snapshot.order(), snapshot.inputs(), snapshot.looping());
		playback.restorePreview(snapshot);
		var previous = playing.get(definition.id);

		if (previous != null) {
			restoredPreviews.remove(previous);
			previous.dispose();
		}

		playback.attach(this);
		playing.put(definition.id, playback);
		restoredPreviews.add(playback);
		sequence = Math.max(sequence, snapshot.order() + 1);
		releasePose();
		apply();
		return playback;
	}

	public void pause(String name) {
		pause(playback(name));
	}

	public void pause(@Nullable PhotonAnimationPlayback value) {
		if (value == null || !isCurrent(value)) {
			return;
		}

		value.clock.pause();

		if (value.nativePlayer != null) {
			value.nativePlayer.silence();
		}
	}

	public void resume(String name) {
		resume(playback(name));
	}

	public void resume(@Nullable PhotonAnimationPlayback value) {
		if (value == null || !isCurrent(value)) {
			return;
		}

		value.clock.resume();
		apply();
	}

	public void setDuration(String name, double ticks) {
		var value = playback(name);

		if (value != null) {
			value.setDuration(ticks);
		}
	}

	public void setParameter(PhotonAnimationPlayback playback, String name, float... value) {
		checkPlayback(playback);
		playback.setParameter(name, value);
	}

	public float[] parameter(PhotonAnimationPlayback playback, String name) {
		checkPlayback(playback);
		return playback.parameter(name);
	}

	public void parameters(PhotonAnimationPlayback playback, Map<String, float[]> values) {
		checkPlayback(playback);
		playback.setParameters(values);
	}

	private void checkPlayback(PhotonAnimationPlayback playback) {
		checkOpen();

		if (!isCurrent(playback)) {
			throw new IllegalStateException(playback.definition.name);
		}
	}

	public float[] property(UUID target, String property, boolean composed) {
		checkOpen();
		return mixer.query(target, property, composed);
	}

	/// 编辑模式不自动播放默认动画或派发启动规则，保留原生时间轴与粒子实例。
	public void previewOnly() {
		checkOpen();
		editorPreview = true;
		previewDispatch = false;
		started = true;
		replacingPreview = true;

		try {
			List.copyOf(playing.values()).forEach(PhotonAnimationPlayback::dispose);
		} finally {
			replacingPreview = false;
		}

		playing.clear();
		restoredPreviews.clear();
		afterApply.clear();
		events.clear();
		mixer.release();
	}

	/// 从编辑游标开始播放，不派发已经跳过的时间标记。
	public void playPreview(PhotonAnimationDefinition definition, double authoredTime) {
		checkOpen();
		var previous = playing.remove(definition.id);

		if (previous != null) {
			restoredPreviews.remove(previous);
			previous.dispose();
		}

		var span = definition.authoredDuration();
		var playback = new PhotonAnimationPlayback(definition, span, sequence++, Map.of(), false);
		playback.timelinePreview = true;
		playback.clock.advance(Math.max(0, authoredTime));
		playback.previewAt(authoredTime);
		playback.attach(this);
		playing.put(definition.id, playback);
		mixer.release();
		apply();
	}

	public void setPreviewDispatch(boolean playing) {
		previewDispatch = playing;

		if (editorPreview) {
			runtime.timelinePlayer.setSignalDispatch(playing);
			runtime.timelinePlayer.setAudioDispatch(playing);
		}

		if (!playing) {
			this.playing.values().forEach(value -> {
				if (value.nativePlayer != null) {
					value.nativePlayer.silence();
				}
			});
		}
	}

	/// 编辑器录制时冻结当前姿态及覆盖所有权，结束后由下一次合成恢复身份保护。
	public void setPreviewRecording(boolean recording) {
		previewRecording = editorPreview && recording;
	}

	public boolean previewFinished() {
		if (playing.values().stream().anyMatch(value -> value.nativePlayer == null || value.nativePlayer.hasFuture())) {
			return false;
		}

		return runtime.objects.values().stream()
				.filter(object -> !(object instanceof EmptyFXObject))
				.noneMatch(object -> object != controller && object.isPlaying());
	}

	/// 原场景模拟后保留已求值制作时间并暂停；未运行的手动姿态才映射游标。
	/// simulateTo(N) 的暂停姿态与原生一致，使用最后求值的 N-1 而非游标 N。
	public void samplePreviewAt(PhotonAnimationDefinition definition, double authoredTime) {
		var playback = playing.get(definition.id);

		if (playback == null) {
			return;
		}

		playback.previewAt(authoredTime);
		playback.clock.pause();

		if (playback.nativePlayer != null) {
			playback.nativePlayer.silence();
		}

		releasePose();
		apply(0, false);
	}

	/// 序列化共用编辑场景时移除预览贡献，结束后恢复姿态；普通实例沿用原序列化。
	public <T> T serializeBase(Supplier<T> serializer) {
		if (!editorPreview || closed || serializing) {
			return serializer.get();
		}

		if (recordingFrozen()) {
			return serializeFrozenBase(serializer);
		}

		serializing = true;

		try {
			mixer.release();
			return serializer.get();
		} finally {
			serializing = false;
			apply();
		}
	}

	private <T> T serializeFrozenBase(Supplier<T> serializer) {
		serializing = true;

		try {
			return mixer.serializeFrozenBase(serializer);
		} finally {
			serializing = false;
		}
	}

	public void trigger(String name, CompoundTag data) {
		trigger(name, data, Map.of());
	}

	/// 每次触发保存独立输入；排队期间不写入其他动画的形参。
	public void trigger(String name, CompoundTag data, Map<String, float[]> inputs) {
		queue(new Event(PhotonAnimationRule.Event.TRIGGER, "", "", name, data, inputs));
	}

	public void signal(String channel, String name, CompoundTag data) {
		signal(null, channel, name, data);
	}

	public void signal(@Nullable String animation, String channel, String name, CompoundTag data) {
		queue(new Event(PhotonAnimationRule.Event.SIGNAL, animation == null ? "" : animation, channel, name, data));
	}

	/// 同步拖动仅采样姿态；实际播放及手动触发按各自来源派发。
	public boolean dispatching() {
		return !closed && !PhotonAnimationPreviewScope.active() && (!editorPreview || previewDispatch);
	}

	private void queue(Event event) {
		if (editorPreview && !previewDispatch && !PhotonAnimationPreviewScope.active()
				&& (event.type() == PhotonAnimationRule.Event.MARKER || event.type() == PhotonAnimationRule.Event.COMPLETE
				|| event.type() == PhotonAnimationRule.Event.SIGNAL)) {
			return;
		}

		if (!closed && events.size() < PhotonAnimationDataUtil.EVENT_BUDGET) {
			var source = event.animation().isEmpty() ? null : playback(event.animation());
			var inputs = new HashMap<String, float[]>();

			if (source != null) {
				inputs.putAll(source.parameters());
			}

			inputs.putAll(event.inputs());
			events.add(new PendingEvent(event, source, copyValues(inputs)));
		}
	}

	private void markers(PhotonAnimationPlayback animation, double previous, double current) {
		if (current <= previous || animation.authoredDuration() == 0) {
			return;
		}

		for (var marker : animation.definition.markers) {
			var fraction = marker.time() / animation.authoredDuration();

			if (fraction < 0 || fraction > 1) {
				continue;
			}

			var first = animation.looping() ? Math.max(0, Math.floor(previous - fraction) + 1) : 0;
			var last = animation.looping() ? Math.floor(current - fraction) : 0;
			var cycle = first;

			for (var count = 0; count < PhotonAnimationDataUtil.EVENT_BUDGET && cycle <= last
					&& events.size() < PhotonAnimationDataUtil.EVENT_BUDGET; count++) {
				var position = cycle + fraction;

				if (position > previous && position <= current) {
					queue(new Event(PhotonAnimationRule.Event.MARKER, animation.definition.id.toString(),
							"", marker.name(), marker.data()));
				}

				if (cycle + 1 <= cycle) {
					break;
				}

				cycle++;
			}
		}
	}

	private void drain() {
		var remaining = PhotonAnimationDataUtil.EVENT_BUDGET;

		while (!events.isEmpty() && !closed && remaining-- > 0) {
			var pending = events.removeFirst();
			var event = pending.event();

			for (var rule : library.rules) {
				if (rule.event != event.type() || differs(rule.animation, event.animation())
						|| differs(rule.channel, event.channel()) || differs(rule.name, event.name())) {
					continue;
				}

				if (remaining-- <= 0 || closed) {
					break;
				}

				try {
					playRule(rule, pending);
				} catch (IllegalArgumentException | IllegalStateException invalidCall) {
					recordProblem(invalidCall.getMessage());
				}
			}
		}

		events.clear();
	}

	private boolean differs(String expected, String actual) {
		return !expected.isEmpty() && !expected.equals(actual);
	}

	private void playRule(PhotonAnimationRule rule, PendingEvent pending) {
		var definition = library.find(rule.target);

		if (definition == null) {
			throw new IllegalArgumentException(rule.target);
		}

		var inputs = new HashMap<String, float[]>();
		pending.event().inputs().forEach((name, value) -> {
			var parameter = definition.parameter(name);

			if (parameter != null) {
				inputs.put(parameter.id.toString(), value.clone());
			}
		});
		var declared = new HashSet<String>();

		for (var input : rule.inputs) {
			var parameter = definition.parameter(input.target);

			if (parameter == null || !declared.add(parameter.id.toString())) {
				throw new IllegalArgumentException(input.target);
			}

			inputs.put(parameter.id.toString(), inputValue(input, pending));
		}

		var playback = play(rule.target, rule.duration, true, inputs);

		for (var input : rule.inputs) {
			if (input.follow && !input.source.isEmpty() && pending.source() != null && pending.source().current()) {
				playback.follow(input.target, pending.source(), input.source);
			}
		}
	}

	private float[] inputValue(PhotonAnimationInput input, PendingEvent pending) {
		if (input.source.isEmpty()) {
			return input.value.clone();
		}

		var value = pending.inputs().get(input.source);

		if (value == null && pending.source() != null) {
			var parameter = pending.source().definition.parameter(input.source);

			if (parameter != null) {
				value = pending.inputs().get(parameter.id.toString());
			}
		}

		if (value == null) {
			throw new IllegalArgumentException(input.source);
		}

		return value.clone();
	}

	@SuppressWarnings("unused") // 保留外部代码显式停止生成的客户端入口，与事件播放配置分开。
	public void stopEmission(String id) {
		for (var object : runtime.getObjects().values()) {
			if ((id.isEmpty() || object.id().toString().equals(id)) && object instanceof PhotonEmissionControlAccess access) {
				timelineState.manualEmission(access, true);
			}
		}
	}

	public void finish(boolean force) {
		close();
		runtime.destroy(force);
	}

	private void checkOpen() {
		if (closed) {
			throw new IllegalStateException();
		}
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}

		closed = true;
		timeScale = () -> 1;
		PhotonAnimationLifecycleUtil.untrack(this);
		mixer.clear();
		List.copyOf(playing.values()).forEach(PhotonAnimationPlayback::dispose);
		playing.clear();
		restoredPreviews.clear();
		timelineState.close();
		originalRetention.forEach(FXObject::setKeepAlive);
		originalRetention.clear();
		events.clear();
		controller.attach(null);
		afterApply.clear();

		if (root.getOnUpdateTick() == tickCallback) {
			root.setOnUpdateTick(previousTick);
		}

		if (root.getOnUpdateFrame() == frameCallback) {
			root.setOnUpdateFrame(previousFrame);
		}
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted") // 公开状态查询保留 closed 语义。
	public boolean closed() {
		return closed;
	}

	public @Nullable ParticleTickHost host() {
		return root.getTickHost();
	}

	public double effectTime() {
		return effectTime;
	}

	public double effectTime(float partialTicks) {
		return effectTime + partialTicks * delta();
	}

	public List<String> problems() {
		return List.copyOf(problems);
	}
}
