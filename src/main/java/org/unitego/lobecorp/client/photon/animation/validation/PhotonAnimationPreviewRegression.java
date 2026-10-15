package org.unitego.lobecorp.client.photon.animation.validation;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.photon.PhotonRegistries;
import com.lowdragmc.photon.client.fx.FXData;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Marker;
import com.lowdragmc.photon.client.fx.timeline.Signal;
import com.lowdragmc.photon.client.fx.timeline.SignalTrack;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import com.lowdragmc.photon.client.fx.timeline.property.PositionPropertyType;
import com.lowdragmc.photon.client.gameobject.EmptyFXObject;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.joml.Vector3f;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationProjectAccess;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationEditSnapshot;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationTimelineModel;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationWorkspace;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationClock.State;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationPlayback;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationBinding.Blend;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationParameter;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationRule;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationDataUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationProjectPreviewUtil;

import java.util.IdentityHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// 工程专用测试时间轴、导出边界及并行播放检查；只用独立内存数据，不写资源或借用当前工作区。
public class PhotonAnimationPreviewRegression {
	private int assertions;

	/// 需要 NeoForge 完成加载，以真实工程 ValueIO、Mixin 桥和原生轨道注册表执行。
	public static int runLoaded() {
		var checks = new PhotonAnimationPreviewRegression();
		checks.projectBoundary();
		checks.modelIsolation();
		checks.editSnapshots();
		checks.parallelPlayback();
		checks.playbackOwnership();
		checks.queuedPreviewCommands();
		checks.previewTimelineEndOwnership();
		checks.previewPlaybackSnapshots();
		checks.previewSnapshotEvents();
		checks.previewSnapshotSignals();
		checks.previewSnapshotValidation();
		return checks.assertions;
	}

	@SuppressWarnings("DataFlowIssue") // 已加载环境通过 Mixin 为真实工程提供桥接口。
	private void projectBoundary() {
		var project = new FXProject();
		var access = (PhotonAnimationProjectAccess) project;
		check(!serialize(project).getCompoundOrEmpty("data").contains(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD),
				"untouched project does not initialize a test timeline");
		var preview = access.lobecorp$previewDefinition();
		check(preview == access.lobecorp$previewDefinition(), "project test definition is stable");
		check(preview.autoDuration && !preview.autoEnableModules, "test defaults preserve native module enable");
		var other = (PhotonAnimationProjectAccess) new FXProject();
		check(!preview.id.equals(other.lobecorp$previewDefinition().id), "different projects own distinct test identities");
		var group = (TrackGroup) Objects.requireNonNull(PhotonRegistries.TIMELINE_TRACKS.get("group")).create();

		for (var type : List.of("activator", "control", "animation", "signal", "speed", "audio", "post_process")) {
			var track = Objects.requireNonNull(PhotonRegistries.TIMELINE_TRACKS.get(type)).create();
			track.displayName(type);
			group.children().add(track);
		}

		preview.tracks.add(group);
		var payload = new CompoundTag();
		payload.putString("payload", "retained");
		preview.markers.add(new PhotonAnimationDefinition.Marker(12.5, "test_marker", payload));
		var named = new PhotonAnimationDefinition();
		named.name = "exported";
		var library = new PhotonAnimationLibrary();
		library.animations.add(named);
		var controller = new PhotonAnimationController();
		controller.library(library);
		project.getFx().getFxData().objects().add(controller);

		var saved = serialize(project);
		var data = saved.getCompoundOrEmpty("data");
		check(data.contains(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD), "test timeline is saved beside project FX");
		check(!saved.getCompoundOrEmpty("meta").contains(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD),
				"test timeline is project content rather than metadata");
		var previewTag = data.getCompoundOrEmpty(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD);
		check(previewTag.getIntOr("version", -1) == PhotonAnimationDataUtil.VERSION, "project test format records current version");
		var reporter = new ProblemReporter.Collector();
		var loaded = new FXProject();
		loaded.deserialize(TagValueInput.create(reporter, Platform.getFrozenRegistry(), saved));
		check(reporter.isEmpty(), "project test round trip has no ValueIO problems");
		var restored = ((PhotonAnimationProjectAccess) loaded).lobecorp$previewDefinition();
		check(PhotonAnimationProjectPreviewUtil.write(Platform.getFrozenRegistry(), restored).equals(previewTag),
				"actual project round trip preserves tracks, markers and payload");
		check(restored.id.equals(preview.id) && restored != preview, "project test identity survives independent load");
		check(restored.tracks.size() == 1 && ((TrackGroup) restored.tracks.getFirst()).children().size() == 7,
				"project test persists all eight native track types");

		exportBoundary(project, preview, named, controller);
		var withoutPreview = saved.copy();
		withoutPreview.getCompoundOrEmpty("data").remove(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD);
		loaded.deserialize(TagValueInput.create(reporter, Platform.getFrozenRegistry(), withoutPreview));
		check(!serialize(loaded).getCompoundOrEmpty("data").contains(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD),
				"loading an old project clears a previous test definition");
		check(!((PhotonAnimationProjectAccess) loaded).lobecorp$previewDefinition().id.equals(preview.id),
				"old project creates its own test definition only when requested");
	}

	private void exportBoundary(FXProject project, PhotonAnimationDefinition preview, PhotonAnimationDefinition named,
	                            PhotonAnimationController controller) {
		var provider = Platform.getFrozenRegistry();
		var exported = project.getFx().serializeNBT(provider);
		check(!exported.contains(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD), "FX export excludes project test field");
		check(!exported.getCompoundOrEmpty("fxData").contains(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD),
				"FX data excludes project test field");
		check(controller.library().find(preview.id.toString()) == null, "test definition never enters exported animation library");
		check(controller.library().find(named.id.toString()) != null, "normal named definition remains exportable");

		for (var deep : List.of(false, true)) {
			var copied = project.getFx().getFxData().copy(deep);
			check(!copied.serializeNBT(provider).contains(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD),
					"runtime FX data copy excludes test field, deep=" + deep);
			var copiedController = copied.objects().stream().filter(PhotonAnimationController.class::isInstance)
					.map(PhotonAnimationController.class::cast).findFirst().orElseThrow();
			check(copiedController.library().find(preview.id.toString()) == null,
					"runtime FX data copy excludes test definition, deep=" + deep);
		}
	}

	private CompoundTag serialize(FXProject project) {
		var reporter = new ProblemReporter.Collector();
		var output = TagValueOutput.createWithContext(reporter, Platform.getFrozenRegistry());
		project.serialize(output);
		check(reporter.isEmpty(), "project serialization has no ValueIO problems");
		return output.buildResult();
	}

	private void modelIsolation() {
		var project = new FXProject();
		@SuppressWarnings("DataFlowIssue") // 测试对象来自已加载的工程 Mixin。
		var preview = ((PhotonAnimationProjectAccess) project).lobecorp$previewDefinition();
		var named = new PhotonAnimationDefinition();
		var model = new PhotonAnimationTimelineModel();
		var track = Objects.requireNonNull(PhotonRegistries.TIMELINE_TRACKS.get("signal")).create();
		model.load(preview);
		model.timeline().tracks().add(track);
		var marker = new Marker().tick(3).name("draft");
		model.timeline().markers().add(marker);
		model.store(preview);
		check(preview.tracks.equals(List.of(track)), "unselected test timeline edits are stored independently");
		check(named.tracks.isEmpty() && project.getFx().getFxData().timeline().tracks().isEmpty(),
				"test edits do not enter named or scene timeline");
		check(model.timeline() != preview.timeline() && model.timeline() != project.getFx().getFxData().timeline(),
				"test editing uses an independent TL model");
		model.load(named);
		check(model.timeline().tracks().isEmpty() && model.timeline().markers().isEmpty(), "selected animation has no test content");
		model.load(preview);
		check(model.timeline().tracks().getFirst() == track, "returning to test restores valid track identity");
		check(model.timeline().markers().getFirst().tick() == 3, "returning to test restores its marker time");
		var restoredMarker = model.timeline().markers().getFirst();
		model.load(preview);
		check(model.timeline().markers().getFirst() == restoredMarker, "reloading test keeps current marker selection identity");
	}

	private void editSnapshots() {
		var target = new EmptyFXObject();
		var preview = position(target, "test_snapshot", 2);
		var named = position(target, "named_snapshot", 3);
		var library = new PhotonAnimationLibrary();
		library.animations.add(named);
		var track = (AnimationTrack) preview.tracks.getFirst();
		var property = track.properties().getFirst();
		var namedTrack = (AnimationTrack) named.tracks.getFirst();
		var namedProperty = namedTrack.properties().getFirst();
		var marker = new PhotonAnimationDefinition.Marker(4, "before", new CompoundTag());
		preview.markers.add(marker);
		var parameter = new PhotonAnimationParameter();
		parameter.name = "gain";
		parameter.defaults = new float[]{2};
		preview.parameters.add(parameter);
		var binding = preview.bindings.getFirst();
		var workspace = snapshotWorkspace(preview, library);
		check(!workspace.hasSelection() && workspace.editingDefinition() == preview,
				"unselected workspace edits its own project test definition");
		var identities = workspace.captureFormIdentities(List.of(parameter, marker, binding, property));
		var before = new PhotonAnimationEditSnapshot(workspace, library.write(Platform.getFrozenRegistry()), library);
		check(before.belongsTo(workspace), "test snapshot retains its workspace owner");
		property.putKey(0, 0, 8);
		preview.duration = 45;
		preview.autoDuration = false;
		preview.loop = true;
		preview.markers.set(0, new PhotonAnimationDefinition.Marker(9, "after", new CompoundTag()));
		parameter.defaults[0] = 5;
		binding.blend = Blend.MULTIPLY;
		check(before.data().equals(library.write(Platform.getFrozenRegistry())), "test-only edit leaves named library snapshot unchanged");
		check(!before.previewData().equals(preview.write(Platform.getFrozenRegistry())), "test-only edit is detected by independent snapshot data");
		var after = new PhotonAnimationEditSnapshot(workspace, before.data(), library);
		namedProperty.putKey(0, 0, 99);
		var restored = before.restorePreview();
		check(restored.tracks.getFirst() == track && track.properties().getFirst() == property,
				"test undo retains original track and property selection references");
		equal(2, property.sample(0)[0], "test undo restores native keyframe content");
		check(restored.markers.getFirst() == marker && !restored.loop && restored.autoDuration,
				"test undo restores marker identity and playback settings");
		equal(2, restored.parameters.getFirst().defaults[0], "test undo restores formal defaults");
		check(restored.bindings.getFirst().blend == Blend.ADD, "test undo restores its binding independently");
		setSnapshotField(workspace, "testDefinition", restored);
		var restoredLibrary = before.restore();
		check(restoredLibrary.animations.size() == 1 && restoredLibrary.find(preview.id.toString()) == null,
				"test snapshot restoration does not insert test definition into named library");
		check(restoredLibrary.animations.getFirst().tracks.getFirst() == namedTrack
				&& namedTrack.properties().getFirst() == namedProperty, "named undo retains its own original track references");
		equal(3, namedProperty.sample(0)[0], "named snapshot content remains separate from test content");
		check(workspace.captureFormIdentities(List.of(restored.parameters.getFirst(), restored.markers.getFirst(),
				restored.bindings.getFirst(), property)).equals(identities), "test undo restores existing form identities");
		var redone = after.restorePreview();
		check(redone.tracks.getFirst() == track && track.properties().getFirst() == property,
				"test redo keeps original track references");
		equal(8, property.sample(0)[0], "test redo restores changed native curve");
		equal(45, redone.playbackDuration(), "test redo restores fixed duration");
		check(redone.loop && redone.markers.getFirst().time() == 9, "test redo restores loop and marker content");
		equal(5, redone.parameters.getFirst().defaults[0], "test redo restores changed formal");
		check(redone.bindings.getFirst().blend == Blend.MULTIPLY, "test redo restores changed binding");
		var detached = before.previewData();
		detached.putString("name", "detached");
		check(!before.previewData().equals(detached), "test snapshot data is returned as a defensive copy");
	}

	/// 仅分配快照所需的三个字段；禁止调用 UI、渲染或生命周期方法，不创建真实编辑器。
	private static PhotonAnimationWorkspace snapshotWorkspace(PhotonAnimationDefinition preview, PhotonAnimationLibrary library) {
		try {
			var type = Class.forName("sun.misc.Unsafe");
			var field = type.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			var workspace = (PhotonAnimationWorkspace) type.getMethod("allocateInstance", Class.class)
					.invoke(field.get(null), PhotonAnimationWorkspace.class);
			setSnapshotField(workspace, "testDefinition", preview);
			setSnapshotField(workspace, "library", library);
			Map<Object, UUID> identities = new IdentityHashMap<>();
			setSnapshotField(workspace, "formIdentities", identities);
			return workspace;
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError("snapshot fixture cannot allocate an isolated workspace", failure);
		}
	}

	private static void setSnapshotField(PhotonAnimationWorkspace workspace, String name, Object value) {
		try {
			var field = PhotonAnimationWorkspace.class.getDeclaredField(name);
			field.setAccessible(true);
			field.set(workspace, value);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError("snapshot fixture field " + name, failure);
		}
	}

	private void parallelPlayback() {
		var target = new EmptyFXObject();
		target.transform().localPosition(new Vector3f(1, 0, 0));
		var named = position(target, "named", 3);
		var preview = position(target, "independent_test", 2);
		var library = new PhotonAnimationLibrary();
		library.animations.add(named);
		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(target, controller)));
		fx.timelinePlayer.setAudioDispatch(false);

		try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
			runtime.previewOnly();
			runtime.playPreview(preview, 10);
			var test = Objects.requireNonNull(runtime.playback(preview.id));
			var running = runtime.play(named.name);
			check(test.definition == preview && runtime.playback(preview.name) == null, "external test is addressed by UUID without library insertion");
			check(controller.library().find(preview.id.toString()) == null, "test playback does not mutate saved library");
			Objects.requireNonNull(((FXObject) fx.getRoot()).getOnUpdateTick()).run();
			equal(6, target.transform().localPosition().x, "test and named animations compose with original scene base");
			runtime.pause(test);
			check(test.clock.state() == State.PAUSED && running.clock.state() == State.PLAYING, "pausing test leaves named animation running");
			runtime.resume(test);
			check(test.clock.state() == State.PLAYING, "external test resumes through owned handle");
			runtime.stop(test);
			check(runtime.playback(preview.id) == null && runtime.isCurrent(running), "stopping external test retains named handle");
			equal(4, target.transform().localPosition().x, "stopping external test releases only its contribution");
			runtime.stop(running);
			equal(1, target.transform().localPosition().x, "stopping remaining named contribution restores original base");
		} finally {
			fx.destroy(true);
		}
	}

	private void playbackOwnership() {
		var controller = new PhotonAnimationController();
		controller.library(new PhotonAnimationLibrary());
		var fx = new FXRuntime(new FXData(List.of(controller)));
		fx.timelinePlayer.setAudioDispatch(false);

		try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
			runtime.previewOnly();
			var preview = PhotonAnimationProjectPreviewUtil.create();
			runtime.playPreview(preview, 5);
			var old = Objects.requireNonNull(runtime.playback(preview.id));
			runtime.playPreview(preview, 10);
			var current = Objects.requireNonNull(runtime.playback(preview.id));
			check(current != old && !runtime.isCurrent(old), "restarting test invalidates its old handle");
			runtime.stop(old);
			runtime.pause(old);
			check(runtime.isCurrent(current) && current.clock.state() == State.PLAYING, "old test handle cannot stop or pause replacement");
			runtime.pause(current);
			runtime.resume(old);
			check(current.clock.state() == State.PAUSED, "old test handle cannot resume replacement");
			runtime.resume(current);
			runtime.pause((PhotonAnimationPlayback) null);
			runtime.resume((PhotonAnimationPlayback) null);
			check(current.clock.state() == State.PLAYING, "missing test handle is safe for passive controls");
			foreignPlaybackOwnership(runtime, current);
			runtime.stop(current);
			check(runtime.playback(preview.id) == null, "test on an empty named library stops by handle");
		} finally {
			fx.destroy(true);
		}
	}

	private void foreignPlaybackOwnership(PhotonAnimationRuntime owner, PhotonAnimationPlayback current) {
		var controller = new PhotonAnimationController();
		controller.library(new PhotonAnimationLibrary());
		var fx = new FXRuntime(new FXData(List.of(controller)));
		fx.timelinePlayer.setAudioDispatch(false);

		try (var foreign = new PhotonAnimationRuntime(fx, controller)) {
			foreign.previewOnly();
			foreign.playPreview(current.definition, 1);
			var other = Objects.requireNonNull(foreign.playback(current.definition.id));
			foreign.stop(current);
			foreign.pause(current);
			check(foreign.isCurrent(other) && other.clock.state() == State.PLAYING,
					"same test UUID in another runtime cannot stop or pause its own handle");
			owner.pause(current);
			foreign.resume(current);
			check(current.clock.state() == State.PAUSED, "foreign runtime cannot resume owner handle");
			owner.resume(current);
			owner.stop(other);
			owner.pause(other);
			check(owner.isCurrent(current) && foreign.isCurrent(other), "foreign test handle cannot affect either ownership map");
			check(other.clock.state() == State.PLAYING, "owner runtime cannot pause foreign test handle");
		} finally {
			fx.destroy(true);
		}
	}

	/// 仅执行真实工作区的命令排队入口；夹具不具备编辑器、预览或渲染对象。
	private void queuedPreviewCommands() {
		var workspace = snapshotWorkspace(new PhotonAnimationDefinition(), new PhotonAnimationLibrary());
		Deque<Runnable> pending = new ArrayDeque<>();
		setSnapshotField(workspace, "afterSimulation", pending);
		setSnapshotField(workspace, "simulationRequested", true);
		var order = new ArrayList<String>();
		setSnapshotField(workspace, "resumeAfterSimulation", true);
		setSnapshotField(workspace, "resumeTestPlaybackAfterSimulation", true);
		check(deferPreviewCommand(workspace, () -> order.add("A")), "first preview command waits for simulation");
		check(deferPreviewCommand(workspace, () -> order.add("B")), "second target also waits without replacing first");
		check(previewCommandFlag(workspace, "resumeAfterSimulation")
						&& previewCommandFlag(workspace, "resumeTestPlaybackAfterSimulation"),
				"queued per-target commands preserve TL and host resume intentions");
		check(!flushPreviewCommands(workspace) && pending.size() == 2 && order.isEmpty(),
				"pending simulation cannot consume or execute queued targets");
		setSnapshotField(workspace, "simulationRequested", false);
		check(flushPreviewCommands(workspace), "completed simulation flushes queued commands");
		check(order.equals(List.of("A", "B")) && pending.isEmpty(), "multiple targets execute once in FIFO order");
		check(previewCommandFlag(workspace, "resumeAfterSimulation")
						&& previewCommandFlag(workspace, "resumeTestPlaybackAfterSimulation"),
				"flushing per-target commands preserves both resume intentions for final handling");
		check(!flushPreviewCommands(workspace) && order.size() == 2, "empty queue cannot execute commands twice");

		setSnapshotField(workspace, "simulationRequested", true);
		deferPreviewCommand(workspace, () -> {
			order.add("C");
			setSnapshotField(workspace, "simulationRequested", true);
		});
		deferPreviewCommand(workspace, () -> order.add("D"));
		setSnapshotField(workspace, "simulationRequested", false);
		flushPreviewCommands(workspace);
		check(order.equals(List.of("A", "B", "C")) && pending.size() == 1,
				"command starting another simulation retains later targets");
		check(!flushPreviewCommands(workspace) && pending.size() == 1, "new simulation delays remaining command");
		setSnapshotField(workspace, "simulationRequested", false);
		flushPreviewCommands(workspace);
		check(order.equals(List.of("A", "B", "C", "D")) && pending.isEmpty(),
				"remaining command resumes after next simulation without replaying earlier commands");

		setSnapshotField(workspace, "simulationRequested", true);
		deferPreviewCommand(workspace, () -> order.add("discarded"));
		setSnapshotField(workspace, "resumeAfterSimulation", true);
		setSnapshotField(workspace, "resumeTestPlaybackAfterSimulation", true);
		workspace.clearQueuedPlaybackCommand();
		check(!previewCommandFlag(workspace, "resumeAfterSimulation")
						&& !previewCommandFlag(workspace, "resumeTestPlaybackAfterSimulation"),
				"explicit command clear cancels both TL and host resume intentions");
		setSnapshotField(workspace, "simulationRequested", false);
		check(!flushPreviewCommands(workspace) && pending.isEmpty() && order.size() == 4,
				"explicit reset discards queued commands without executing them");
		pending.addLast(() -> order.add("closed"));
		setSnapshotField(workspace, "closed", true);
		flushPreviewCommands(workspace);
		check(order.size() == 4 && pending.size() == 1, "closed workspace cannot execute retained commands");
		workspace.clearQueuedPlaybackCommand();
	}

	private static boolean previewCommandFlag(PhotonAnimationWorkspace workspace, String name) {
		try {
			var field = PhotonAnimationWorkspace.class.getDeclaredField(name);
			field.setAccessible(true);
			return field.getBoolean(workspace);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError("isolated preview command flag " + name, failure);
		}
	}

	private void previewTimelineEndOwnership() {
		var workspace = snapshotWorkspace(new PhotonAnimationDefinition(), new PhotonAnimationLibrary());
		check(!workspace.timelinePlaybackActive(), "manual animation tests do not activate native TL end handling");
		setSnapshotField(workspace, "timelinePlayback", true);
		check(workspace.timelinePlaybackActive(), "live TL authoring playback enables native end handling");

		for (var flag : List.of("timelinePaused", "simulationRequested", "effectFinished", "closed")) {
			setSnapshotField(workspace, flag, true);
			check(!workspace.timelinePlaybackActive(), "native end handling is disabled while " + flag);
			setSnapshotField(workspace, flag, false);
			check(workspace.timelinePlaybackActive(), "native end handling resumes after clearing " + flag);
		}

		setSnapshotField(workspace, "timelinePlayback", false);
		check(!workspace.timelinePlaybackActive(), "finished authoring playback cannot restart manual held animations");
	}

	private static boolean deferPreviewCommand(PhotonAnimationWorkspace workspace, Runnable command) {
		return (boolean) invokePreviewCommand(workspace, "deferPlaybackCommand", new Class<?>[]{Runnable.class}, command);
	}

	private static boolean flushPreviewCommands(PhotonAnimationWorkspace workspace) {
		return (boolean) invokePreviewCommand(workspace, "flushQueuedPlaybackCommands", new Class<?>[]{});
	}

	private static Object invokePreviewCommand(PhotonAnimationWorkspace workspace, String name,
	                                           Class<?>[] signature, Object... arguments) {
		try {
			var method = PhotonAnimationWorkspace.class.getDeclaredMethod(name, signature);
			method.setAccessible(true);
			return method.invoke(workspace, arguments);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError("isolated preview command fixture " + name, failure);
		}
	}

	private void previewPlaybackSnapshots() {
		var target = new EmptyFXObject();
		target.transform().localPosition(new Vector3f(1, 0, 0));
		var first = position(target, "snapshot_first", 2);
		first.bindings.getFirst().blend = Blend.OVERRIDE;
		first.autoDuration = false;
		first.duration = 10;
		first.loop = true;
		first.parameters.add(previewParameter());
		var second = position(target, "snapshot_second", 3);
		second.bindings.getFirst().blend = Blend.OVERRIDE;
		second.parameters.add(previewParameter());
		var library = new PhotonAnimationLibrary();
		library.animations.addAll(List.of(first, second));
		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(target, controller)));
		fx.timelinePlayer.setAudioDispatch(false);

		try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
			runtime.previewOnly();
			var oldFirst = runtime.play(first.name, Map.of("gain", new float[]{4}));
			oldFirst.clock.advance(25);
			oldFirst.setDuration(30);
			oldFirst.completion(PhotonAnimationDefinition.Completion.RELEASE);
			runtime.pause(oldFirst);
			var oldSecond = runtime.play(second.name, Map.of("gain", new float[]{3}));
			oldSecond.clock.advance(20);
			check(oldSecond.clock.takeCompletion(), "held preview has already consumed its completion event");
			var firstSnapshot = Objects.requireNonNull(runtime.capturePreview(first.id));
			var secondSnapshot = Objects.requireNonNull(runtime.capturePreview(second.id));
			oldFirst.setParameter("gain", 9);
			firstSnapshot.inputs().get(first.parameters.getFirst().id.toString())[0] = 12;
			equal(4, firstSnapshot.inputs().get(first.parameters.getFirst().id.toString())[0],
					"captured input arrays are isolated from live inputs and caller mutation");
			Objects.requireNonNull(secondSnapshot.endedInputs()).get(second.parameters.getFirst().id.toString())[0] = 12;
			equal(3, Objects.requireNonNull(secondSnapshot.endedInputs()).get(second.parameters.getFirst().id.toString())[0],
					"captured completed inputs are independently copied on access");
			equal(2.5, oldFirst.clock.position(), "capture does not advance the source clock");
			runtime.previewOnly();
			var restoredSecond = Objects.requireNonNull(runtime.restorePreview(secondSnapshot));
			var restoredFirst = Objects.requireNonNull(runtime.restorePreview(firstSnapshot));
			check(!runtime.isCurrent(oldFirst) && !runtime.isCurrent(oldSecond), "replay restoration invalidates old handles");
			check(restoredFirst.order < restoredSecond.order && restoredFirst.order == firstSnapshot.order(),
					"reverse snapshot restoration preserves original startup ordering");
			equal(3, target.transform().localPosition().x, "same-priority override keeps original last contributor after replay");
			equal(30, restoredFirst.clock.duration(), "temporary duration survives replay");
			equal(2.5, restoredFirst.clock.position(), "loop count and fractional position survive replay");
			check(restoredFirst.clock.loops() == 2 && restoredFirst.clock.state() == State.PAUSED,
					"looping preview retains cycles and paused state");
			equal(4, restoredFirst.parameter("gain")[0], "live input snapshot is restored rather than changed source input");
			check(restoredFirst.completion() == PhotonAnimationDefinition.Completion.RELEASE,
					"per-play completion override survives replay");
			restoredFirst.setParameter("gain", 6);
			equal(30, restoredFirst.clock.duration(), "input updates preserve captured explicit duration override");
			check(restoredSecond.clock.state() == State.HOLDING && !restoredSecond.clock.takeCompletion(),
					"held last frame cannot emit completion a second time after replay");
			runtime.resume(oldFirst);
			runtime.stop(oldSecond);
			check(restoredFirst.clock.state() == State.PAUSED && runtime.isCurrent(restoredSecond),
					"old handles cannot resume or stop restored plays");
			runtime.resume(restoredFirst);
			check(restoredFirst.clock.state() == State.PLAYING, "restored paused loop resumes through its new handle");
			runtime.stop(restoredSecond);
			equal(2, target.transform().localPosition().x, "stopping restored second play retains restored first contribution");
			runtime.stop(restoredFirst);
			equal(1, target.transform().localPosition().x, "stopping restored combination returns to original base");
			var controlled = runtime.playControlled(first.name, 20, Map.of(), new Object());
			check(runtime.capturePreview(first.id) == null, "parent-controlled playback is not a manual preview snapshot");
			runtime.stop(controlled);
			runtime.playPreview(PhotonAnimationProjectPreviewUtil.create(), 0);
			check(runtime.capturePreview(UUID.randomUUID()) == null, "missing playback has no preview snapshot");
			runtime.playPreview(first, 0);
			check(runtime.capturePreview(first.id) == null, "TL preview is rebuilt from authoring data rather than manual snapshot");
		} finally {
			fx.destroy(true);
		}
	}

	private void previewSnapshotEvents() {
		var source = new PhotonAnimationDefinition();
		source.name = "snapshot_events";
		source.autoDuration = false;
		source.duration = 20;

		for (var time : List.of(0, 5, 10, 20)) {
			source.markers.add(new PhotonAnimationDefinition.Marker(time, "marker", new CompoundTag()));
		}

		var probe = new PhotonAnimationDefinition();
		probe.name = "snapshot_event_probe";
		var library = new PhotonAnimationLibrary();
		library.animations.addAll(List.of(source, probe));

		for (var event : List.of(PhotonAnimationRule.Event.MARKER, PhotonAnimationRule.Event.COMPLETE)) {
			var rule = new PhotonAnimationRule();
			rule.event = event;
			rule.animation = source.id.toString();
			rule.target = probe.id.toString();
			library.rules.add(rule);
		}

		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(controller)));
		fx.timelinePlayer.setAudioDispatch(false);

		try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
			runtime.previewOnly();
			runtime.setPreviewDispatch(true);
			fx.timelinePlayer.setAudioDispatch(false);
			runtime.play(source.name);
			tickPreview(fx, 8);
			check(runtime.playback(probe.name) != null, "original play dispatches initial and passed marker events");
			runtime.stop(probe.name);
			var playing = Objects.requireNonNull(runtime.capturePreview(source.id));
			runtime.previewOnly();
			runtime.restorePreview(playing);
			runtime.setPreviewDispatch(true);
			fx.timelinePlayer.setAudioDispatch(false);
			tickPreview(fx, 1);
			check(runtime.playback(probe.name) == null, "restoring running play does not repeat marker zero or passed markers");
			tickPreview(fx, 1);
			check(runtime.playback(probe.name) != null, "restored play still dispatches future passed markers");
			runtime.stop(probe.name);
			tickPreview(fx, 10);
			check(runtime.playback(probe.name) != null, "restored play still dispatches its natural completion");
			runtime.stop(probe.name);
			var held = Objects.requireNonNull(runtime.capturePreview(source.id));
			check(held.clock().completed(), "held snapshot remembers already-dispatched completion");
			runtime.previewOnly();
			runtime.restorePreview(held);
			runtime.setPreviewDispatch(true);
			fx.timelinePlayer.setAudioDispatch(false);
			tickPreview(fx, 1);
			check(runtime.playback(probe.name) == null, "held restoration repeats neither markers nor completion");
		} finally {
			fx.destroy(true);
		}
	}

	private void previewSnapshotSignals() {
		var source = new PhotonAnimationDefinition();
		source.name = "snapshot_native_signals";
		var track = (SignalTrack) Objects.requireNonNull(PhotonRegistries.TIMELINE_TRACKS.get("signal")).create();
		track.displayName("snapshot_signal_channel");

		for (var time : List.of(0, 5, 10, 20)) {
			track.signals().add(new Signal(time, "snapshot_signal"));
		}

		source.tracks.add(track);
		var library = new PhotonAnimationLibrary();
		library.animations.add(source);
		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(controller)));
		var effect = new PreviewSignalEffect();
		fx.getRoot().setEffect(effect);
		fx.timelinePlayer.setAudioDispatch(false);

		try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
			runtime.previewOnly();
			runtime.setPreviewDispatch(true);
			fx.timelinePlayer.setAudioDispatch(false);
			var playing = runtime.play(source.name);
			runtime.resume(playing);
			tickPreview(fx, 8);
			check(effect.times.equals(List.of(0D, 5D)), "native signal fixture dispatches initial and passed signals once");
			var snapshot = Objects.requireNonNull(runtime.capturePreview(source.id));
			runtime.previewOnly();
			runtime.restorePreview(snapshot);
			check(effect.times.equals(List.of(0D, 5D)), "native snapshot initialization does not replay passed signals");
			runtime.setPreviewDispatch(true);
			fx.timelinePlayer.setAudioDispatch(false);
			tickPreview(fx, 1);
			check(effect.times.equals(List.of(0D, 5D)), "native signal cursor remains at captured authoring time");
			tickPreview(fx, 1);
			check(effect.times.equals(List.of(0D, 5D, 10D)), "future native signal dispatch survives snapshot restoration");
		} finally {
			fx.destroy(true);
		}
	}

	private void previewSnapshotValidation() {
		var animation = new PhotonAnimationDefinition();
		animation.name = "snapshot_validation";
		animation.parameters.add(previewParameter());
		var library = new PhotonAnimationLibrary();
		library.animations.add(animation);
		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(controller)));
		fx.timelinePlayer.setAudioDispatch(false);

		try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
			var current = runtime.play(animation.name, Map.of("gain", new float[]{4}));
			check(runtime.capturePreview(animation.id) == null, "ordinary runtime cannot capture editor preview state");
			runtime.previewOnly();
			current = runtime.play(animation.name, Map.of("gain", new float[]{4}));
			current.clock.advance(7);
			runtime.pause(current);
			var snapshot = Objects.requireNonNull(runtime.capturePreview(animation.id));
			crossRuntimePreviewSnapshot(snapshot, animation);
			var parameter = current.definition.parameters.getFirst();
			var type = parameter.type;
			var defaults = parameter.defaults;
			parameter.type = PhotonAnimationParameter.Type.VECTOR;
			parameter.defaults = new float[]{1, 2, 3};

			try {
				runtime.restorePreview(snapshot);
				throw new AssertionError("incompatible preview signature was accepted");
			} catch (IllegalArgumentException expected) {
				check(runtime.isCurrent(current) && current.clock.state() == State.PAUSED,
						"failed signature restoration preserves current handle and paused state");
			} finally {
				parameter.type = type;
				parameter.defaults = defaults;
			}

			equal(4, current.parameter("gain")[0], "failed restoration preserves original inputs");
			previewRuntimeLibrary(runtime).animations.clear();
			check(runtime.restorePreview(snapshot) == null && runtime.isCurrent(current),
					"deleted definition cannot be restored or overwrite existing state");
		} finally {
			fx.destroy(true);
		}
	}

	private void crossRuntimePreviewSnapshot(PhotonAnimationRuntime.PreviewPlayback snapshot,
	                                         PhotonAnimationDefinition source) {
		var restoredDefinition = PhotonAnimationDefinition.read(Platform.getFrozenRegistry(), source.write(Platform.getFrozenRegistry()));
		var library = new PhotonAnimationLibrary();
		library.animations.add(restoredDefinition);
		var controller = new PhotonAnimationController();
		controller.library(library);
		var fx = new FXRuntime(new FXData(List.of(controller)));
		fx.timelinePlayer.setAudioDispatch(false);

		try (var runtime = new PhotonAnimationRuntime(fx, controller)) {
			runtime.previewOnly();
			var restored = Objects.requireNonNull(runtime.restorePreview(snapshot));
			check(restored.definition == previewRuntimeLibrary(runtime).find(snapshot.animation().toString())
							&& restored.definition != source && restored.definition != restoredDefinition,
					"recreated runtime resolves its own definition by stable snapshot identity");
			check(restored.definition.id.equals(snapshot.animation()) && restored.definition.write(Platform.getFrozenRegistry())
							.equals(restoredDefinition.write(Platform.getFrozenRegistry())),
					"recreated runtime preserves stable identity and definition content across deserialization");
			equal(7.0 / 20, restored.clock.position(), "new runtime retains source preview progress");
			equal(4, restored.parameter("gain")[0], "new runtime receives captured inputs");
			check(restored.clock.state() == State.PAUSED, "new runtime preserves source pause state");
			runtime.resume(restored);
			check(restored.clock.state() == State.PLAYING, "new runtime independently resumes restored playback");
		} finally {
			fx.destroy(true);
		}
	}

	private static PhotonAnimationLibrary previewRuntimeLibrary(PhotonAnimationRuntime runtime) {
		try {
			var field = PhotonAnimationRuntime.class.getDeclaredField("library");
			field.setAccessible(true);
			return (PhotonAnimationLibrary) field.get(runtime);
		} catch (ReflectiveOperationException failure) {
			throw new AssertionError("preview fixture cannot inspect its actual runtime library", failure);
		}
	}

	/// 原生信号使用隔离的无世界执行器；不发射粒子、不访问客户端音频或渲染。
	private static class PreviewSignalEffect implements IEffectExecutor {
		private final RandomSource random = RandomSource.create(1);
		private final List<Double> times = new ArrayList<>();

		@Override
		public Level getLevel() {
			return null;
		}

		@Override
		public RandomSource getRandomSource() {
			return random;
		}

		@Override
		public void onTimelineSignal(String channel, String name, CompoundTag data, double time) {
			times.add(time);
		}
	}

	private static PhotonAnimationParameter previewParameter() {
		var parameter = new PhotonAnimationParameter();
		parameter.name = "gain";
		parameter.defaults = new float[]{1};
		return parameter;
	}

	private static void tickPreview(FXRuntime fx, int ticks) {
		for (int index = 0; index < ticks; index++) {
			Objects.requireNonNull(((FXObject) fx.getRoot()).getOnUpdateTick()).run();
		}
	}

	private static PhotonAnimationDefinition position(EmptyFXObject target, String name, float value) {
		var definition = new PhotonAnimationDefinition();
		definition.name = name;
		var track = (AnimationTrack) Objects.requireNonNull(PhotonRegistries.TIMELINE_TRACKS.get("animation")).create();
		track.targetId(target.id());
		var property = PositionPropertyType.INSTANCE.create(target);
		property.putKey(0, 0, value);
		property.putKey(0, 20, value);
		track.properties().add(property);
		definition.tracks.add(track);
		definition.binding(target.id(), property.type().path()).blend = Blend.ADD;
		return definition;
	}

	private void equal(double expected, double actual, String name) {
		check(Math.abs(expected - actual) < 0.00001, name + ": expected=" + expected + ", actual=" + actual);
	}

	private void check(boolean condition, String name) {
		assertions++;

		if (!condition) {
			throw new AssertionError(name);
		}
	}
}
