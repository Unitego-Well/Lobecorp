package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import org.unitego.lobecorp.client.photon.model.editor.PhotonModelTimelineView;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Marker;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackUIState;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import org.unitego.lobecorp.client.photon.editor.PhotonTimelineEditAccess;

import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.unitego.lobecorp.client.photon.editor.PhotonTimelinePanAccess;
import org.unitego.lobecorp.util.photon.clipboard.PhotonKeyClipboardUtil;
import com.lowdragmc.photon.gui.editor.FXEditor;

import java.util.List;
import java.util.Collection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.unitego.lobecorp.util.photon.clipboard.PhotonSystemClipboardUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonTimelineClipboardUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonSubClipboardUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTimelineUtil;
import org.unitego.lobecorp.client.photon.editor.PhotonTimelineViewState;
import org.unitego.lobecorp.util.photon.editor.PhotonTimelineStateUtil;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;

import java.util.HashMap;

@Mixin(FXTimelineView.class)
public abstract class FXTimelineViewMixin implements PhotonTimelinePanAccess, PhotonTimelineEditAccess {
	/// 模型时间轴沿用原生组件，将传输控制交给模型自己的会话。
	@Inject(method = "togglePlay", at = @At("HEAD"), cancellable = true)
	private void lobecorp$modelPlay(CallbackInfo ci) {
		if ((Object) this instanceof PhotonModelTimelineView model) {
			model.toggleModelPlayback();
			ci.cancel();
		}
	}

	@Inject(method = "stop", at = @At("HEAD"), cancellable = true)
	private void lobecorp$modelStop(CallbackInfo ci) {
		if ((Object) this instanceof PhotonModelTimelineView model) {
			model.stopModelPlayback();
			ci.cancel();
		}
	}

	@Inject(method = "jumpToEnd", at = @At("HEAD"), cancellable = true)
	private void lobecorp$modelJumpToEnd(CallbackInfo ci) {
		if ((Object) this instanceof PhotonModelTimelineView model) {
			model.jumpToModelEnd();
			ci.cancel();
		}
	}

	@Inject(method = "contentMaxTick", at = @At("HEAD"), cancellable = true)
	private void lobecorp$modelExtent(CallbackInfoReturnable<Double> cir) {
		if ((Object) this instanceof PhotonModelTimelineView model) {
			cir.setReturnValue(model.modelDuration());
		}
	}

	@Inject(method = "openAddTrackMenu", at = @At("HEAD"), cancellable = true)
	private void lobecorp$modelTracks(float x, float y, CallbackInfo ci) {
		if ((Object) this instanceof PhotonModelTimelineView model) {
			model.openModelTrackMenu(x, y);
			ci.cancel();
		}
	}

	@Inject(method = "updatePlaybackEnd", at = @At("HEAD"), cancellable = true)
	private void lobecorp$modelClock(CallbackInfo ci) {
		if ((Object) this instanceof PhotonModelTimelineView) {
			ci.cancel();
		}
	}

	@Shadow
	@Final
	public FXEditor fxEditor;
	@Shadow
	private static int clipboardKind;
	@Shadow
	@Final
	private static List<?> clipboardClips;
	@Shadow
	@Final
	private static List<Track> clipboardTracks;

	@Inject(method = "copySelection", at = @At("RETURN"))
	private void lobecorp$isolateClipboard(CallbackInfo ci) {
		if (clipboardKind == 1) {
			clipboardTracks.clear();
			PhotonKeyClipboardUtil.clear(fxEditor);
		} else if (clipboardKind == 2) {
			clipboardClips.clear();
			PhotonKeyClipboardUtil.clear(fxEditor);
		}
		if (clipboardKind == 1 && PhotonKeyClipboardUtil.systemEnabled(fxEditor)) {
			try {
				PhotonTimelineClipboardUtil.copyClips((TimelineContext) this);
			} catch (RuntimeException _) {
				PhotonTimelineClipboardUtil.invalid((TimelineContext) this);
			}
		}
	}

	@Inject(method = "copyTracksToClipboard", at = @At("RETURN"))
	private void lobecorp$copyTracks(Collection<Track> tracks, CallbackInfo ci) {
		clipboardClips.clear();
		PhotonKeyClipboardUtil.clear(fxEditor);
		if (PhotonKeyClipboardUtil.systemEnabled(fxEditor)) {
			try {
				PhotonTimelineClipboardUtil.copyTracks((TimelineContext) this, tracks);
			} catch (RuntimeException _) {
				PhotonTimelineClipboardUtil.invalid((TimelineContext) this);
			}
		}
	}

	@Shadow
	private Track selectedTrack;
	@Shadow
	private Clip selectedClip;
	@Shadow
	private Track selectedClipTrack;
	@Shadow
	@Final
	private LinkedHashSet<Clip> selectedClips;
	@Shadow
	private Marker selectedMarker;
	@Shadow
	private Marker dragMarker;

	@Inject(method = "rebuild", at = @At("HEAD"))
	private void lobecorp$pruneAnimationState(CallbackInfo ci) {
		var workspace = PhotonAnimationTimelineUtil.workspace((TimelineContext) this);
		if (workspace == null) {
			return;
		}

		// 原生按轨道引用缓存状态；命名动画切换和撤销仍会恢复这些轨道。
		// 缓存仅跟随当前视图生命周期，不能在每次重建时删除。
		var model = workspace.timelineContext().animationTimeline();
		selectedTracks.removeIf(track -> model.parentListOf(track) == null);
		if (model.parentListOf(selectedTrack) == null) {
			selectedTrack = null;
		}
	}

	@Inject(method = "pasteClipboard", at = @At("HEAD"), cancellable = true)
	private void lobecorp$pasteSystem(CallbackInfo ci) {
		if (!PhotonKeyClipboardUtil.systemEnabled(fxEditor)) {
			return;
		}

		var view = (FXTimelineView) (Object) this;
		String kind = PhotonSystemClipboardUtil.kind();
		if (PhotonSystemClipboardUtil.KEYS.equals(kind) || PhotonSubClipboardUtil.KIND.equals(kind)) {
			clipboardKind = 3;
			return;
		}

		ci.cancel();
		if (PhotonSystemClipboardUtil.CLIPS.equals(kind) || PhotonSystemClipboardUtil.TRACKS.equals(kind)) {
			int index = fxEditor.runtime == null ? 0 : PhotonAnimationTimelineUtil.of(view).tracks().size();
			PhotonTimelineClipboardUtil.paste(view, selectedTrack, index, PhotonSystemClipboardUtil.CLIPS.equals(kind));
		} else if (PhotonSystemClipboardUtil.hasContent()) {
			PhotonTimelineClipboardUtil.invalid(view);
		}
	}

	@Inject(method = "pasteTracks", at = @At("HEAD"), cancellable = true)
	private void lobecorp$pasteSystemTracks(int index, CallbackInfo ci) {
		if (!PhotonKeyClipboardUtil.systemEnabled(fxEditor)) {
			return;
		}

		ci.cancel();
		PhotonTimelineClipboardUtil.paste((FXTimelineView) (Object) this, selectedTrack, index, false);
	}

	@WrapOperation(method = {"openAddTrackMenu", "openTrackMenu"}, at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
	private boolean lobecorp$systemPasteMenu(List<?> instance, Operation<Boolean> original) {
		if (instance == clipboardTracks && PhotonKeyClipboardUtil.systemEnabled(fxEditor)) {
			return !PhotonSystemClipboardUtil.TRACKS.equals(PhotonSystemClipboardUtil.kind());
		}

		return original.call(instance);
	}

	@Shadow
	private float scale;
	@Shadow
	private float scrollTicks;

	@Shadow
	private void updateHScroller() {
		throw new AssertionError();
	}

	@Shadow
	private void repositionLaneItems() {
		throw new AssertionError();
	}

	@Shadow
	@Final
	private Map<Track, TrackUIState> states;
	@Shadow
	@Final
	private LinkedHashSet<Track> selectedTracks;

	@Shadow
	private TrackEditor editorFor(Track track) {
		throw new AssertionError();
	}

	@Shadow
	private float viewWidth() {
		throw new AssertionError();
	}

	@Shadow
	private void copySelection() {
		throw new AssertionError();
	}

	@Shadow
	private void pasteClipboard() {
		throw new AssertionError();
	}

	@Shadow
	public abstract void endClipGroupDrag(boolean commit);

	@Override
	public Map<Track, TrackUIState> lobecorp$states() {
		return Map.copyOf(states);
	}

	@Override
	public Set<Track> lobecorp$selectedTracks() {
		return Set.copyOf(selectedTracks);
	}

	@Override
	public TrackEditor lobecorp$trackEditor(Track track) {
		return editorFor(track);
	}

	@Override
	public void lobecorp$duplicateNative() {
		copySelection();
		pasteClipboard();
	}

	@Override
	public void lobecorp$cancelClipDrag() {
		endClipGroupDrag(false);
	}

	@Override
	public void lobecorp$pruneSelection(Timeline timeline) {
		selectedTracks.removeIf(track -> timeline.parentListOf(track) == null);
		if (timeline.parentListOf(selectedTrack) == null) {
			selectedTrack = null;
		}

		var clips = new LinkedHashSet<Clip>();
		for (var track : timeline.leafTracks(true)) {
			clips.addAll(track.clips());
		}

		selectedClips.retainAll(clips);
		if (!clips.contains(selectedClip)) {
			selectedClip = null;
			selectedClipTrack = null;
		}

		if (!timeline.markers().contains(selectedMarker)) {
			selectedMarker = null;
		}

		if (!timeline.markers().contains(dragMarker)) {
			dragMarker = null;
		}
	}

	@Shadow
	private int rowHeight;
	@Shadow
	private boolean markersVisible;
	@Shadow
	@Final
	private ScrollerView leftScroller;
	@Shadow
	@Final
	private ScrollerView rightScroller;

	@Shadow
	private void setMarkersVisible(boolean visible) {
		throw new AssertionError();
	}

	@Override
	public Object lobecorp$captureViewState() {
		var view = (FXTimelineView) (Object) this;
		return new PhotonTimelineViewState(scale, scrollTicks, rowHeight,
				new HashMap<>(states), new LinkedHashSet<>(selectedTracks), selectedTrack,
				new LinkedHashSet<>(selectedClips), selectedClip, selectedClipTrack, selectedMarker,
				markersVisible, leftScroller.verticalScroller.getNormalizedValue(),
				rightScroller.verticalScroller.getNormalizedValue(), PhotonTimelineStateUtil.capture(view),
				PhotonEditorTools.capturePreviewRange(fxEditor));
	}

	@Override
	@SuppressWarnings("DeconstructionCanBeUsed") // 保持快照字段在下方按职责恢复。
	public void lobecorp$restoreViewState(Object snapshot) {
		if (!(snapshot instanceof PhotonTimelineViewState state)) {
			return;
		}

		var view = (FXTimelineView) (Object) this;
		scale = state.scale();
		scrollTicks = state.scroll();
		rowHeight = state.rowHeight();
		states.clear();
		states.putAll(state.states());
		selectedTracks.clear();
		selectedTracks.addAll(state.tracks());
		selectedTrack = state.track();
		selectedClips.clear();
		selectedClips.addAll(state.clips());
		selectedClip = state.clip();
		selectedClipTrack = state.clipTrack();
		selectedMarker = state.marker();
		dragMarker = null;
		setMarkersVisible(state.markersVisible());
		PhotonTimelineStateUtil.restore(view, state.modes());
		PhotonEditorTools.restorePreviewRange(fxEditor, state.previewRange());

		lobecorp$pruneSelection(PhotonAnimationTimelineUtil.of(view));
		view.rebuild();
		leftScroller.verticalScroller.setNormalizedValue(state.leftScroll());
		rightScroller.verticalScroller.setNormalizedValue(state.rightScroll());
		updateHScroller();
		repositionLaneItems();
	}

	@Inject(method = "updatePlaybackEnd", at = @At("HEAD"), cancellable = true)
	private void lobecorp$localPreviewEnd(CallbackInfo ci) {
		if (PhotonEditorTools.handlesPreviewEnd((FXTimelineView) (Object) this)) {
			ci.cancel();
		}
	}

	@SuppressWarnings("ConstantValue")
	@Override
	public void lobecorp$fitTime(double start, double end) {
		if (!Double.isFinite(start) || !Double.isFinite(end) || viewWidth() <= 1) {
			return;
		}

		double span = Math.max(1, end - start);
		scale = Math.clamp((float) (viewWidth() / (span + FXTimelineView.CONTENT_PAD)),
				FXTimelineView.MIN_SCALE, FXTimelineView.MAX_SCALE);
		scrollTicks = (float) Math.max(0, start - FXTimelineView.CONTENT_PAD / 2f);
		updateHScroller();
		repositionLaneItems();
	}

	@Override
	public void lobecorp$panTimeline(float pixels) {
		if (scale <= 0 || !Float.isFinite(pixels)) {
			return;
		}

		scrollTicks = Math.max(0, scrollTicks - pixels / scale);
		updateHScroller();
		repositionLaneItems();
	}
}
