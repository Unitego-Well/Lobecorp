package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.EditAction;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TabView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.CommandEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import org.unitego.lobecorp.registry.photon.client.LcPhotonAnimationTypes;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.PhotonAnimationController;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationClock.State;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPreviewScope;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationProjectAccess;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationPlayback;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime.PreviewPlayback;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationClip;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnConfig;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonResourceClipboardUtil;
import org.unitego.lobecorp.util.photon.runtime.PhotonEmissionPrimeUtil;

import java.util.Map;
import java.util.EnumMap;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

import com.lowdragmc.lowdraglib2.gui.ui.utils.IHistoryStack.HistoryItem;

import javax.annotation.Nonnull;

public class PhotonAnimationWorkspace extends View {
	private final FXEditor owner;
	private final PhotonAnimationController controller;
	private final PhotonAnimationPreview preview;
	private final PhotonAnimationTimelineContext context;
	private final PhotonAnimationProjectAccess projectPreview;
	private final PhotonAnimationTestControls testControls;
	private PhotonAnimationDefinition testDefinition;
	private final Set<Object> sceneHistorySources = Collections.newSetFromMap(new IdentityHashMap<>());
	private boolean sceneRefreshPending;
	private boolean effectFinished;
	private final PhotonAnimationPanels panels;
	private final UIElement sceneHost = PhotonAnimationUiUtil.column();
	private final UIElement recordingFields = PhotonAnimationUiUtil.column();
	private final PhotonAnimationOutline outline;
	private final @Nullable HistoryItem historyBoundary;
	private final UIElement inspector = PhotonAnimationUiUtil.column();
	private final Map<PhotonAnimationForms.Page, UIElement> pageContents = new EnumMap<>(PhotonAnimationForms.Page.class);
	private final Map<PhotonAnimationForms.Page, ScrollerView> pageScrollers = new EnumMap<>(PhotonAnimationForms.Page.class);
	private final Map<FormKey, PhotonAnimationFormState> formStates = new HashMap<>();
	private final Map<Object, UUID> formIdentities = new IdentityHashMap<>();
	private final Map<ConfiguratorGroup, PhotonAnimationFormState.GroupIdentity> formGroups = new IdentityHashMap<>();
	private @Nullable UUID formAnimation;
	private @Nullable PhotonAnimationForms.Page formPage;
	private PhotonAnimationLibrary library;
	private @Nullable UUID selectedId;

	private @Nullable UUID targetSelection;
	private CompoundTag saved;
	private PhotonAnimationEditSnapshot savedSnapshot;

	/// 子片段标签逐帧查询来源；仅定义对象替换时比较完整 NBT，避免重复遍历动画库。
	private @Nullable ProjectLibraryValidation projectLibraryValidation;
	private boolean closed;
	private boolean needsRebuild;
	private boolean timelinePlayback;
	private boolean simulationRequested;
	private boolean recordingModelPending;
	private boolean resumeAfterSimulation;
	private boolean resumeTestPlaybackAfterSimulation;
	private final Deque<Runnable> afterSimulation = new ArrayDeque<>();
	private final Set<UUID> testAnimations = new HashSet<>();
	private List<PreviewPlayback> preservedTestPlaybacks = List.of();
	private boolean testPlaybackRestorePending;
	private boolean replayFromStart = true;
	private boolean timelinePaused;
	private double pausedTimelineTime;
	private double playbackCursor;
	private double playbackEpoch;

	private record InspectorHistorySource(PhotonAnimationWorkspace workspace, Object field) {
	}

	private record ProjectLibraryValidation(CompoundTag definition, CompoundTag saved, boolean matches) {
	}

	private record FormKey(@Nullable UUID animation, PhotonAnimationForms.Page page) {
	}

	private PhotonAnimationForms.Page currentPage = PhotonAnimationForms.Page.ANIMATION;

	public static void open(FXEditor owner) {
		PhotonAnimationTab.open(owner);
	}

	public static @Nullable PhotonAnimationWorkspace create(FXEditor owner) {
		if (owner.runtime == null || !PhotonEditorSettings.of(owner).enabled(PhotonAnimationTextUtil.FEATURE)) {
			return null;
		}

		var controllers = owner.runtime.getObjects().values().stream()
				.filter(PhotonAnimationController.class::isInstance).map(PhotonAnimationController.class::cast).toList();

		if (controllers.size() > 1) {
			invalid(owner);
			return null;
		}

		var controller = controllers.isEmpty() ? new PhotonAnimationController() : controllers.getFirst();

		if (controllers.isEmpty()) {
			controller.setName(LcPhotonAnimationTypes.TYPE_NAME);
			controller.transform().parent(owner.runtime.getRoot().transform(), false);
			owner.historyView.pushHistory(Component.translatable(PhotonAnimationTextUtil.key("new")),
					EditAction.of(() -> owner.hierarchyView.addSceneObject(controller),
							() -> owner.hierarchyView.removeSceneObject(controller)));
		}

		try {
			return new PhotonAnimationWorkspace(owner, controller);
		} catch (IllegalArgumentException failure) {
			invalid(owner);
			return null;
		}
	}

	private static void invalid(FXEditor editor) {
		Dialog.showNotification(PhotonAnimationTextUtil.key("title"), PhotonAnimationTextUtil.key("invalid"), null).show(editor);
	}

	public PhotonAnimationWorkspace(FXEditor owner, PhotonAnimationController controller) {
		super(PhotonAnimationTextUtil.key("title"));
		this.owner = owner;
		this.controller = controller;

		if (!(owner.getCurrentProject() instanceof PhotonAnimationProjectAccess project)) {
			throw new IllegalArgumentException();
		}

		projectPreview = project;
		testDefinition = project.lobecorp$previewDefinition();
		library = controller.library();
		testControls = new PhotonAnimationTestControls(this);
		saved = controller.definition.copy();
		savedSnapshot = new PhotonAnimationEditSnapshot(this, saved, library);
		historyBoundary = owner.historyView.getCurrentHistory();

		if (!library.migrationBlocked() && !library.animations.isEmpty()) {
			selectedId = library.animations.getFirst().id;
		}

		preview = new PhotonAnimationPreview(owner);
		context = new PhotonAnimationTimelineContext(this);
		outline = new PhotonAnimationOutline(this, this::create, this::duplicate, this::rename, this::delete);
		setCanRemove(true);
		setFloatable(true);
		setFocusable(true);
		setOnRemove(this::cleanup);
		getLayout().widthPercent(100).heightPercent(100).flexDirection(FlexDirection.COLUMN);
		panels = buildLayout();

		if (library.migrationBlocked()) {
			PhotonAnimationForms.migrationReport(library, sceneHost);
		}

		bindCommands(this);
		addEventListener(UIEvents.TICK, _ -> update());
		rebuild();
	}

	private PhotonAnimationPanels buildLayout() {
		sceneHost.layout(layout -> layout.flex(1).minHeight(0));
		addChild(sceneHost);
		return new PhotonAnimationPanels(this, outline, inspectorTabs());
	}

	private TabView inspectorTabs() {
		var tabs = new TabView();
		tabs.layout(layout -> layout.widthPercent(100).heightPercent(100).flex(1).minHeight(0)
				.flexDirection(FlexDirection.COLUMN_REVERSE));
		tabs.tabContentContainer.layout(layout -> layout.flex(1).minHeight(0).paddingAll(0));
		tabs.tabContentContainer.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
		tabs.tabScroller.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY))
				.layout(layout -> layout.paddingAll(0));

		for (var page : PhotonAnimationForms.Page.values()) {
			var content = PhotonAnimationUiUtil.column();
			pageContents.put(page, content);
			var tab = new Tab().setText(Component.translatable(PhotonAnimationTextUtil.key(page.key())))
					.setOnTabSelected(() -> showPage(page));
			tab.getLayout().setFlexShrink(0);
			PhotonAnimationUiUtil.tooltip(tab, page.key());
			var scroller = PhotonAnimationUiUtil.scroller(content);
			pageScrollers.put(page, scroller);
			tabs.addTab(tab, scroller);
		}

		return tabs;
	}

	public void bindCommands(UIElement element) {
		element.addEventListener(UIEvents.VALIDATE_COMMAND, this::command);
		element.addEventListener(UIEvents.EXECUTE_COMMAND, this::command);
	}

	public void showPanels(boolean active) {
		if (library.migrationBlocked()) {
			panels.setActive(active);
			return;
		}

		if (active == preview.editing()) {
			return;
		}

		if (active) {
			context.captureSceneState();
			preview.show(sceneHost, true);
			context.activate();
			panels.setActive(true);
			context.refreshPreview();
			return;
		}

		context.suspend();
		clearQueuedPlaybackCommand();
		clearTestPlayback();
		simulationRequested = false;
		preview.show(sceneHost, false);
		context.restoreSceneState();
		panels.setActive(false);
	}

	public void finishPreview() {
		context.setRecordingTrack(null);

		if (deferPlaybackCommand(this::finishPreview)) {
			return;
		}

		var runtime = preview.runtimeOrNull();

		if (runtime != null) {
			runtime.finish(true);
		}

		effectFinished = true;
		timelinePlayback = false;
		timelinePaused = false;
		simulationRequested = false;
		clearQueuedPlaybackCommand();
		clearTestPlayback();
		preview.scene().particleManager.pause();
	}

	private void withPlaybackSelection(Runnable action) {
		context.setRecordingTrack(null);

		if (deferPlaybackCommand(() -> withPlaybackSelection(action))) {
			return;
		}

		if (preview.active()) {
			withSelected(action);
		}
	}

	private boolean deferPlaybackCommand(Runnable action) {
		if (!simulationRequested) {
			return false;
		}

		afterSimulation.addLast(action);
		return true;
	}

	/// 重模拟期间按点击顺序保留命令；命令再次请求模拟时暂缓剩余操作。
	private boolean flushQueuedPlaybackCommands() {
		if (simulationRequested || afterSimulation.isEmpty()) {
			return false;
		}

		while (!simulationRequested && !closed && !afterSimulation.isEmpty()) {
			afterSimulation.removeFirst().run();
		}

		return true;
	}

	/// 重建原场景前冻结手动测试播放；重模拟只推进 TL，不推进已有组合。
	public void preserveTestPlayback() {
		if (testPlaybackRestorePending || closed) {
			return;
		}

		var runtime = preview.runtimeOrNull();

		if (runtime == null) {
			return;
		}

		var snapshots = new ArrayList<PreviewPlayback>();

		for (var id : Set.copyOf(testAnimations)) {
			var snapshot = runtime.capturePreview(id);

			if (snapshot == null) {
				testAnimations.remove(id);
				continue;
			}

			snapshots.add(snapshot);
		}

		preservedTestPlaybacks = List.copyOf(snapshots);
		testPlaybackRestorePending = true;

		for (var snapshot : snapshots) {
			runtime.stop(runtime.playback(snapshot.animation()));
		}
	}

	private void restoreTestPlayback() {
		if (!testPlaybackRestorePending) {
			return;
		}

		var runtime = preview.runtimeOrNull();

		if (runtime == null) {
			return;
		}

		var snapshots = preservedTestPlaybacks;
		preservedTestPlaybacks = List.of();
		testPlaybackRestorePending = false;

		for (var snapshot : snapshots) {
			if (snapshot.animation().equals(editingDefinition().id)) {
				testAnimations.remove(snapshot.animation());
				continue;
			}

			try {
				if (runtime.restorePreview(snapshot) == null) {
					testAnimations.remove(snapshot.animation());
				}
			} catch (IllegalArgumentException | IllegalStateException failure) {
				testAnimations.remove(snapshot.animation());
				invalid(owner);
			}
		}
	}

	private void clearTestPlayback() {
		testAnimations.clear();
		preservedTestPlaybacks = List.of();
		testPlaybackRestorePending = false;
		resumeTestPlaybackAfterSimulation = false;
	}

	public boolean timelinePlaybackActive() {
		return timelinePlayback && !timelinePaused && !simulationRequested && !effectFinished && !closed;
	}

	public void continuePreviewAfterSimulation(boolean timeline) {
		if (timeline) {
			playFromCursor();
			return;
		}

		if (simulationRequested) {
			resumeTestPlaybackAfterSimulation = true;
			return;
		}

		if (preview.active() && !effectFinished) {
			preview.runtime().setPreviewDispatch(true);
			preview.scene().particleManager.play();
		}
	}

	private void pauseAnimation() {
		resumeAfterSimulation = false;

		if (timelinePlayback) {
			pausedTimelineTime = playheadTime(0);
			timelinePaused = true;
		}

		preview.runtime().pause(selected().id.toString());
	}

	private void resumeAnimation() {
		if (timelinePlayback && timelinePaused) {
			playbackCursor = pausedTimelineTime;
			playbackEpoch = preview.runtime().effectTime();
			timelinePaused = false;
		}

		preview.runtime().resume(selected().id.toString());
		preview.runtime().setPreviewDispatch(true);
		preview.scene().particleManager.play();
	}

	public void resetPreview() {
		context.setRecordingTrack(null);
		clearQueuedPlaybackCommand();
		clearTestPlayback();
		effectFinished = false;
		preview.reset();
		replayFromStart = true;
		context.refreshPreview();
	}

	private void withSelected(Runnable action) {
		if (!hasSelection()) {
			return;
		}

		withEditing(action);
	}

	private void withEditing(Runnable action) {
		if (closed || library.migrationBlocked()) {
			return;
		}

		try {
			action.run();
		} catch (IllegalArgumentException | IllegalStateException failure) {
			invalid(owner);
		}
	}

	public void pauseSelectedAnimation() {
		withPlaybackSelection(this::pauseAnimation);
	}

	public void stopSelectedAnimation() {
		withPlaybackSelection(this::stopAtCursor);
	}

	public void continueAnimation() {
		context.setRecordingTrack(null);

		if (deferPlaybackCommand(this::continueAnimation)) {
			return;
		}

		var runtime = preview.runtimeOrNull();

		if (runtime == null || !hasSelection() || runtime.playback(selected().id.toString()) == null) {
			playAnimation(false);
			return;
		}

		withSelected(this::resumeAnimation);
	}

	public State sceneState() {
		if (effectFinished || preview.runtimeOrNull() == null) {
			return State.STOPPED;
		}

		return preview.scene().particleManager.isPlaying() ? State.PLAYING : State.PAUSED;
	}

	public Component animationStatus(UUID id) {
		var runtime = preview.runtimeOrNull();
		var playback = runtime == null ? null : runtime.playback(id);
		var state = playback == null ? State.STOPPED : playback.clock.state();
		return PhotonAnimationUiUtil.animationStatus(state,
				runtime == null ? 0 : runtime.effectTime(), playback == null ? 0 : playback.clock.progress(),
				playback == null ? 0 : playback.clock.loops(), runtime == null ? List.of() : runtime.problems());
	}

	public void playAnimation(boolean restart) {
		context.setRecordingTrack(null);

		if (deferPlaybackCommand(() -> playAnimation(restart))) {
			return;
		}

		timelinePlayback = false;
		timelinePaused = false;
		resumeAfterSimulation = false;
		effectFinished = false;

		if (!preview.active()) {
			preview.reset();
		}

		if (!preview.active()) {
			return;
		}

		withSelected(() -> {
			preview.runtime().setPreviewDispatch(true);
			preview.runtime().play(selected().id.toString(), -1, restart, Map.of());
			testAnimations.add(selected().id);
			preview.scene().particleManager.play();
		});
	}

	/// 测试目标独立于制作选择；仅在预览结束后重建实例，不清除其他播放贡献。
	public void testPlay(UUID id, boolean restart, Map<String, float[]> inputs) {
		if (library.find(id.toString()) == null) {
			return;
		}

		context.setRecordingTrack(null);

		if (deferPlaybackCommand(() -> testPlay(id, restart, inputs))) {
			return;
		}

		withEditing(() -> {
			effectFinished = false;

			if (!preview.active()) {
				preview.reset();
			}

			var runtime = preview.runtimeOrNull();

			if (runtime == null) {
				return;
			}

			runtime.play(id.toString(), -1, restart, inputs);
			testAnimations.add(id);

			if (id.equals(editingDefinition().id)) {
				timelinePlayback = false;
				timelinePaused = false;
				resumeAfterSimulation = false;
			}

			runtime.setPreviewDispatch(true);
			preview.scene().particleManager.play();
		});
	}

	public @Nullable PhotonAnimationPlayback testPlayback(UUID id) {
		var runtime = preview.runtimeOrNull();
		return runtime == null ? null : runtime.playback(id);
	}

	public void testPause(UUID id) {
		if (id.equals(selectedId)) {
			pauseSelectedAnimation();
			return;
		}

		context.setRecordingTrack(null);

		if (deferPlaybackCommand(() -> testPause(id))) {
			return;
		}

		withEditing(() -> {
			var runtime = preview.runtimeOrNull();

			if (runtime != null) {
				runtime.pause(runtime.playback(id));
			}
		});
	}

	public void testContinue(UUID id, Map<String, float[]> inputs) {
		context.setRecordingTrack(null);

		if (deferPlaybackCommand(() -> testContinue(id, inputs))) {
			return;
		}

		var playback = testPlayback(id);

		if (playback == null) {
			testPlay(id, false, inputs);
			return;
		}

		withEditing(() -> {
			playback.setParameters(inputs);

			if (id.equals(selectedId)) {
				continueAnimation();
				return;
			}

			preview.runtime().resume(playback);
			preview.runtime().setPreviewDispatch(true);
			preview.scene().particleManager.play();
		});
	}

	public void testStop(UUID id) {
		if (id.equals(selectedId)) {
			stopSelectedAnimation();
			return;
		}

		context.setRecordingTrack(null);

		if (deferPlaybackCommand(() -> testStop(id))) {
			return;
		}

		withEditing(() -> {
			var runtime = preview.runtimeOrNull();
			var playback = testPlayback(id);
			testAnimations.remove(id);

			if (runtime != null && playback != null) {
				runtime.stop(playback);
			}
		});
	}

	private void create() {
		if (library.migrationBlocked()) {
			return;
		}

		Dialog.stringEditorDialog(PhotonAnimationTextUtil.key("new"), "", this::availableName, name -> editSelection(() -> {
			var animation = new PhotonAnimationDefinition();
			animation.name = name.trim();
			library.animations.add(animation);
			selectedId = animation.id;
		})).show(this);
	}

	private boolean availableName(String name) {
		return !name.isBlank() && library.find(name.trim()) == null;
	}

	private void rename() {
		withSelected(() -> Dialog.stringEditorDialog(PhotonAnimationTextUtil.key("rename"), selected().name,
				name -> !name.isBlank() && (availableName(name) || name.trim().equals(selected().name)),
				name -> edit(() -> selected().name = name.trim())).show(this));
	}

	private void duplicate() {
		withSelected(() -> Dialog.stringEditorDialog(PhotonAnimationTextUtil.key("copy"), duplicateName(selected().name), this::availableName,
				name -> editSelection(() -> {
					var copy = PhotonAnimationDefinition.read(Platform.getFrozenRegistry(), selected().write(Platform.getFrozenRegistry()));
					copy.id = UUID.randomUUID();
					remapCopiedParameters(copy);
					copy.name = name.trim();
					library.animations.add(copy);
					selectedId = copy.id;
				})).show(this));
	}

	private String duplicateName(String name) {
		var candidate = name;

		for (var index = 1; !availableName(candidate); index++) {
			candidate = Component.translatable(PhotonAnimationTextUtil.key("copy_name"), name, index).getString();
		}

		return candidate;
	}

	private void delete() {
		withSelected(() -> editSelection(() -> {
			var removed = selected().id.toString();
			library.animations.remove(selected());
			library.rules.removeIf(rule -> rule.animation.equals(removed) || rule.target.equals(removed));

			if (library.defaultAnimation.equals(removed)) {
				library.defaultAnimation = "";
			}

			selectedId = library.animations.isEmpty() ? null : library.animations.getFirst().id;
		}));
	}

	private void editSelection(Runnable action) {
		if (closed || library.migrationBlocked()) {
			return;
		}

		context.prepareSwitch();
		edit(action);
	}

	public void edit(Runnable action) {
		edit(PhotonAnimationTextUtil.key("title"), action);
	}

	private static void remapCopiedParameters(PhotonAnimationDefinition copy) {
		var mapping = new HashMap<String, String>();

		for (var parameter : copy.parameters) {
			var previous = parameter.id.toString();
			parameter.id = UUID.randomUUID();
			mapping.put(previous, parameter.id.toString());
			mapping.put(parameter.name, parameter.id.toString());
		}

		copy.durationParameter = mapping.getOrDefault(copy.durationParameter, copy.durationParameter);

		for (var binding : copy.bindings) {
			binding.parameter = mapping.getOrDefault(binding.parameter, binding.parameter);
			binding.scaleParameter = mapping.getOrDefault(binding.scaleParameter, binding.scaleParameter);
			binding.offsetParameter = mapping.getOrDefault(binding.offsetParameter, binding.offsetParameter);
		}

		for (var track : copy.leafTracks(true)) {
			for (var clip : track.clips()) {
				if (clip instanceof PhotonChildAnimationClip child) {
					child.id = UUID.randomUUID();
					child.inputs.forEach(input -> input.source = mapping.getOrDefault(input.source, input.source));
				}
			}
		}
	}

	public void edit(String name, Runnable action) {
		if (closed || library.migrationBlocked()) {
			return;
		}

		context.setRecordingTrack(null);
		var before = savedSnapshot;

		try {
			action.run();
			var after = library.write(Platform.getFrozenRegistry());
			push(before, after, Component.translatable(name), null, true);
		} catch (IllegalArgumentException | IllegalStateException failure) {
			restore(before);
			invalid(owner);
		}
	}

	public void applied(String name, Runnable redo, Runnable undo) {
		if (closed || library.migrationBlocked()) {
			return;
		}

		var snapshot = savedSnapshot;
		var after = library.write(Platform.getFrozenRegistry());
		undo.run();
		redo.run();
		push(snapshot, after, Component.translatable(name), null, true);
	}

	private void push(PhotonAnimationEditSnapshot before, CompoundTag after, Component name, @Nullable Object source, boolean refresh) {
		if (before.data().equals(after) && before.previewData().equals(testDefinition.write(Platform.getFrozenRegistry()))) {
			return;
		}

		var next = new PhotonAnimationEditSnapshot(this, after, library);
		saved = after.copy();
		savedSnapshot = next;
		controller.definition = after.copy();
		owner.historyView.pushHistory(name, EditAction.of(() -> restore(next), () -> restore(before)),
				source == null ? next : new InspectorHistorySource(this, source), false);

		if (refresh) {
			refreshDefinition(false);
		}
	}

	public void commitInspectorChange(Component name, Object source) {
		if (closed || library.migrationBlocked()) {
			return;
		}

		context.storeModel();
		push(savedSnapshot, library.write(Platform.getFrozenRegistry()), name, source, false);
		var playing = preview.scene().particleManager.isPlaying();
		var timeline = timelinePlaybackActive();
		preview.reset();
		replayFromStart = true;
		context.refreshPreview();

		if (playing) {
			continuePreviewAfterSimulation(timeline);
		}
	}

	/// 切换前保存当前测试或命名时间轴，仅实际内容变化进入历史，不重建当前编辑视图。
	public void commitTimelineChanges() {
		if (closed || library.migrationBlocked()) {
			return;
		}

		context.storeModel();
		push(savedSnapshot, library.write(Platform.getFrozenRegistry()),
				Component.translatable(PhotonAnimationTextUtil.key("title")), null, false);
	}

	private void restore(PhotonAnimationEditSnapshot snapshot) {
		controller.definition = snapshot.data();
		testDefinition = snapshot.restorePreview();
		projectPreview.lobecorp$setPreviewDefinition(testDefinition);
		saved = snapshot.data();
		savedSnapshot = snapshot;

		if (closed) {
			return;
		}

		library = snapshot.restore();
		preview.clearTimelineInspection();

		if (selectedId != null && library.find(selectedId.toString()) == null) {
			selectedId = null;
		}

		refreshDefinition(true);
	}

	private void refreshDefinition(boolean restored) {
		var playing = preview.editing() && preview.scene().particleManager.isPlaying();
		var timeline = timelinePlaybackActive();
		preview.reset();
		replayFromStart = true;
		outline.rebuild(library, selectedId);
		rebuildCurves();

		if (restored) {
			context.restoreEditorState();
		}

		context.refreshPreview();

		if (playing) {
			continuePreviewAfterSimulation(timeline);
		}

		needsRebuild = true;

		if (restored) {
			showPage(currentPage);
			needsRebuild = false;
		}
	}

	private void rebuild() {
		outline.rebuild(library, selectedId);
		rebuildCurves();
		showPage(currentPage);
	}

	public void selectAnimation(@Nullable UUID id) {
		if (library.migrationBlocked() || Objects.equals(selectedId, id)) {
			return;
		}

		context.prepareSwitch();
		var testsPlaying = preview.scene().particleManager.isPlaying() && !testAnimations.isEmpty();
		preserveTestPlayback();
		preview.scene().particleManager.pause();
		syncPreviewDispatch(false);
		timelinePlayback = false;
		timelinePaused = false;
		clearQueuedPlaybackCommand();
		resumeTestPlaybackAfterSimulation = testsPlaying;
		replayFromStart = true;
		selectedId = id;
		preview.clearTimelineInspection();
		rebuildCurves();
		showPage(currentPage);
		context.refreshPreview();
	}

	public void reorderAnimations(List<PhotonAnimationDefinition> ordered) {
		if (library.animations.equals(ordered) || ordered.size() != library.animations.size()
				|| !new HashSet<>(ordered).equals(new HashSet<>(library.animations))) {
			return;
		}

		edit(() -> {
			library.animations.clear();
			library.animations.addAll(ordered);
		});
	}

	public void selectTarget(UUID id) {
		targetSelection = id;
		preview.inspect(id);
	}

	public void rebuildCurves() {
		context.loadSelected();
	}

	private void showPage(PhotonAnimationForms.Page page) {
		saveFormState();
		currentPage = page;
		var content = pageContents.get(page);

		if (content == null) {
			return;
		}

		if (inspector.getParent() != content) {
			content.addChildren(inspector, recordingFields);
		}

		formGroups.clear();
		inspector.clearAllChildren();
		PhotonAnimationForms.build(this, page, inspector);
		formAnimation = selectedId;
		formPage = page;
		var state = formStates.get(new FormKey(formAnimation, page));

		if (state != null) {
			state.restore(inspector, pageScrollers.get(page), formGroups);
		}
	}

	private void saveFormState() {
		if (formPage == null) {
			return;
		}

		formStates.put(new FormKey(formAnimation, formPage),
				PhotonAnimationFormState.capture(inspector, pageScrollers.get(formPage), formGroups));
	}

	public void bindFormGroup(ConfiguratorGroup group, Object model) {
		bindFormGroup(group, model, "");
	}

	public void bindFormGroup(ConfiguratorGroup group, Object model, String role) {
		var identity = formIdentities.computeIfAbsent(model, _ -> UUID.randomUUID());
		formGroups.put(group, new PhotonAnimationFormState.GroupIdentity(identity, role));
	}

	public void replaceFormIdentity(Object previous, Object replacement) {
		var identity = formIdentities.get(previous);

		if (identity != null) {
			formIdentities.put(replacement, identity);
		}
	}

	public List<UUID> captureFormIdentities(List<Object> models) {
		Set<Object> retained = Collections.newSetFromMap(new IdentityHashMap<>());
		retained.addAll(models);
		formIdentities.keySet().retainAll(retained);
		return models.stream().map(model -> formIdentities.computeIfAbsent(model, _ -> UUID.randomUUID())).toList();
	}

	public void restoreFormIdentities(List<Object> models, List<UUID> identities) {
		if (models.size() != identities.size()) {
			return;
		}

		formIdentities.clear();

		for (int index = 0; index < models.size(); index++) {
			formIdentities.put(models.get(index), identities.get(index));
		}
	}

	public void scrub(double time) {
		if (library.migrationBlocked() || !preview.editing() || effectFinished) {
			return;
		}

		var runtime = preview.runtimeOrNull();

		if (runtime == null) {
			return;
		}

		withEditing(() -> {
			if (!timelinePlayback || !testAnimations.isEmpty()) {
				replayFromStart = true;
			}

			preserveTestPlayback();

			if (runtime.playback(editingDefinition().id) == null) {
				replayFromStart = true;
				prepareTimelineSimulation();
			}

			runtime.resume(runtime.playback(editingDefinition().id));
			runtime.setPreviewDispatch(false);
			simulationRequested = true;
			preview.scene().requestSimulateTo(Math.max(0, Math.round(time)));
		});
	}

	public void prepareTimelineSimulation() {
		var runtime = preview.runtimeOrNull();

		if (library.migrationBlocked() || runtime == null || effectFinished) {
			return;
		}

		runtime.previewOnly();
		runtime.setPreviewDispatch(false);
		runtime.playPreview(editingDefinition(), 0);
		preview.adoptRuntime();
		PhotonEmissionPrimeUtil.prime(owner.runtime);
		PhotonEmissionPrimeUtil.flushScene(preview.scene().particleManager);
	}

	public boolean replayFromStart() {
		return replayFromStart;
	}

	public void finishTimelineSimulation() {
		simulationRequested = false;
		var runtime = preview.runtimeOrNull();

		if (library.migrationBlocked() || !preview.editing() || runtime == null || effectFinished) {
			clearQueuedPlaybackCommand();
			return;
		}

		replayFromStart = false;
		runtime.pause(runtime.playback(editingDefinition().id));
		runtime.samplePreviewAt(editingDefinition(), context.time());
		restoreTestPlayback();

		if (flushQueuedPlaybackCommands() && (simulationRequested || closed || effectFinished)) {
			return;
		}

		if (resumeAfterSimulation) {
			resumeAfterSimulation = false;
			resumeTestPlaybackAfterSimulation = false;
			playFromCursor();
		} else if (resumeTestPlaybackAfterSimulation) {
			resumeTestPlaybackAfterSimulation = false;
			continuePreviewAfterSimulation(false);
		}
	}

	public void previewTime(double time) {
		resumeAfterSimulation = false;
		resumeTestPlaybackAfterSimulation = false;
		preview.scene().particleManager.pause();
		context.seek(time);
	}

	public double previewTime() {
		return context.time();
	}

	public double playheadTime(float partialTick) {
		if (!preview.active() || !preview.scene().particleManager.isPlaying()) {
			return context.time();
		}

		var runtime = preview.runtime();
		var playback = runtime.playback(editingDefinition().id);

		if (playback == null) {
			return context.time();
		}

		if (timelinePlayback) {
			return timelinePaused ? pausedTimelineTime : playbackCursor + runtime.effectTime(partialTick) - playbackEpoch;
		}

		return hasSelection() ? runtime.playbackTime(selected().id.toString(), partialTick)
				: runtime.playbackTime(playback, partialTick);
	}

	public double playbackEndTime() {
		return playheadTime(0);
	}

	public double timelineDuration() {
		return editingDefinition().authoredDuration();
	}

	public boolean effectFinished() {
		return effectFinished;
	}

	public boolean previewFinished() {
		var runtime = preview.runtimeOrNull();
		return effectFinished || runtime != null && runtime.previewFinished();
	}

	public void syncPreviewDispatch(boolean playing) {
		var runtime = preview.runtimeOrNull();

		if (preview.editing() && runtime != null) {
			runtime.setPreviewDispatch(playing && !simulationRequested && !effectFinished);
		}
	}

	public void restartTimelinePreview() {
		context.setRecordingTrack(null);

		if (!preview.editing() || owner.runtime == null) {
			return;
		}

		effectFinished = false;
		preserveTestPlayback();
		preview.scene().particleManager.pause();
		preview.scene().reset();
		PhotonAnimationPreviewScope.enter();

		try {
			owner.runtime.emit(preview.scene().effect);
		} finally {
			PhotonAnimationPreviewScope.leave();
		}

		prepareTimelineSimulation();
		context.followTime(0);
		simulationRequested = false;
		clearQueuedPlaybackCommand();
		restoreTestPlayback();
		playFromCursor();
	}

	private void update() {
		if (closed || !preview.editing()) {
			return;
		}

		preview.sync();
		syncPreviewRecording();
		var selectedTargets = owner.hierarchyView.treeList.getSelected();
		var target = selectedTargets.size() == 1 ? selectedTargets.iterator().next().getKey().id() : null;

		if (!Objects.equals(target, targetSelection)) {
			targetSelection = target;

			if (target != null) {
				selectTarget(target);
			}

			recordingChanged();
		}

		if (needsRebuild && !editingText()) {
			needsRebuild = false;
			showPage(currentPage);
		}

		if (!preview.active() || !preview.scene().particleManager.isPlaying()) {
			return;
		}

		if (preview.runtime().playback(editingDefinition().id) != null) {
			context.followTime(playheadTime(0));
		}
	}

	private boolean editingText() {
		var ui = getModularUI();
		var focused = ui == null ? null : ui.getFocusedElement();

		if (ui != null && ui.getDragHandler().isDragging()) {
			return true;
		}

		return PhotonAnimationUiUtil.editingInput(focused);
	}

	@Override
	protected void drawBackgroundAdditional(@Nonnull IGUIContext graphics) {
		preview.sync();
		super.drawBackgroundAdditional(graphics);
	}

	private void command(UIEvent event) {
		if (PhotonResourceClipboardUtil.textTarget(event.target)) {
			return;
		}

		if (!CommandEvents.UNDO.equals(event.command) && !CommandEvents.REDO.equals(event.command)) {
			return;
		}

		if (UIEvents.EXECUTE_COMMAND.equals(event.type)) {
			context.setRecordingTrack(null);

			if (CommandEvents.UNDO.equals(event.command)) {
				owner.historyView.undo();
			} else {
				owner.historyView.redo();
			}
		}

		event.stopImmediatePropagation();
	}


	public void closeWorkspace() {
		cleanup();
		removeSelf();
	}

	private void cleanup() {
		if (closed) {
			return;
		}

		context.setRecordingTrack(null);
		clearQueuedPlaybackCommand();
		clearTestPlayback();
		context.suspend();
		closed = true;
		preview.close();
		context.restoreSceneState(true);
		panels.close();
		outline.close();
		formGroups.clear();
		formIdentities.clear();
		formStates.clear();
		pageScrollers.clear();
		pageContents.clear();
		projectLibraryValidation = null;
	}

	public FXTimelineView timelineView() {
		return owner.timelineView;
	}

	public PhotonAnimationTimelineContext timelineContext() {
		return context;
	}

	public void playFromCursor() {
		effectFinished = false;

		if (simulationRequested) {
			resumeAfterSimulation = true;
			resumeTestPlaybackAfterSimulation = true;
			return;
		}

		withEditing(() -> {
			if (!preview.active()) {
				preview.reset();
			}

			var runtime = preview.runtimeOrNull();

			if (runtime == null) {
				return;
			}

			var playback = runtime.playback(editingDefinition().id);

			if (playback == null || !playback.timelinePreview) {
				replayFromStart = true;
				scrub(context.time());
				resumeAfterSimulation = true;
				resumeTestPlaybackAfterSimulation = true;
				return;
			}

			runtime.resume(playback);
			timelinePlayback = true;
			timelinePaused = false;
			playbackCursor = context.time();
			playbackEpoch = preview.runtime().effectTime();
			preview.runtime().setPreviewDispatch(true);
			preview.scene().particleManager.play();
		});
	}

	public void clearQueuedPlaybackCommand() {
		afterSimulation.clear();
		resumeAfterSimulation = false;
		resumeTestPlaybackAfterSimulation = false;
	}

	public void stopAtCursor() {
		if (deferPlaybackCommand(this::stopAtCursor)) {
			return;
		}

		context.followTime(playheadTime(0));
		timelinePlayback = false;
		timelinePaused = false;
		resumeAfterSimulation = false;
		testAnimations.remove(editingDefinition().id);
		withEditing(() -> {
			var runtime = preview.runtimeOrNull();
			var playback = runtime == null ? null : runtime.playback(editingDefinition().id);

			if (playback != null) {
				runtime.stop(playback);
			}
		});
	}

	public void refreshTimelinePreview() {
		if (recordingTrack() != null) {
			recordingModelPending = true;
			sceneRefreshPending = true;
			return;
		}

		scrub(context.time());
	}

	public void nativeRecordingPolled() {
		syncPreviewRecording();

		if (!recordingModelPending || closed) {
			return;
		}

		recordingModelPending = false;
		context.storeModel();
		stageRecording();
	}

	public void stageRecording() {
		if (library.migrationBlocked()) {
			return;
		}

		controller.definition = library.write(Platform.getFrozenRegistry());
	}

	public void recordingChanged() {
		recordingFields.clearAllChildren();
		var recording = recordingTrack() != null;
		recordingFields.addChild(new TextElement().setText(
				PhotonAnimationTextUtil.key(recording ? "record_values" : "record_off")));
	}

	public @Nullable Track recordingTrack() {
		return context.animationTimeline().leafTracks(true).stream()
				.filter(owner.timelineView::isRecording).findFirst().orElse(null);
	}

	public void syncPreviewRecording() {
		var runtime = preview.runtimeOrNull();

		if (preview.editing() && runtime != null) {
			runtime.setPreviewRecording(recordingTrack() != null && !preview.scene().particleManager.isPlaying());
		}
	}

	public void nativeRecordingChanged() {
		syncPreviewRecording();
		recordingChanged();

		if (recordingTrack() == null && sceneRefreshPending) {
			recordingModelPending = false;
			sceneRefreshPending = false;
			refreshAuthoredScene();
		}
	}

	public void registerSceneHistorySource(Object source) {
		sceneHistorySources.add(source);
	}

	public void sceneInspectorChanged(Configurator field) {
		sceneBaseChanged(field);
	}

	public void sceneBaseChanged(Object source) {
		registerSceneHistorySource(source);

		if (closed || !preview.editing()) {
			return;
		}

		if (recordingTrack() != null) {
			sceneRefreshPending = true;
			return;
		}

		refreshAuthoredScene();
	}

	public void sceneHistoryRestored() {
		if (!closed && preview.editing()) {
			refreshAuthoredScene();
		}
	}

	private void refreshAuthoredScene() {
		if (owner.runtime == null || effectFinished) {
			return;
		}

		var playing = preview.scene().particleManager.isPlaying();
		var state = context.time();
		var timeline = timelinePlaybackActive();
		preserveTestPlayback();
		preview.scene().particleManager.pause();
		replayFromStart = true;
		context.followTime(state);
		simulationRequested = true;
		resumeAfterSimulation = playing && timeline;
		resumeTestPlaybackAfterSimulation = playing;
		preview.scene().requestSimulateTo(Math.max(0, Math.round(state)));
	}

	public boolean canRestoreHistory(@Nullable HistoryItem item) {
		if (item == historyBoundary) {
			return true;
		}

		return item != null && (sceneHistorySources.contains(item.source()) || item.source() == this
				|| item.source() instanceof PhotonAnimationEditSnapshot snapshot && snapshot.belongsTo(this)
				|| item.source() instanceof InspectorHistorySource source && source.workspace() == this);
	}

	/// 只把当前主实例中的项目模板视为本库来源；外部与已生成子实例的快照不共享定义。
	public boolean projectTemplateUsesLibrary(Track track) {
		var runtime = owner.runtime;

		if (runtime == null || !(runtime.objects.get(track.targetId()) instanceof PhotonEmitterSpawner spawner)
				|| spawner.config.source != PhotonEmitterSpawnConfig.Source.PROJECT || spawner.templateScope() != runtime) {
			return false;
		}

		var controllers = runtime.objects.values().stream().filter(PhotonAnimationController.class::isInstance)
				.map(PhotonAnimationController.class::cast).toList();

		if (controllers.size() != 1 || !controllers.getFirst().id().equals(controller.id())) {
			return false;
		}

		var definition = controllers.getFirst().definition;
		var validation = projectLibraryValidation;

		if (validation == null || validation.definition() != definition || validation.saved() != saved) {
			validation = new ProjectLibraryValidation(definition, saved, definition.equals(saved));
			projectLibraryValidation = validation;
		}

		return validation.matches();
	}

	public PhotonAnimationLibrary library() {
		return library;
	}

	public PhotonAnimationDefinition selected() {
		return Objects.requireNonNull(library.find(Objects.requireNonNull(selectedId).toString()));
	}

	/// 未选择命名动画时，原 TL 编辑项目专用测试定义，不发布到运行动画库。
	public PhotonAnimationDefinition editingDefinition() {
		return hasSelection() ? selected() : testDefinition;
	}

	public PhotonAnimationDefinition testDefinition() {
		return testDefinition;
	}

	public PhotonAnimationTestControls testControls() {
		return testControls;
	}

	public boolean hasSelection() {
		return !library.migrationBlocked() && selectedId != null && library.find(selectedId.toString()) != null;
	}

	public PhotonAnimationPreview preview() {
		return preview;
	}

	public FXEditor owner() {
		return owner;
	}
}
