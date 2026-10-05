package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import com.lowdragmc.photon.client.fx.timeline.Track;
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

@Mixin(FXTimelineView.class)
public abstract class FXTimelineViewMixin implements PhotonTimelinePanAccess, PhotonTimelineEditAccess {
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
			} catch (RuntimeException ignored) {
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
			} catch (RuntimeException ignored) {
				PhotonTimelineClipboardUtil.invalid((TimelineContext) this);
			}
		}
	}

	@Shadow
	private Track selectedTrack;

	@Inject(method = "pasteClipboard", at = @At("HEAD"), cancellable = true)
	private void lobecorp$pasteSystem(CallbackInfo ci) {
		if (!PhotonKeyClipboardUtil.systemEnabled(fxEditor))
			return;
		var view = (FXTimelineView) (Object) this;
		String kind = PhotonSystemClipboardUtil.kind();
		if (PhotonSystemClipboardUtil.KEYS.equals(kind) || PhotonSubClipboardUtil.KIND.equals(kind)) {
			clipboardKind = 3;
			return;
		}
		ci.cancel();
		if (PhotonSystemClipboardUtil.CLIPS.equals(kind) || PhotonSystemClipboardUtil.TRACKS.equals(kind)) {
			int index = fxEditor.runtime == null ? 0 : fxEditor.runtime.fxData.timeline().tracks().size();
			PhotonTimelineClipboardUtil.paste(view, selectedTrack, index, PhotonSystemClipboardUtil.CLIPS.equals(kind));
		} else if (PhotonSystemClipboardUtil.hasContent())
			PhotonTimelineClipboardUtil.invalid(view);
	}

	@Inject(method = "pasteTracks", at = @At("HEAD"), cancellable = true)
	private void lobecorp$pasteSystemTracks(int index, CallbackInfo ci) {
		if (!PhotonKeyClipboardUtil.systemEnabled(fxEditor))
			return;
		ci.cancel();
		PhotonTimelineClipboardUtil.paste((FXTimelineView) (Object) this, selectedTrack, index, false);
	}

	@WrapOperation(method = {"openAddTrackMenu", "openTrackMenu"}, at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
	private boolean lobecorp$systemPasteMenu(List<?> instance, Operation<Boolean> original) {
		if (instance == clipboardTracks && PhotonKeyClipboardUtil.systemEnabled(fxEditor))
			return !PhotonSystemClipboardUtil.TRACKS.equals(PhotonSystemClipboardUtil.kind());
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

	@Inject(method = "updatePlaybackEnd", at = @At("HEAD"), cancellable = true)
	private void lobecorp$localPreviewEnd(CallbackInfo ci) {
		if (PhotonEditorTools.handlesPreviewEnd((FXTimelineView) (Object) this))
			ci.cancel();
	}

	@SuppressWarnings("ConstantValue")
	@Override
	public void lobecorp$fitTime(double start, double end) {
		if (!Double.isFinite(start) || !Double.isFinite(end) || viewWidth() <= 1)
			return;
		double span = Math.max(1, end - start);
		scale = Math.clamp((float) (viewWidth() / (span + FXTimelineView.CONTENT_PAD)),
				FXTimelineView.MIN_SCALE, FXTimelineView.MAX_SCALE);
		scrollTicks = (float) Math.max(0, start - FXTimelineView.CONTENT_PAD / 2f);
		updateHScroller();
		repositionLaneItems();
	}

	@Override
	public void lobecorp$panTimeline(float pixels) {
		if (scale <= 0 || !Float.isFinite(pixels))
			return;
		scrollTicks = Math.max(0, scrollTicks - pixels / scale);
		updateHScroller();
		repositionLaneItems();
	}
}
