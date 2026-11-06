package org.unitego.lobecorp.client.photon.playback;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.PhotonSignals;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.gui.editor.FXProjectEffectExecutor;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Binding;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Definition;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Blend;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Completion;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Event;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Input;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.ValueSource;
import org.unitego.lobecorp.util.photon.model.PhotonModelClipUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonRuntimePreviewUtil;
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

import javax.annotation.Nullable;

import org.unitego.lobecorp.client.photon.editor.PhotonAuthoringPose;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// 一个FXRuntime拥有一个命名运行时；纯句柄、原生属性和最终写入分别处理。
public class PhotonNamedRuntime implements AutoCloseable {
	public record Notification(Event event, @Nullable PhotonNamedHandle source, String channel, String name,
	                           CompoundTag data) {
		public Notification {
			data = data.copy();
		}

		@Override
		public CompoundTag data() {
			return data.copy();
		}
	}

	private static final int EVENT_BUDGET = 256;

	private static class Entry {
		private final PhotonNamedHandle handle;
		private final PhotonNamedPlan plan;
		private double pendingTicks;
		private double notifiedPosition = -Double.MIN_VALUE;
		private Completion completion;
		private boolean authoring;
		private final PhotonChildWindows children;
		private final PhotonNamedNativeTracks nativeTracks;
		private final PhotonModelClipUtil.ContributionState modelContributions = new PhotonModelClipUtil.ContributionState();

		private Entry(PhotonNamedHandle handle, PhotonNamedPlan plan, FXRuntime runtime, @Nullable PhotonNamedRuntime callHost) {
			this.handle = handle;
			this.plan = plan;
			completion = handle.definition().completion();
			children = new PhotonChildWindows(runtime, handle, callHost);
			nativeTracks = new PhotonNamedNativeTracks(runtime, plan.timeline, plan.leaves, plan.hasAudio(), plan.hasPostProcess());
		}
	}

	private final FXRuntime runtime;
	private final PhotonNamedController controller;
	private PhotonNamedLibrary library;
	private @Nullable CompoundTag librarySource;
	private @Nullable PhotonNamedController.CompiledDefinition compiledDefinition;
	private long controllerRevision;
	private boolean controllerMatches = true;
	private final UUID id = UUID.randomUUID();
	private final PhotonNamedMixer mixer = new PhotonNamedMixer();
	private final PhotonNamedGates gates;
	private final PhotonNamedNativeTracks.Speeds speeds;
	private final Map<String, PhotonNamedPlan> plans = new LinkedHashMap<>();
	private final Map<UUID, Entry> playing = new LinkedHashMap<>();
	private @Nullable List<Entry> orderedEntries = List.of();
	private final Map<FXObject, BooleanSupplier> retention = new LinkedHashMap<>();
	private final ArrayDeque<Notification> events = new ArrayDeque<>();
	private final FXObject root;
	private final @Nullable Runnable previousTick;
	private final @Nullable Consumer<Float> previousFrame;
	private final Runnable tickCallback = this::tick;
	private final Consumer<Float> frameCallback = this::frame;
	private Consumer<Notification> listener = _ -> {
	};
	private long sequence;
	private double effectTime;
	private double pendingEffectTicks;
	private boolean dispatching;
	private boolean closed;
	private boolean applying;
	private long revision;
	private @Nullable String failure;
	private @Nullable Entry preview;

	public PhotonNamedRuntime(FXRuntime runtime, PhotonNamedController controller) throws IOException {
		this.runtime = runtime;
		this.controller = controller;
		var compiled = controller.compiledDefinition();
		compiledDefinition = compiled;
		library = compiled == null ? PhotonNamedController.readLibrary(controller.definition()) : compiled.library();
		librarySource = compiled == null ? library.source() : null;
		controllerRevision = controller.definitionRevision();
		gates = new PhotonNamedGates(runtime);
		speeds = new PhotonNamedNativeTracks.Speeds(runtime);
		root = (FXObject) runtime.root;

		for (var object : runtime.objects.values()) {
			if (object instanceof PhotonModelOutput model) {
				model.prepare();
			}
		}

		plans.putAll(preparePlans(runtime, library, compiled));
		previousTick = root.getOnUpdateTick();
		previousFrame = root.getOnUpdateFrame();
		root.setOnUpdateTick(tickCallback);
		root.setOnUpdateFrame(frameCallback);
		runtime.objects.values().forEach(object -> {
			if (object instanceof FXObject target) {
				retain(target);
			}
		});
		controller.attach(this);
	}

	public UUID id() {
		return id;
	}

	public UUID controllerId() {
		return controller.id();
	}

	public PhotonNamedLibrary library() {
		return library;
	}

	/// 返回与 play 相同的已编译计划定义，供调用方投影形参而不重复扫描动画列表。
	public @Nullable Definition definition(String name) {
		var plan = plans.get(name);
		return plan == null ? null : plan.definition;
	}

	public boolean matchesController() {
		if (closed) {
			return false;
		}

		if (controllerRevision != controller.definitionRevision()) {
			controllerMatches = compiledDefinition == null ? controller.matchesDefinition(librarySource)
					: compiledDefinition.matches(controller);
			controllerRevision = controller.definitionRevision();
		}

		return controllerMatches;
	}

	public List<PhotonNamedHandle> playbacks() {
		return playing.values().stream().map(entry -> entry.handle).toList();
	}

	public long revision() {
		return revision;
	}

	public PhotonAuthoringPose authoredPose(FXObject object, PhotonAuthoringPose current) {
		return mixer.authoredPose(object, current);
	}

	public void pause(PhotonNamedHandle handle, boolean paused) {
		current(handle);
		if (handle.controlled()) {
			throw new IllegalArgumentException("受控播放由所属时间轴驱动。");
		}

		if (paused) {
			handle.clock().pause();
		} else {
			handle.clock().resume();
		}

		revision++;
		refresh();
	}

	/// 定位只更新该命名句柄的制作游标，不伪造粒子或世界的历史模拟。
	public void seek(PhotonNamedHandle handle, double authoredTime) {
		var entry = current(handle);
		if (handle.controlled() || !Double.isFinite(authoredTime) || authoredTime < 0
				|| handle.authoredDuration() == 0 && authoredTime != 0) {
			throw new IllegalArgumentException("命名制作定位时间无效，受控播放须使用所属时间轴。");
		}

		double position = handle.authoredDuration() == 0 ? 0 : authoredTime / handle.authoredDuration();
		if (!Double.isFinite(position)) {
			throw new IllegalArgumentException("命名制作定位超出有效范围。");
		}

		handle.clock().seek(handle.definition().loop() ? position : Math.min(1, position));
		entry.pendingTicks = 0;
		entry.notifiedPosition = handle.position(0);
		revision++;
		refresh();
	}

	public void parameters(PhotonNamedHandle handle, Map<String, List<Float>> changes) {
		current(handle);
		handle.parameters(changes);
		revision++;
		refresh();
	}

    /// 在原生下一次模拟或帧采样中统一消费连续输入，不在每个字段更新时重跑整条时间线。
    public void stageParameters(PhotonNamedHandle handle, Map<String, List<Float>> changes) {
        current(handle);
        handle.parameters(changes);
        revision++;
    }

	public void duration(PhotonNamedHandle handle, double duration) {
		current(handle);
		handle.duration(duration);
		revision++;
		refresh();
	}

	/// 仅更新后续播放和事件使用的库；现有句柄继续使用其准备时的定义和计划。
	public void redefine(CompoundTag source) throws IOException {
		requireOpen();
		if (compiledDefinition == null ? librarySource != null && librarySource.equals(source) : compiledDefinition.matchesDefinition(source)) {
			return;
		}

		var compiled = compiledDefinition == null ? null
				: PhotonNamedController.compileDefinition(source, controller.source());
		var candidate = compiled == null ? PhotonNamedController.readLibrary(source) : compiled.library();
		var prepared = preparePlans(runtime, candidate, compiled);
		if (preview != null && prepared.containsKey(preview.handle.definition().id().toString())) {
			throw new IllegalArgumentException("命名动画身份与工程测试身份冲突。");
		}

		library = candidate;
		compiledDefinition = compiled;
		librarySource = compiled == null ? candidate.source() : null;
		controllerMatches = compiledDefinition == null ? controller.matchesDefinition(librarySource)
				: compiledDefinition.matches(controller);
		controllerRevision = controller.definitionRevision();
		plans.clear();
		plans.putAll(prepared);
		revision++;
	}

	public static void validateLibrary(FXRuntime runtime, CompoundTag source) throws IOException {
		preparePlans(runtime, PhotonNamedController.readLibrary(source), null);
	}

	private static Map<String, PhotonNamedPlan> preparePlans(FXRuntime runtime, PhotonNamedLibrary library,
	                                                         @Nullable PhotonNamedController.CompiledDefinition compiled) {
		var prepared = new LinkedHashMap<String, PhotonNamedPlan>();
		for (var definition : library.animations()) {
			var plan = compiled == null ? new PhotonNamedPlan(Platform.getFrozenRegistry(), runtime, definition)
					: new PhotonNamedPlan(runtime, compiled.template(definition.id()));
			prepared.put(definition.name(), plan);
			prepared.put(definition.id().toString(), plan);
		}

		validateReferences(library, prepared);
		return prepared;
	}

	public void begin() {
		if (!library.defaultAnimation().isEmpty()) {
			play(library.defaultAnimation(), -1, true, Map.of());
		}

		refresh();
	}

	public PhotonNamedHandle play(String name, double duration, boolean restart, Map<String, List<Float>> inputs) {
		return play(name, duration, restart, inputs, false);
	}

	private PhotonNamedHandle play(String name, double duration, boolean restart, Map<String, List<Float>> inputs, boolean controlled) {
		requireOpen();
		var plan = plans.get(name);

		if (plan == null) {
			throw new IllegalArgumentException("命名动画不存在：" + name);
		}

		var current = playing.get(plan.definition.id());

		if (current != null && !restart) {
			current.handle.parameters(inputs);
			revision++;
			refresh();
			return current.handle;
		}

		var handle = new PhotonNamedHandle(plan.definition, plan.authoredDuration, sequence++, duration,
				plan.definition.loop(), controlled, inputs);

		if (current != null) {
			release(current);
		}

		playing.put(plan.definition.id(), new Entry(handle, plan, runtime, null));
		orderedEntries = null;
		revision++;
		queue(new Notification(Event.START, handle, "", "", new CompoundTag()));
		drainEvents();
		refresh();
		return handle;
	}

	public PhotonNamedHandle playControlled(String name, double duration, Map<String, List<Float>> inputs) {
		return play(name, duration, true, inputs, true);
	}

	/// 工程专用测试定义不进入发布动画索引；调用者保管原工程，运行时只持有独立准备数据。
	public PhotonNamedHandle preview(Definition definition, double authoredTime) {
		requireOpen();

		if (!(root.getEffectExecutor() instanceof FXProjectEffectExecutor) || !Double.isFinite(authoredTime)
				|| authoredTime < 0 || plans.containsKey(definition.id().toString())) {
			throw new IllegalArgumentException("工程测试定义身份或制作时间无效。");
		}

		var plan = new PhotonNamedPlan(Platform.getFrozenRegistry(), runtime, definition, true);
		var handle = new PhotonNamedHandle(definition, plan.authoredDuration, sequence++, plan.authoredDuration,
				false, true, Map.of());
		handle.drive(Math.clamp(authoredTime / plan.authoredDuration, 0, 1));
		stopPreview();
		var entry = new Entry(handle, plan, runtime, this);
		entry.completion = Completion.HOLD;
		entry.notifiedPosition = handle.position(0);
		preview = entry;
		playing.put(definition.id(), entry);
		orderedEntries = null;
		revision++;
		refresh();
		return handle;
	}

	/// 每个制作工作区拥有独立的临时身份，不替换发布动画或其他工作区的贡献。
	public PhotonNamedHandle previewAuthoring(Definition definition, double authoredTime) {
		requireOpen();
		if (!(root.getEffectExecutor() instanceof FXProjectEffectExecutor) || !Double.isFinite(authoredTime) || authoredTime < 0) {
			throw new IllegalArgumentException("制作预览来源或时间无效。");
		}

		var source = definition.source();
		source.putString("id", UUID.randomUUID().toString());
		try {
			var isolated = PhotonProjectPreviewUtil.read(source);
			var plan = new PhotonNamedPlan(Platform.getFrozenRegistry(), runtime, isolated, true);
			var handle = new PhotonNamedHandle(isolated, definition.id(), plan.authoredDuration, sequence++, plan.authoredDuration, false, true, Map.of());
			handle.drive(Math.clamp(authoredTime / plan.authoredDuration, 0, 1));
			var entry = new Entry(handle, plan, runtime, this);
			entry.authoring = true;
			entry.completion = Completion.HOLD;
			entry.notifiedPosition = handle.position(0);
			playing.put(isolated.id(), entry);
			orderedEntries = null;
			revision++;
			refresh();
			return handle;
		} catch (IOException invalid) {
			throw new IllegalArgumentException("制作预览定义无法读取。", invalid);
		}
	}

	public boolean ownsAuthoring(PhotonNamedHandle handle) {
		var entry = playing.get(handle.definition().id());
		return !closed && entry != null && entry.handle == handle && entry.authoring && !handle.disposed();
	}

	public UUID sourceDefinitionId(PhotonNamedHandle handle) {
		return handle.sourceDefinitionId();
	}

	public boolean ownsPlayback(PhotonNamedHandle handle) {
		var entry = playing.get(handle.definition().id());
		return !closed && entry != null && entry.handle == handle && !handle.disposed();
	}

	/// 工程调用拥有独立播放身份，不能替换另一个测试窗口或同名的全局播放。
	public PhotonNamedHandle playProjectCall(String name, double duration, boolean controlled, Map<String, List<Float>> inputs) {
		requireOpen();
		var original = plans.get(name);
		if (original == null) {
			throw new IllegalArgumentException("工程调用动画不存在：" + name);
		}
		var source = original.definition.source();
		source.putString("id", UUID.randomUUID().toString());
		try {
			var definition = PhotonProjectPreviewUtil.read(source);
			var plan = new PhotonNamedPlan(Platform.getFrozenRegistry(), runtime, definition);
			var handle = new PhotonNamedHandle(definition, original.definition.id(), plan.authoredDuration, sequence++, duration,
					definition.loop(), controlled, inputs);
			playing.put(definition.id(), new Entry(handle, plan, runtime, null));
			orderedEntries = null;
			revision++;
			queue(new Notification(Event.START, handle, "", "", new CompoundTag()));
			drainEvents();
			refresh();
			return handle;
		} catch (IOException invalid) {
			throw new IllegalArgumentException("工程调用定义无法读取。", invalid);
		}
	}

	public void seekAuthoring(PhotonNamedHandle handle, double authoredTime) {
		if (!ownsAuthoring(handle) || !Double.isFinite(authoredTime) || authoredTime < 0) {
			throw new IllegalArgumentException("制作预览句柄或时间已失效。");
		}

		var entry = current(handle);
		handle.drive(Math.clamp(authoredTime / entry.plan.authoredDuration, 0, 1));
		entry.notifiedPosition = handle.position(0);
		revision++;
		refresh();
	}

	public void seekPreview(double authoredTime) {
		requireOpen();
		var entry = preview;

		if (entry == null || !Double.isFinite(authoredTime) || authoredTime < 0) {
			throw new IllegalArgumentException("没有有效的工程测试播放或制作时间。");
		}

		entry.handle.drive(Math.clamp(authoredTime / entry.plan.authoredDuration, 0, 1));
		entry.notifiedPosition = entry.handle.position(0);
		revision++;
		refresh();
	}

	public void stopPreview() {
		var entry = preview;
		preview = null;

		if (entry != null) {
			playing.remove(entry.handle.definition().id(), entry);
			orderedEntries = null;
			release(entry);
			revision++;
			refresh();
		}
	}

	public boolean ownsPreview(PhotonNamedHandle handle) {
		return !closed && preview != null && preview.handle == handle && !handle.disposed()
				&& playing.get(handle.definition().id()) == preview && !plans.containsKey(handle.definition().id().toString());
	}

	public void completeControlled(PhotonNamedHandle handle, Completion completion) {
		var entry = current(handle);
		entry.completion = completion;
		handle.completeControlled();
		revision++;
		refresh();
	}

	public void driveControlled(PhotonNamedHandle handle, double position, boolean simulation) {
		current(handle);

		if (Double.compare(handle.progress(0), position) == 0) {
			return;
		}

		handle.drive(position);
		revision++;

		if (simulation) {
			refresh();
		} else if (!applying) {
			mixer.begin();
			sample(0, false);
		}
	}

	public void stop(PhotonNamedHandle handle) {
		requireOpen();
		var entry = current(handle);

		if (entry == preview) {
			preview = null;
		}
		playing.remove(handle.definition().id());
		orderedEntries = null;
		release(entry);
		revision++;
		refresh();
	}

	public @Nullable PhotonNamedHandle playback(String name) {
		var plan = plans.get(name);
		var entry = plan == null ? null : playing.get(plan.definition.id());
		return entry == null ? null : entry.handle;
	}

	public void trigger(String channel, String name, CompoundTag data) {
		requireOpen();
		queue(new Notification(Event.TRIGGER, null, channel, name, data));
		drainEvents();
		refresh();
	}

	public void signal(String channel, String name, CompoundTag data) {
		requireOpen();
		queue(new Notification(Event.SIGNAL, null, channel, name, data));
		drainEvents();
		refresh();
	}

	public void listener(Consumer<Notification> value) {
		listener = value;
	}

	public void retain(FXObject object) {
		if (!retention.containsKey(object)) {
			var previous = object.getKeepAlive();
			retention.put(object, previous);
			object.setKeepAlive(() -> !closed || previous != null && previous.getAsBoolean());
		}
	}

	private void tick() {
		mixer.begin();
		gates.begin();

		if (previousTick != null) {
			previousTick.run();
		}

		if (closed) {
			return;
		}

		try {
			effectTime += pendingEffectTicks;
			float rate = runtime.getRate();

			for (var entry : List.copyOf(playing.values())) {
				entry.handle.advance(entry.pendingTicks);
				entry.pendingTicks = rate;
				notifyProgress(entry);
			}

			drainEvents();
			sample(0, true);
			pendingEffectTicks = rate;
		} catch (RuntimeException invalid) {
			fail(invalid);
		}
	}

	private void frame(float partialTicks) {
		mixer.begin();

		if (previousFrame != null) {
			previousFrame.accept(partialTicks);
		}

		if (closed) {
			return;
		}

		try {
			sample(Math.max(0, partialTicks) * runtime.getRate(), false);
		} catch (RuntimeException invalid) {
			fail(invalid);
		}
	}

	private List<Entry> ordered() {
		// 排序键在 Entry 生命周期内固定；成员变更只丢弃缓存，当前采样继续持有原快照。
		if (orderedEntries == null) {
			orderedEntries = playing.values().stream().sorted(Comparator
					.comparingInt((Entry entry) -> entry.plan.definition.priority()).thenComparingLong(entry -> entry.handle.order())).toList();
		}

		return orderedEntries;
	}

	private void sample(double partial, boolean simulation) {
		if (applying || closed || !simulation && ((PhotonTimelineStateAccess) runtime.timelinePlayer).lobecorp$recording()) {
			return;
		}

		applying = true;

		try {
			for (int pass = 0; pass < EVENT_BUDGET; pass++) {
				long before = revision;
				samplePass(partial, simulation);

				if (closed || before == revision) {
					PhotonRuntimePreviewUtil.apply(runtime);
					return;
				}

				mixer.begin();

				if (simulation) {
					gates.begin();
				}
			}

			throw new IllegalStateException("命名播放切换超过单次重采样预算。");
		} finally {
			applying = false;
		}
	}

	private void refresh() {
		if (!dispatching && !applying && !closed) {
			mixer.begin();
			gates.begin();
			sample(0, true);
		}
	}

	private void samplePass(double partial, boolean simulation) {
		var entries = ordered();

		if (simulation) {
			speeds.begin();
			for (var entry : entries) {
				gates.collect(entry.plan, entry.handle, entry.handle.authoredTime(0));
			}

			gates.commit();
		}

		for (var entry : entries) {
			if (entry.handle.disposed()) {
				continue;
			}

			var handle = entry.handle;
			int priority = entry.plan.definition.priority();
			double time = handle.authoredTime(partial);
			var stage = simulation ? Stage.ANIMATION : Stage.ANIMATION_FRAME;
			int node = PhotonPerformanceTraceUtil.enabled(stage)
					? PhotonPerformanceTraceUtil.namedNode(PhotonPerformanceOwnerUtil.runtime(runtime), "named_animation",
					handle.id().toString(), handle.definition().name(), handle.definition().id().toString()) : 0;
			long trace = PhotonPerformanceTraceUtil.begin(stage, node, time);
			boolean completed = false;

			try {

				if (simulation) {
					entry.children.evaluate(entry.plan.childTracks(), time);
				} else {
					entry.children.frame(time);
				}

				entry.modelContributions.apply(handle, runtime, entry.plan.modelClips(), time, priority, handle.order());
				entry.nativeTracks.sample(time, simulation, handle.clock().state() == PhotonPlaybackClock.State.PLAYING);
				if (simulation) {
					speeds.apply(entry.plan.speedTracks(), time);
				}

				for (var property : entry.plan.properties) {
					var binding = property.binding();
					var values = switch (binding.source()) {
						case CURVE -> List.<Float>of();
						case PARAMETER -> handle.parameter(binding.parameter());
						case EFFECT_TIME -> List.of((float) (effectTime + partial));
						case ANIMATION_TIME -> List.of((float) (handle.progress(partial) * handle.clock().duration()));
						case PROGRESS -> List.of((float) handle.progress(partial));
						case LOOP_COUNT -> List.of((float) handle.loops(partial));
					};
					var scales = coefficient(handle, binding.scaleParameter(), binding.scale());
					var offsets = coefficient(handle, binding.offsetParameter(), binding.offset());
					mixer.add(property.target(), property.value(), time, handle.authoredDuration(), handle.loops(partial),
							binding, values, scales, offsets, priority, handle.order());
					autoEnable(entry, property, time, partial);
				}

				completed = true;
			} finally {
				PhotonPerformanceTraceUtil.end(trace, completed);
			}
		}

		mixer.commit();

		if (simulation) {
			speeds.finish();
			for (var entry : entries) {
				if (entry.handle.clock().takeCompletion()) {
					queue(new Notification(Event.COMPLETE, entry.handle, "", "", new CompoundTag()));

					if (entry.completion == Completion.RELEASE && playing.get(entry.handle.definition().id()) == entry) {
						playing.remove(entry.handle.definition().id());
						orderedEntries = null;
						release(entry);
						revision++;
					}
				}
			}

			drainEvents();
		}
	}

	private void autoEnable(Entry entry, PhotonNamedPlan.Property property, double time, double partial) {
		String path = property.value().type().path();
		int moduleEnd = path.indexOf('.', "config.".length());

		if (!entry.plan.definition.autoEnableModules() || !path.startsWith("config.") || moduleEnd < 0 || path.endsWith(".enable")) {
			return;
		}

		String enablePath = path.substring(0, moduleEnd) + ".enable";

		if (entry.plan.properties.stream().anyMatch(value -> value.target() == property.target()
				&& value.value().type().path().equals(enablePath))) {
			return;
		}

		var type = property.target().getFXObjectType().animatableProperties(property.target()).stream()
				.filter(value -> value.path().equals(enablePath)).findFirst().orElse(null);

		if (type != null) {
			var binding = new Binding(property.target().id(), type.key(), Blend.OVERRIDE, ValueSource.PARAMETER,
					"", -1, 1, 0, "", "");
			mixer.add(property.target(), type.create(property.target()), time, entry.handle.authoredDuration(),
					entry.handle.loops(partial), binding, List.of(1f), List.of(1f), List.of(0f),
					entry.plan.definition.priority(), entry.handle.order());
		}
	}

	private static List<Float> coefficient(PhotonNamedHandle handle, String name, float fallback) {
		return name.isEmpty() ? List.of(fallback) : handle.parameter(name);
	}

	private void notifyProgress(Entry entry) {
		double position = entry.handle.position(0);
		long from = Math.max(0, (long) Math.floor(entry.notifiedPosition));
		long to = (long) Math.floor(position);
		int count = 0;

		for (long cycle = from; cycle <= to; cycle++) {
			for (var marker : entry.plan.definition.markers()) {
				double at = cycle + marker.time() / entry.plan.authoredDuration;

				if (at > entry.notifiedPosition && at <= position && (entry.handle.clock().looping() || at <= 1)) {
					queue(new Notification(Event.MARKER, entry.handle, "", marker.name(), marker.data()));

					if (++count > EVENT_BUDGET) {
						throw new IllegalStateException("命名标记超过单次事件预算。");
					}
				}
			}

			for (var signals : entry.plan.signalTracks()) {
				if (signals.mute()) {
					continue;
				}

				for (var signal : signals.signals()) {
					double at = cycle + signal.time() / entry.plan.authoredDuration;
					if (at <= entry.notifiedPosition || at > position || !entry.handle.clock().looping() && at > 1) {
						continue;
					}
					if (++count > EVENT_BUDGET) {
						throw new IllegalStateException("命名信号超过单次事件预算。");
					}
					var channel = signals.displayName();
					queue(new Notification(Event.SIGNAL, entry.handle, channel, signal.name(), signal.data()));
					var effect = entry.nativeTracks.executor();
					if (effect != null) {
						effect.onTimelineSignal(channel, signal.name(), signal.data(), signal.time());
					}
					PhotonSignals.fire(effect, channel, signal.name(), signal.data(), signal.time());
				}
			}

			if (cycle == Long.MAX_VALUE || cycle - from >= EVENT_BUDGET) {
				if (cycle < to) {
					throw new IllegalStateException("命名播放跨越周期超过单次事件预算。");
				}

				break;
			}
		}

		entry.notifiedPosition = position;
	}

	private void queue(Notification event) {
		if (events.size() >= EVENT_BUDGET) {
			throw new IllegalStateException("命名事件队列超过预算。");
		}

		events.add(event);
	}

	private void drainEvents() {
		if (dispatching) {
			return;
		}

		dispatching = true;

		try {
			int count = 0;

			while (!events.isEmpty()) {
				if (++count > EVENT_BUDGET) {
					events.clear();
					throw new IllegalStateException("命名规则出现循环或超过单次事件预算。");
				}

				var event = events.removeFirst();
				listener.accept(event);

				for (var rule : library.rules()) {
					if (rule.event() != event.event() || !rule.channel().isEmpty() && !rule.channel().equals(event.channel())
							|| !rule.name().isEmpty() && !rule.name().equals(event.name())
							|| !rule.animation().isEmpty() && (event.source() == null
							|| !matchesSource(rule.animation(), event.source()))) {
						continue;
					}

					var handle = play(rule.target(), rule.duration(), true, inputs(rule.inputs(), event.source()));

					for (var input : rule.inputs()) {
						if (input.follow() && !input.source().isEmpty() && event.source() != null) {
							handle.follow(input.target(), event.source(), input.source());
						}
					}
				}
			}
		} finally {
			dispatching = false;
		}
	}

	private boolean matchesSource(String alias, PhotonNamedHandle source) {
		if (alias.equals(source.definition().name()) || alias.equals(source.sourceDefinitionId().toString())) {
			return true;
		}
		var current = plans.get(alias);
		return current != null && current.definition.id().equals(source.sourceDefinitionId());
	}

	public static Map<String, List<Float>> inputs(List<Input> inputs, @Nullable PhotonNamedHandle parent) {

		var result = new LinkedHashMap<String, List<Float>>();

		for (var input : inputs) {
			var value = input.source().isEmpty() ? input.value() : parent == null ? null : parent.parameter(input.source());

			if (value == null || input.target().isEmpty() || result.putIfAbsent(input.target(), value) != null) {
				throw new IllegalArgumentException("子播放形参来源或目标无效：" + input.target());
			}
		}

		return Map.copyOf(result);
	}

	private static void validateReferences(PhotonNamedLibrary library, Map<String, PhotonNamedPlan> plans) {
		if (!library.defaultAnimation().isEmpty() && !plans.containsKey(library.defaultAnimation())) {
			throw new IllegalArgumentException("默认命名动画不存在。");
		}

		for (var rule : library.rules()) {
			if (!plans.containsKey(rule.target()) || !rule.animation().isEmpty() && !plans.containsKey(rule.animation())) {
				throw new IllegalArgumentException("命名规则动画引用无效。");
			}
		}
	}

	private Entry current(PhotonNamedHandle handle) {
		var entry = playing.get(handle.definition().id());

		if (entry == null || entry.handle != handle || handle.disposed()) {
			throw new IllegalArgumentException("命名播放句柄已过期。");
		}

		return entry;
	}

	private void release(Entry entry) {
		entry.children.close();
		entry.nativeTracks.close();
		entry.modelContributions.clear(entry.handle);
		entry.handle.dispose();
	}

	private void fail(RuntimeException invalid) {
		failure = invalid.getMessage() == null ? invalid.getClass().getSimpleName() : invalid.getMessage();
		close();
		runtime.destroy(true);
		Photon.LOGGER.error("命名特效播放失败：{}", controller.getName(), invalid);
	}

	public @Nullable String failure() {
		return failure;
	}

	public int unusedBindings() {
		return plans.values().stream().distinct().mapToInt(plan -> plan.unusedBindings).sum();
	}

	public boolean closed() {
		return closed;
	}

	public void captureNativeState(Set<UUID> targets, @Nullable PhotonNamedGates activation) {
		if (!closed) {
			gates.captureNativeState(targets, activation);
		}
	}

	private void requireOpen() {
		if (closed) {
			throw new IllegalStateException("命名运行时已关闭。");
		}
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}

		closed = true;
		orderedEntries = List.of();
		preview = null;
		mixer.clear();
		gates.clear();
		speeds.clear();
		List.copyOf(playing.values()).forEach(this::release);
		playing.clear();
		events.clear();
		retention.forEach(FXObject::setKeepAlive);
		retention.clear();
		controller.attach(null);

		if (root.getOnUpdateTick() == tickCallback) {
			root.setOnUpdateTick(previousTick);
		}

		if (root.getOnUpdateFrame() == frameCallback) {
			root.setOnUpdateFrame(previousFrame);
		}
	}
}
