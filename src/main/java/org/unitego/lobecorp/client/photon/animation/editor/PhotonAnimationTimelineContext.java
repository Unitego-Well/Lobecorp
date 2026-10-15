package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.ExprClip;
import com.lowdragmc.photon.client.fx.timeline.CurveClip;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.ColorAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.editor.PhotonTimelineEditAccess;
import org.unitego.lobecorp.client.photon.editor.PhotonTimelineViewState;
import org.unitego.lobecorp.mixin.photon.editor.client.AnimationTrackUIStateAccessor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/// 同一个原生 TL 的命名动画上下文，仅持有模型、时钟和模式状态。
@SuppressWarnings("resource") // 工作区拥有借用的编辑器及预览。
public class PhotonAnimationTimelineContext {
	private final PhotonAnimationWorkspace workspace;
	private final PhotonAnimationTimelineModel model = new PhotonAnimationTimelineModel();
	private final Map<UUID, Object> viewStates = new HashMap<>();
	private final Map<UUID, Double> clocks = new HashMap<>();
	private @Nullable Object sceneState;
	private @Nullable PhotonTimelineViewState emptyState;
	private @Nullable UUID loadedAnimation;
	private long sceneTime;
	private boolean scenePlaying;
	private boolean restoringScene;
	private double time;
	private boolean active;
	private boolean scrubWasPlaying;
	private boolean scrubTimelineWasPlaying;

	public PhotonAnimationTimelineContext(PhotonAnimationWorkspace workspace) {
		this.workspace = workspace;
	}

	public FXTimelineView view() {
		return workspace.owner().timelineView;
	}

	private PhotonTimelineEditAccess access() {
		return (PhotonTimelineEditAccess) view();
	}

	public Timeline animationTimeline() {
		return model.timeline();
	}

	public void captureSceneState() {
		if (active) {
			return;
		}

		if (restoringScene) {
			editor().sceneView.simulateTo(sceneTime);
			finishSceneRestore();
		}

		view().setRecordingTrack(null);
		access().lobecorp$cancelClipDrag();
		sceneState = access().lobecorp$captureViewState();
		sceneTime = editor().sceneView.particleManager.getTime();
		scenePlaying = editor().sceneView.particleManager.isPlaying();

		if (sceneState instanceof PhotonTimelineViewState state) {
			emptyState = new PhotonTimelineViewState(state.scale(), 0, state.rowHeight(),
					Map.of(), Set.of(), null, Set.of(), null, null, null,
					state.markersVisible(), 0, 0, state.modes(), null);
		}
	}

	public void activate() {
		if (active) {
			return;
		}

		active = true;
		restoreNamedState();
		access().lobecorp$pruneSelection(animationTimeline());
		rebuild();
	}

	public void suspend() {
		if (!active) {
			return;
		}

		prepareSwitch();
		active = false;
	}

	/// 仅在离开当前编辑上下文前提交投影；普通载入和撤销恢复不能回写旧投影。
	public void prepareSwitch() {
		if (!active) {
			return;
		}

		view().setRecordingTrack(null);
		access().lobecorp$cancelClipDrag();
		workspace.commitTimelineChanges();
		saveNamedState();
	}

	public void restoreSceneState() {
		restoreSceneState(false);
	}

	public void restoreSceneState(boolean immediate) {
		if (sceneState == null) {
			return;
		}

		access().lobecorp$restoreViewState(sceneState);
		sceneState = null;
		view().rebuild();
		var scene = editor().sceneView;
		scene.reset();
		scene.particleManager.setTime(sceneTime);
		scene.particleManager.pause();
		restoringScene = true;

		if (immediate) {
			scene.simulateTo(sceneTime);
			finishSceneRestore();
			return;
		}

		scene.requestSimulateTo(sceneTime);
	}

	public boolean replayingScene() {
		return restoringScene;
	}

	public void finishSceneRestore() {
		if (!restoringScene || workspace.preview().editing()) {
			return;
		}

		restoringScene = false;

		if (scenePlaying) {
			editor().sceneView.particleManager.play();
		} else {
			editor().sceneView.particleManager.pause();
		}
	}

	private void saveNamedState() {
		if (loadedAnimation != null) {
			viewStates.put(loadedAnimation, access().lobecorp$captureViewState());
			clocks.put(loadedAnimation, time);
		}
	}

	private void restoreNamedState() {
		var state = viewStates.get(loadedAnimation);

		if (state != null) {
			access().lobecorp$restoreViewState(state);
		} else if (emptyState != null) {
			access().lobecorp$restoreViewState(emptyState);
		}

		time = clocks.getOrDefault(loadedAnimation, 0D);
	}

	public void loadSelected() {
		var currentId = workspace.editingDefinition().id;
		var changed = !Objects.equals(loadedAnimation, currentId);

		if (active) {
			view().setRecordingTrack(null);
			access().lobecorp$cancelClipDrag();

			if (changed) {
				saveNamedState();
			}
		}

		model.load(workspace.editingDefinition());
		loadedAnimation = currentId;

		if (!active) {
			return;
		}

		if (changed) {
			restoreNamedState();
		}

		access().lobecorp$pruneSelection(animationTimeline());
		rebuild();
		sanitizeEditorState();
	}

	public void storeModel() {
		model.store(workspace.editingDefinition(), workspace::replaceFormIdentity);
	}

	public double time() {
		return time;
	}

	public void followTime(double ticks) {
		if (Double.isFinite(ticks)) {
			time = Math.max(0, ticks);
		}
	}

	public void seek(double ticks) {
		if (!Double.isFinite(ticks)) {
			return;
		}

		var playing = editor().sceneView.particleManager.isPlaying();
		var timeline = workspace.timelinePlaybackActive();
		workspace.preserveTestPlayback();
		editor().sceneView.particleManager.pause();
		followTime(ticks);
		workspace.scrub(time);

		if (playing) {
			workspace.continuePreviewAfterSimulation(timeline);
		}

		view().refreshLaneLayout();
	}

	public long currentTimeTicks() {
		return Math.max(0, Math.round(time));
	}

	public void pushEdit(String name, Runnable doFn) {
		view().setRecordingTrack(null);
		workspace.edit(name, () -> {
			doFn.run();
			storeModel();
		});
	}

	public void pushApplied(String name, Runnable redo, Runnable undo) {
		storeModel();
		workspace.applied(name, () -> {
			redo.run();
			storeModel();
		}, () -> {
			undo.run();
			storeModel();
		});
	}

	public void refreshPreview() {
		if (workspace.preview().editing()) {
			workspace.refreshTimelinePreview();
		}
	}

	public void beginScrub() {
		scrubWasPlaying = editor().sceneView.particleManager.isPlaying();
		scrubTimelineWasPlaying = workspace.timelinePlaybackActive();
		workspace.preserveTestPlayback();
		editor().sceneView.particleManager.pause();
	}

	public void endScrub() {
		refreshPreview();

		if (scrubWasPlaying) {
			scrubWasPlaying = false;
			workspace.continuePreviewAfterSimulation(scrubTimelineWasPlaying);
		}
	}

	public void restoreEditorState() {
		access().lobecorp$pruneSelection(animationTimeline());
		access().lobecorp$cancelClipDrag();
		sanitizeEditorState();
		var tracks = animationTimeline().leafTracks(true);

		for (var entry : access().lobecorp$states().entrySet()) {
			if (!tracks.contains(entry.getKey())
					|| !(entry.getValue() instanceof AnimationTrackUIStateAccessor state)) {
				continue;
			}

			state.lobecorp$setDragProperty(null);
			state.lobecorp$setDragSnapshot(null);
			state.lobecorp$setKeyGroupDrag(false);
			state.lobecorp$getKeyDragOrigins().clear();
		}
	}

	private void sanitizeEditorState() {
		var tracks = animationTimeline().leafTracks(true);

		for (var entry : access().lobecorp$states().entrySet()) {
			if (!tracks.contains(entry.getKey()) || !(entry.getKey() instanceof AnimationTrack track)
					|| !(entry.getValue() instanceof AnimationTrackUIStateAccessor state)) {
				continue;
			}

			state.lobecorp$getExpandedProperties().retainAll(track.properties());

			if (state.lobecorp$getSelectedProperty() != null
					&& !track.properties().contains(state.lobecorp$getSelectedProperty())) {
				state.lobecorp$setSelectedProperty(track.properties().isEmpty() ? null : track.properties().getFirst());
				state.lobecorp$getSelectedKeys().clear();
				state.lobecorp$setPrimaryAxis(-1);
				state.lobecorp$setPrimaryIndex(-1);
			}

			prunePropertySelection(state);
		}
	}

	private static void prunePropertySelection(AnimationTrackUIStateAccessor state) {
		var property = state.lobecorp$getSelectedProperty();
		var keys = state.lobecorp$getSelectedKeys();

		if (keys.removeIf(key -> !validKey(property, key)) || keys.isEmpty()) {
			var primary = keys.size() == 1 ? keys.iterator().next() : null;
			state.lobecorp$setPrimaryAxis(primary == null ? -1 : (int) (primary >>> Integer.SIZE));
			state.lobecorp$setPrimaryIndex(primary == null ? -1 : primary.intValue());
		}

		Set<ExprClip> expressions = new HashSet<>();
		Set<CurveClip> curves = new HashSet<>();

		if (property != null) {
			for (int axis = 0; axis < property.channelCount(); axis++) {
				expressions.addAll(property.exprClips(axis));

				if (property instanceof ConfigAnimatedProperty config) {
					curves.addAll(config.curveClips(axis));
				}
			}
		}

		state.lobecorp$getExprClips().retainAll(expressions);
		state.lobecorp$getCurveClips().retainAll(curves);

		if (property instanceof ColorAnimatedProperty color) {
			state.lobecorp$getGradientClips().retainAll(color.gradientClips());

			if (!color.stops().contains(state.lobecorp$getSelectedStop())) {
				state.lobecorp$setSelectedStop(null);
			}
		} else {
			state.lobecorp$getGradientClips().clear();
			state.lobecorp$setSelectedStop(null);
		}
	}

	private static boolean validKey(@Nullable AnimatedProperty property, long key) {
		if (property == null) {
			return false;
		}

		var axis = (int) (key >>> Integer.SIZE);
		var index = (int) key;
		return axis >= 0 && axis < property.channelCount() && index >= 0 && index < property.keyCount(axis);
	}

	public void togglePlayback() {
		view().setRecordingTrack(null);
		workspace.clearQueuedPlaybackCommand();

		if (workspace.timelinePlaybackActive() && editor().sceneView.particleManager.isPlaying()) {
			editor().sceneView.particleManager.pause();
			workspace.syncPreviewDispatch(false);
			return;
		}

		workspace.playFromCursor();
	}

	public void stopPlayback() {
		view().setRecordingTrack(null);
		workspace.clearQueuedPlaybackCommand();
		editor().sceneView.particleManager.pause();
		workspace.stopAtCursor();
		seek(0);
	}

	public void rebuild() {
		view().rebuild();
	}

	public FXEditor editor() {
		return workspace.owner();
	}

	public void setRecordingTrack(@Nullable Track track) {
		view().setRecordingTrack(track);
	}
}
