package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Menu;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackEditor;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.ClipTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackUIState;
import net.minecraft.nbt.ListTag;
import org.joml.Vector2f;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/// 命名制作的独立宿主；本地历史保存原树，恢复后不继续执行旧轨道闭包。
public class PhotonNamedTimelineContext implements TimelineContext, PhotonTimelineEditAccess {
	private static final Map<FXEditor, WeakReference<PhotonNamedTimelineContext>> INSPECTORS = new WeakHashMap<>();

	private record ViewState(float scale, float scroll, long time) {
	}

	private final FXEditor editor;
	private final FXRuntime runtime;
	private final boolean projectTest;
	private UIElement origin;
	private final Runnable rebuild;
	private final Consumer<ListTag> publish;
	private final Runnable preview;
	private PhotonNamedTrackDraft draft;
	private ListTag checkpoint;
	private final ArrayDeque<ListTag> undo = new ArrayDeque<>();
	private final ArrayDeque<ListTag> redo = new ArrayDeque<>();
	private final List<LaneItem> laneItems = new ArrayList<>();
	private final Set<Clip> selectedClips = new LinkedHashSet<>();
	private final Map<Clip, Track> clipTracks = new IdentityHashMap<>();
	private final Map<Track, TrackUIState> states = new IdentityHashMap<>();
	private final Map<Clip, Double> dragStarts = new IdentityHashMap<>();
	private @Nullable Track selectedTrack;
	private @Nullable Track recording;
	private @Nullable Clip primary;
	private @Nullable Clip dragAnchor;
	private @Nullable ListTag scrubBefore;
	private double dragOffset;
	private boolean invalidDrag;
	private float scale = 2;
	private float scroll;
	private long time;
	private boolean closed;

	public PhotonNamedTimelineContext(FXEditor editor, UIElement origin, ListTag source,
	                                  Runnable rebuild, Consumer<ListTag> publish, Runnable preview, boolean projectTest) {
		if (editor.runtime == null) {
			throw new IllegalStateException("工程预览实例未创建。");
		}

		this.editor = editor;
		this.projectTest = projectTest;
		runtime = editor.runtime;
		this.origin = origin;
		this.rebuild = rebuild;
		this.publish = publish;
		this.preview = preview;
		draft = new PhotonNamedTrackDraft(source, projectTest);
		checkpoint = source.copy();
	}

	public PhotonNamedTrackDraft draft() {
		return draft;
	}

	public void addTrack(Track track) {
		requireCurrent();
		if (draft.timeline().parentListOf(track) != null) {
			throw new IllegalArgumentException("轨道已属于当前草稿。");
		}

		PhotonEditorProjectGuardUtil.requireSupportedAuthoringTrack(Timeline.writeTrack(track, Platform.getFrozenRegistry()), projectTest);
		pushEdit("add_track", () -> {
			draft.timeline().tracks().add(track);
			requestRebuild();
		}, () -> {
		});
	}

	public void origin(UIElement lane) {
		origin = lane;
	}

	public void duplicate(Track track) {
		var parent = draft.timeline().parentListOf(track);
		if (parent == null || track.lock()) {
			throw new IllegalStateException("轨道已锁定或不属于当前草稿。");
		}
		pushEdit("duplicate_track", () -> {
			parent.add(parent.indexOf(track) + 1, draft.duplicate(track));
			requestRebuild();
		}, () -> {
		});
	}

	public void move(Track track, int offset) {
		var parent = draft.timeline().parentListOf(track);
		if (parent == null || track.lock()) {
			throw new IllegalStateException("轨道已锁定或不属于当前草稿。");
		}
		int target = parent.indexOf(track) + offset;
		if (target < 0 || target >= parent.size()) {
			throw new IllegalArgumentException("轨道排序目标超出范围。");
		}
		pushEdit("move_track", () -> {
			parent.remove(track);
			parent.add(target, track);
			requestRebuild();
		}, () -> {
		});
	}

	public TrackUIState state(Track track) {
		return states.computeIfAbsent(track, value -> editorFor(value).createState());
	}

	private static TrackEditor editorFor(Track track) {
		var type = track.type();
		if (type == null) {
			throw new IllegalArgumentException("制作轨道类型未注册。");
		}
		return type.editor();
	}

	public void time(long value) {
		time = Math.max(0, value);
		preview.run();
		refreshLaneLayout();
	}

	public ListTag tracks() {
		return draft.write();
	}

	public void undo() {
		history(undo, redo);
	}

	public void redo() {
		history(redo, undo);
	}

	public void deleteSelection() {
		if (!selectedClips.isEmpty()) {
			var removed = new ArrayList<>(selectedClips);
			pushEdit("remove_clips", () -> {
				for (var clip : removed) {
					var track = clipTracks.get(clip);
					if (track != null && !track.lock()) {
						track.clips().remove(clip);
					}
				}
				selectedClips.clear();
				primary = null;
				requestRebuild();
			}, () -> {
			});
			return;
		}

		var track = selectedTrack;
		if (track == null || track.lock()) {
			return;
		}
		if (editorFor(track).deleteSelection(this, track, state(track))) {
			return;
		}
		pushEdit("remove_track", () -> {
			editorFor(track).onRemoved(this, track, state(track));
			var parent = draft.timeline().parentListOf(track);
			if (parent != null) {
				parent.remove(track);
			}
			selectedTrack = null;
			requestRebuild();
		}, () -> {
		});
	}

	private void history(ArrayDeque<ListTag> source, ArrayDeque<ListTag> destination) {
		requireCurrent();
		setRecordingTrack(null);
		cancelScrub();
		if (source.isEmpty()) {
			return;
		}

		var before = checkpoint.copy();
		var candidate = source.getFirst().copy();
		try {
			restore(candidate);
			publish.accept(checkpoint.copy());
		} catch (RuntimeException failure) {
			restore(before);
			throw failure;
		}

		source.pop();
		destination.push(before);
		preview.run();
	}

	private void restore(ListTag source) {
		draft = new PhotonNamedTrackDraft(source, projectTest);
		checkpoint = source.copy();
		dragAnchor = null;
		dragStarts.clear();
		invalidDrag = false;
		scrubBefore = null;
		selectedTrack = null;
		primary = null;
		selectedClips.clear();
		states.clear();
		clipTracks.clear();
		laneItems.clear();
		clearInspector();
		rebuild.run();
	}

	private void applied(ListTag before) {
		requireCurrent();
		var after = draft.write();
		if (after.equals(before)) {
			return;
		}

		publish.accept(after.copy());
		if (scrubBefore == null) {
			undo.push(before.copy());
			redo.clear();
		}
		checkpoint = after;
	}

	@Override
	public FXEditor editor() {
		return editor;
	}

	@Override
	public FXRuntime runtime() {
		return runtime;
	}

	@Override
	public float scale() {
		return scale;
	}

	@Override
	public float scrollTicks() {
		return scroll;
	}

	@Override
	public float originX() {
		return origin.getContentX();
	}

	@Override
	public float tickToLocalX(double tick) {
		return (float) ((tick - scroll) * scale);
	}

	@Override
	public double xToTick(float x) {
		return (x - originX()) / scale + scroll;
	}

	@Override
	public long currentTimeTicks() {
		return time;
	}

	@Override
	public double majorTickInterval() {
		return Math.max(1, Math.pow(10, Math.floor(Math.log10(60 / scale))));
	}

	@Override
	public void drawPlayhead(GUIContext graphics, float x, float y, float width, float height, float partialTick) {
		float position = originX() + tickToLocalX(time);
		if (position < x || position > x + width) {
			return;
		}
		DrawerHelperClient.drawSolidRect(graphics, position, y, 1, height, 0xffeeeeee);
	}

	@Override
	public void zoom(UIEvent event) {
		var tick = xToTick(event.x);
		scale = Math.clamp(scale * (event.deltaY > 0 ? 1.1f : 1 / 1.1f), 0.05f, 50);
		scroll = (float) Math.max(0, tick - (event.x - originX()) / scale);
		refreshLaneLayout();
		event.stopPropagation();
	}

	@Override
	public void pushEdit(String name, Runnable doFn, Runnable undoFn) {
		requireCurrent();
		var before = checkpoint.copy();
		try {
			doFn.run();
			applied(before);
		} catch (RuntimeException failure) {
			restore(before);
			throw failure;
		}
	}

	@Override
	public void pushApplied(String name, Runnable redoFn, Runnable undoFn) {
		applied(checkpoint);
	}

	@Override
	public void refreshPreview() {
		preview.run();
	}

	@Override
	public void beginScrub() {
		requireCurrent();
		if (scrubBefore == null) {
			scrubBefore = checkpoint.copy();
		}
	}

	@Override
	public void endScrub() {
		requireCurrent();
		if (scrubBefore != null && !scrubBefore.equals(checkpoint)) {
			undo.push(scrubBefore.copy());
			redo.clear();
		}
		scrubBefore = null;
	}

	public void cancelScrub() {
		requireCurrent();
		if (scrubBefore != null) {
			restore(scrubBefore);
			scrubBefore = null;
			publish.accept(checkpoint.copy());
			preview.run();
		}
	}

	@Override
	public void openMenu(float x, float y, TreeBuilder.Menu menu) {
		if (menu.isEmpty()) {
			return;
		}

		UIElement host = origin;
		while (host != null && !(host instanceof Dialog)) {
			host = host.getParent();
		}
		if (host == null) {
			editor.openMenu(x, y, menu);
			return;
		}

		var point = host.worldToLocal(new Vector2f(x, y));
		var popup = new Menu<>(menu.build(), TreeBuilder.Menu::uiProvider)
				.setHoverTextureProvider(TreeBuilder.Menu::hoverTextureProvider)
				.setOnNodeClicked(TreeBuilder.Menu::handle)
				.setCloseOnClick(true);
		popup.layout(layout -> layout.left(point.x).top(point.y));
		host.addChild(popup);
	}

	@Override
	public void requestRebuild() {
		laneItems.clear();
		clipTracks.clear();
		rebuild.run();
	}

	@Override
	public void selectTrack(Track track) {
		requireOwned(track);
		INSPECTORS.put(editor, new WeakReference<>(this));
		selectedTrack = track;
		primary = null;
		selectedClips.clear();
		editor.inspectorView.inspect(editorFor(track).trackConfigurator(this, track), null, () -> {
		});
	}

	@Override
	public void setActiveTrack(Track track) {
		requireOwned(track);
		selectedTrack = track;
	}

	@Override
	public void inspectProperty(Track track, IConfigurable configurable) {
		requireOwned(track);
		INSPECTORS.put(editor, new WeakReference<>(this));
		selectedTrack = track;
		editor.inspectorView.inspect(configurable, null, () -> {
		});
	}

	@Override
	public boolean isTrackSelected(Track track) {
		return selectedTrack == track && primary == null;
	}

	@Override
	public void setRecordingTrack(@Nullable Track track) {
		if (track != null) {
			requireOwned(track);
		}
		if (recording == track) {
			return;
		}
		if (recording != null) {
			editorFor(recording).endRecording(this, recording, state(recording));
		}
		recording = track;
		if (track != null) {
			editor.sceneView.particleManager.pause();
			editorFor(track).beginRecording(this, track, state(track));
		}
	}

	public void pollRecording() {
		requireCurrent();
		if (recording != null) {
			editorFor(recording).pollRecording(this, recording, state(recording));
		}
	}

	@Override
	public boolean isRecording(Track track) {
		return recording == track;
	}

	@Override
	public void selectClip(Track track, Clip clip) {
		requireClip(track, clip);
		INSPECTORS.put(editor, new WeakReference<>(this));
		selectedTrack = track;
		primary = clip;
		selectedClips.clear();
		selectedClips.add(clip);
		if (editorFor(track) instanceof ClipTrackEditor lane) {
			editor.inspectorView.inspect(lane.clipConfigurator(this, track, clip), null, () -> {
			});
		}
	}

	@Override
	public Clip selectedClip() {
		return primary;
	}

	@Override
	public Track selectedClipTrack() {
		return primary == null ? null : clipTracks.get(primary);
	}

	@Override
	public boolean isClipSelected(Clip clip) {
		return selectedClips.contains(clip);
	}

	@Override
	public Set<Clip> selectedClips() {
		return Set.copyOf(selectedClips);
	}

	@Override
	public void selectClips(Collection<Clip> clips, boolean additive) {
		requireCurrent();
		for (var clip : clips) {
			requireClip(clipTracks.get(clip), clip);
		}
		if (!additive) {
			selectedClips.clear();
		}
		selectedClips.addAll(clips);
		primary = selectedClips.isEmpty() ? null : selectedClips.iterator().next();
		selectedTrack = primary == null ? null : clipTracks.get(primary);
	}

	@Override
	public void toggleClipSelection(Track track, Clip clip) {
		requireClip(track, clip);
		if (!selectedClips.remove(clip)) {
			selectedClips.add(clip);
		}
		primary = selectedClips.isEmpty() ? null : selectedClips.iterator().next();
		selectedTrack = primary == null ? null : clipTracks.get(primary);
	}

	@Override
	public boolean isClipGroupDragInvalid() {
		return invalidDrag;
	}

	@Override
	public void beginClipGroupDrag(Clip anchor, double grabOffsetTicks) {
		requireClip(clipTracks.get(anchor), anchor);
		if (!selectedClips.contains(anchor)) {
			throw new IllegalArgumentException("拖动锚点不属于当前选择。");
		}
		dragAnchor = anchor;
		dragOffset = grabOffsetTicks;
		dragStarts.clear();
		for (var clip : selectedClips) {
			dragStarts.put(clip, clip.start());
		}
		beginScrub();
	}

	@Override
	public void updateClipGroupDrag(float cursorX, float cursorY, boolean ctrl) {
		requireCurrent();
		if (dragAnchor == null) {
			return;
		}
		var delta = snapTick(xToTick(cursorX) - dragOffset, dragAnchor, ctrl) - dragStarts.get(dragAnchor);
		invalidDrag = false;
		for (var entry : dragStarts.entrySet()) {
			var clip = entry.getKey();
			clip.start(entry.getValue() + delta);
			var track = clipTracks.get(clip);
			if (clip.start() < 0 || track == null || track.lock()) {
				invalidDrag = true;
				continue;
			}
			if (editorFor(track) instanceof ClipTrackEditor lane
					&& track.clips().stream().anyMatch(other -> !dragStarts.containsKey(other) && lane.clipsConflict(clip, other))) {
				invalidDrag = true;
			}
		}
		refreshLaneLayout();
		preview.run();
	}

	@Override
	public void endClipGroupDrag(boolean commit) {
		if (commit && !invalidDrag) {
			applied(checkpoint);
		} else {
			for (var entry : dragStarts.entrySet()) {
				entry.getKey().start(entry.getValue());
			}
			preview.run();
		}
		dragAnchor = null;
		dragStarts.clear();
		invalidDrag = false;
		endScrub();
		refreshLaneLayout();
	}

	@Override
	public void registerLaneItem(UIElement element, Runnable reposition) {
		laneItems.add(new LaneItem(element, reposition));
	}

	@Override
	public void refreshLaneLayout() {
		for (var item : List.copyOf(laneItems)) {
			item.reposition().run();
		}
	}

	@Override
	public void registerClipView(Track track, Clip clip, UIElement element) {
		clipTracks.put(clip, track);
		registerLaneItem(element, () -> element.layout(layout -> {
			layout.left(tickToLocalX(clip.start()));
			layout.width((float) Math.max(2, clip.duration() * scale));
		}));
	}

	@Override
	public Map<Track, TrackUIState> lobecorp$states() {
		return states;
	}

	@Override
	public Set<Track> lobecorp$selectedTracks() {
		return selectedTrack == null ? Set.of() : Set.of(selectedTrack);
	}

	@Override
	public TrackEditor lobecorp$trackEditor(Track track) {
		return editorFor(track);
	}

	@Override
	public void lobecorp$fitTime(double start, double end) {
		if (!Double.isFinite(start) || !Double.isFinite(end)) {
			return;
		}
		scale = Math.clamp((float) (Math.max(1, origin.getContentWidth()) / Math.max(1, end - start + 10)), 0.05f, 50);
		scroll = (float) Math.max(0, start - 5);
		refreshLaneLayout();
	}

	@Override
	public void lobecorp$duplicateNative() {
		if (selectedTrack != null) {
			duplicate(selectedTrack);
		}
	}

	@Override
	public void lobecorp$cancelClipDrag() {
		endClipGroupDrag(false);
		cancelScrub();
	}

	@Override
	public void lobecorp$pruneSelection(Timeline timeline) {
		selectedClips.removeIf(clip -> !clipTracks.containsKey(clip) || timeline.parentListOf(clipTracks.get(clip)) == null);
		if (primary != null && !selectedClips.contains(primary)) {
			primary = null;
		}
		if (selectedTrack != null && timeline.parentListOf(selectedTrack) == null) {
			selectedTrack = null;
		}
		states.keySet().removeIf(track -> timeline.parentListOf(track) == null);
	}

	@Override
	public Object lobecorp$captureViewState() {
		return new ViewState(scale, scroll, time);
	}

	@Override
	public void lobecorp$restoreViewState(Object snapshot) {
		if (snapshot instanceof ViewState(float savedScale, float savedScroll, long savedTime)) {
			scale = savedScale;
			scroll = savedScroll;
			time = savedTime;
			refreshLaneLayout();
		}
	}

	@Override
	public double snapTick(double tick, @Nullable Clip exclude, boolean ctrl) {
		return Math.max(0, ctrl ? tick : Math.rint(tick));
	}

	@Override
	public double snapMoveStart(double start, double duration, @Nullable Clip exclude, boolean ctrl) {
		return snapTick(start, exclude, ctrl);
	}

	@Override
	public double snapKeyTick(double tick, boolean ctrl) {
		return snapTick(tick, null, ctrl);
	}

	@Override
	public double snapKeyTick(double tick, boolean ctrl, @Nullable Set<?> exclude) {
		return snapKeyTick(tick, ctrl);
	}

	@Override
	public void setDragGuide(@Nullable Clip clip) {
		refreshLaneLayout();
	}

	@Override
	public void setDragGuideTicks(double start, double end) {
		refreshLaneLayout();
	}

	@Override
	public void clearDragGuideTicks() {
		refreshLaneLayout();
	}

	@Override
	public void addClip(Track track, Clip clip) {
		requireOwned(track);
		if (draft.timeline().leafTracks(true).stream().anyMatch(candidate -> candidate.clips().contains(clip))) {
			throw new IllegalArgumentException("片段已属于当前草稿。");
		}
		if (track.lock()) {
			throw new IllegalStateException("轨道已锁定。");
		}
		if (!Double.isFinite(clip.start()) || clip.start() < 0 || !Double.isFinite(clip.end()) || clip.duration() < 0
				|| !Float.isFinite(clip.speed()) || clip.speed() < 0) {
			throw new IllegalArgumentException("片段窗口无效。");
		}
		if (editorFor(track) instanceof ClipTrackEditor lane && track.clips().stream().anyMatch(other -> lane.clipsConflict(clip, other))) {
			throw new IllegalArgumentException("片段窗口与当前轨道内容冲突。");
		}
		pushEdit("add_clip", () -> {
			track.clips().add(clip);
			requestRebuild();
		}, () -> {
		});
	}

	@Override
	public void removeClip(Track track, Clip clip) {
		requireClip(track, clip);
		if (track.lock()) {
			throw new IllegalStateException("轨道已锁定。");
		}
		pushEdit("remove_clip", () -> {
			track.clips().remove(clip);
			requestRebuild();
		}, () -> {
		});
	}

	@Override
	public void addChildTrack(TrackGroup group, Track child) {
		requireOwned(group);
		if (draft.timeline().parentListOf(child) != null || child == group || Timeline.isDescendant(child, group)) {
			throw new IllegalArgumentException("子轨道已属于当前草稿。");
		}
		PhotonEditorProjectGuardUtil.requireSupportedAuthoringTrack(Timeline.writeTrack(child, Platform.getFrozenRegistry()), projectTest);
		if (group.lock()) {
			throw new IllegalStateException("轨道组已锁定。");
		}
		pushEdit("add_track", () -> {
			group.children().add(child);
			requestRebuild();
		}, () -> {
		});
	}

	@Override
	public void bind(Track track, @Nullable UUID targetId) {
		requireOwned(track);
		if (track.lock()) {
			throw new IllegalStateException("轨道已锁定。");
		}
		pushEdit("bind", () -> {
			editorFor(track).onTargetWillChange(this, track, track.targetId());
			track.targetId(targetId);
			requestRebuild();
		}, () -> {
		});
	}

	@Override
	public double defaultClipDuration(@Nullable UUID objectId) {
		var object = objectId == null ? null : runtime.objects.get(objectId);
		return object instanceof Emitter emitter ? Math.max(1, emitter.getLifetime()) : 20;
	}

	public void close() {
		if (closed) {
			return;
		}
		try {
			if (editor.runtime == runtime) {
				setRecordingTrack(null);
				cancelScrub();
			}
		} finally {
			recording = null;
			scrubBefore = null;
			dragAnchor = null;
			dragStarts.clear();
			closed = true;
			clearInspector();
		}
	}

	private void clearInspector() {
		var inspector = INSPECTORS.get(editor);
		if (inspector != null && inspector.get() == this) {
			INSPECTORS.remove(editor);
			editor.inspectorView.clear();
		}
	}

	private Track requireOwned(@Nullable Track track) {
		requireCurrent();
		if (track == null || draft.timeline().parentListOf(track) == null) {
			throw new IllegalStateException("轨道不属于当前草稿，请重新读取当前轨道。");
		}
		return track;
	}

	private void requireClip(@Nullable Track track, Clip clip) {
		var current = requireOwned(track);
		if (!current.clips().contains(clip)) {
			throw new IllegalStateException("片段不属于当前轨道。");
		}
	}

	private void requireCurrent() {
		if (closed || editor.runtime != runtime) {
			throw new IllegalStateException("制作上下文已关闭或场景已重建。");
		}
	}
}
