package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.property.ColorAnimatedProperty;
import com.lowdragmc.photon.gui.editor.view.timeline.AnimationTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.AnimationTrackEditor.AnimationTrackUIState;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackUIState;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonTimelinePanAccess;
import org.unitego.lobecorp.client.photon.editor.PhotonCurveViewAccess;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.util.photon.editor.PhotonCurveEditUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonKeyClipboardUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonSystemClipboardUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonTimelineClipboardUtil;
import org.unitego.lobecorp.util.photon.clipboard.PhotonSubClipboardUtil;
import com.lowdragmc.photon.gui.editor.FXEditor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

@Mixin(AnimationTrackEditor.class)
@SuppressWarnings("FieldMayBeFinal")
public abstract class AnimationTrackEditorMixin implements PhotonCurveViewAccess {
	@Unique
	private FXEditor lobecorp$clipboardEditor;

	@WrapMethod(method = "canPasteInto")
	private boolean lobecorp$canPasteKeys(AnimatedProperty property, Operation<Boolean> original) {
		if (lobecorp$clipboardEditor != null && PhotonKeyClipboardUtil.systemEnabled(lobecorp$clipboardEditor)
				&& PhotonSubClipboardUtil.KIND.equals(PhotonSystemClipboardUtil.kind()))
			return PhotonSubClipboardUtil.compatible(property);
		if (lobecorp$clipboardEditor != null && (PhotonKeyClipboardUtil.systemEnabled(lobecorp$clipboardEditor)
				|| PhotonKeyClipboardUtil.has(lobecorp$clipboardEditor)))
			return PhotonKeyClipboardUtil.compatible(lobecorp$clipboardEditor, property);
		return original.call(property);
	}

	@WrapMethod(method = "copySubSelection")
	@SuppressWarnings("ConstantValue")
	private boolean lobecorp$copyKeys(TimelineContext ctx, Track track, TrackUIState state, Operation<Boolean> original) {
		lobecorp$clipboardEditor = ctx.editor();
		try {
			if (PhotonKeyClipboardUtil.copy(ctx, (AnimationTrackUIState) state)) {
				FXTimelineViewAccessor.lobecorp$setClipboardKind(3);
				return true;
			}
			if (PhotonKeyClipboardUtil.systemEnabled(ctx.editor())) {
				boolean copied = PhotonSubClipboardUtil.copy((AnimationTrackUIState) state);
				if (copied) {
					PhotonKeyClipboardUtil.clear(ctx.editor());
					FXTimelineViewAccessor.lobecorp$setClipboardKind(3);
				}
				return copied;
			}
		} catch (RuntimeException ignored) {
			PhotonTimelineClipboardUtil.invalid(ctx);
			return false;
		}
		boolean copied = original.call(ctx, track, state);
		if (copied) {
			PhotonKeyClipboardUtil.clear(ctx.editor());
			FXTimelineViewAccessor.lobecorp$setClipboardKind(3);
		}
		return copied;
	}

	@WrapMethod(method = "pasteSubSelectionAt")
	private boolean lobecorp$pasteKeys(TimelineContext ctx, Track track, TrackUIState state, double destTick, Operation<Boolean> original) {
		if (PhotonKeyClipboardUtil.systemEnabled(ctx.editor())) {
			boolean pasted = PhotonSubClipboardUtil.KIND.equals(PhotonSystemClipboardUtil.kind())
					? PhotonSubClipboardUtil.paste(ctx, track, (AnimationTrackUIState) state, destTick)
					: PhotonKeyClipboardUtil.paste(ctx, track, (AnimationTrackUIState) state, destTick);
			if (!pasted && PhotonSystemClipboardUtil.hasContent())
				PhotonTimelineClipboardUtil.invalid(ctx);
			return pasted;
		}
		if (PhotonKeyClipboardUtil.has(ctx.editor()))
			return PhotonKeyClipboardUtil.paste(ctx, track, (AnimationTrackUIState) state, destTick);
		return original.call(ctx, track, state, destTick);
	}

	/**
	 * GUI 像素：超过此距离后区分点击与拖动，并确定 Shift 锁定轴。
	 */
	@Unique
	private static final float DRAG_AXIS_THRESHOLD = 3f;
	@Unique
	private Map<AnimatedProperty, Float> lobecorp$viewOffsets = new WeakHashMap<>();
	@Unique
	private Map<AnimationTrackUIState, CurveGesture> lobecorp$gestures = new WeakHashMap<>();
	@Unique
	private boolean lobecorp$lockTick;

	@Shadow
	private float[] effectiveRange(AnimatedProperty property) {
		throw new AssertionError();
	}

	@Shadow
	private void beginCurveDrag(TimelineContext ctx, AnimationTrackUIState st, AnimatedProperty property,
	                            int axis, int key, int handle) {
		throw new AssertionError();
	}

	private static class CurveGesture {
		private Vector2f origin;
		private int axis;
		private boolean moved;
		private Set<Long> clickSelection;
		private boolean copyOnEnd;
		private Set<Long> copyKeys;
	}

	@Override
	public void lobecorp$resetView(AnimatedProperty property) {
		lobecorp$viewOffsets.remove(property);
	}

	@Override
	public boolean lobecorp$cancelCurve(TimelineContext ctx, AnimationTrackUIState st) {
		AnimationTrackUIStateAccessor access = (AnimationTrackUIStateAccessor) st;
		var property = access.lobecorp$getDragProperty();
		var snapshot = access.lobecorp$getDragSnapshot();
		if (property == null || snapshot == null)
			return false;
		property.restoreChannels(snapshot);
		lobecorp$gestures.remove(st);
		access.lobecorp$setDragProperty(null);
		access.lobecorp$setDragSnapshot(null);
		access.lobecorp$setKeyGroupDrag(false);
		access.lobecorp$getKeyDragOrigins().clear();
		ctx.endScrub();
		ctx.refreshLaneLayout();
		ctx.refreshPreview();
		return true;
	}

	@ModifyReturnValue(method = "effectiveRange", at = @At("RETURN"))
	private float[] lobecorp$panRange(float[] range, AnimatedProperty property) {
		float offset = lobecorp$viewOffsets.getOrDefault(property, 0f);
		return new float[]{range[0] + offset, range[1] + offset};
	}

	@ModifyReturnValue(method = "buildExpandedRight", at = @At("RETURN"))
	private UIElement lobecorp$enablePan(UIElement container, TimelineContext ctx, Track track, TrackUIState state) {
		lobecorp$clipboardEditor = ctx.editor();
		AnimationTrackUIState st = (AnimationTrackUIState) state;
		boolean[] panning = {false};
		Vector2f last = new Vector2f();
		container.addEventListener(UIEvents.MOUSE_DOWN, event -> {
			if (!PhotonEditorTools.enabled(container, "curve_pan"))
				return;
			AnimatedProperty property = ((AnimationTrackUIStateAccessor) st).lobecorp$getSelectedProperty();
			if (event.button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || property == null || property instanceof ColorAnimatedProperty)
				return;
			panning[0] = true;
			last.set(event.x, event.y);
			container.startDrag(null, null);
			event.stopImmediatePropagation();
		}, true);
		container.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
			if (!panning[0])
				return;
			AnimatedProperty property = ((AnimationTrackUIStateAccessor) st).lobecorp$getSelectedProperty();
			if (property != null && container.getContentHeight() > 0) {
				float[] range = effectiveRange(property);
				float offset = lobecorp$viewOffsets.getOrDefault(property, 0f)
						+ (event.y - last.y) * (range[1] - range[0]) / container.getContentHeight();
				if (Float.isFinite(offset))
					lobecorp$viewOffsets.put(property, offset);
			}
			if (ctx instanceof PhotonTimelinePanAccess pan)
				pan.lobecorp$panTimeline(event.x - last.x);
			last.set(event.x, event.y);
			ctx.refreshLaneLayout();
			event.stopImmediatePropagation();
		}, true);
		container.addEventListener(UIEvents.DRAG_END, event -> {
			if (!panning[0])
				return;
			panning[0] = false;
			event.stopImmediatePropagation();
		}, true);
		return container;
	}

	@Inject(method = "beginCurveDrag", at = @At("RETURN"))
	private void lobecorp$rememberOrigin(TimelineContext ctx, AnimationTrackUIState st, AnimatedProperty property,
	                                     int axis, int key, int handle, CallbackInfo ci) {
		Vector2f origin = handle == 1 ? property.inHandle(axis, key)
				: handle == 2 ? property.outHandle(axis, key) : property.key(axis, key);
		if (origin == null)
			return;
		CurveGesture gesture = new CurveGesture();
		gesture.origin = new Vector2f(origin);
		lobecorp$gestures.put(st, gesture);
	}

	@WrapMethod(method = "onKeyframeMouseDown")
	private void lobecorp$startShiftDrag(TimelineContext ctx, UIEvent e, AnimationTrack track, AnimatedProperty property,
	                                     AnimationTrackUIState st, int axis, int index, UIElement el, Operation<Void> original) {
		boolean shift = PhotonEditorTools.enabled(ctx.editor(), "axis_lock")
				&& e.button == GLFW.GLFW_MOUSE_BUTTON_LEFT && e.isShiftDown() && !e.isCtrlDown() && !track.lock();
		AnimationTrackUIStateAccessor access = (AnimationTrackUIStateAccessor) st;
		Set<Long> before = shift ? new HashSet<>(access.lobecorp$getSelectedKeys()) : null;
		original.call(ctx, e, track, property, st, axis, index, el);
		if (e.button == GLFW.GLFW_MOUSE_BUTTON_LEFT && e.isAltDown() && !e.isCtrlDown()
				&& !e.isShiftDown() && !track.lock() && PhotonEditorTools.enabled(ctx.editor(), "precise")) {
			var gesture = lobecorp$gestures.get(st);
			if (gesture != null) {
				gesture.copyOnEnd = true;
				gesture.copyKeys = new HashSet<>(access.lobecorp$getSelectedKeys());
			}
		}
		if (!shift)
			return;
		Set<Long> clicked = new HashSet<>(access.lobecorp$getSelectedKeys());
		access.lobecorp$getSelectedKeys().clear();
		access.lobecorp$getSelectedKeys().addAll(before);
		access.lobecorp$getSelectedKeys().add((long) axis << 32 | index & 0xffffffffL);
		beginCurveDrag(ctx, st, property, axis, index, 0);
		CurveGesture gesture = lobecorp$gestures.get(st);
		if (gesture != null)
			gesture.clickSelection = clicked;
		el.startDrag(null, null);
		ctx.refreshLaneLayout();
	}

	@WrapMethod(method = "onCurveDrag")
	private void lobecorp$lockCurveAxis(TimelineContext ctx, UIEvent e, AnimationTrackUIState st, Operation<Void> original) {
		if (!PhotonEditorTools.enabled(ctx.editor(), "axis_lock")) {
			original.call(ctx, e, st);
			return;
		}
		CurveGesture gesture = lobecorp$gestures.get(st);
		if (gesture == null) {
			original.call(ctx, e, st);
			return;
		}
		float dx = e.x - e.dragStartX;
		float dy = e.y - e.dragStartY;
		if (!gesture.moved && Math.max(Math.abs(dx), Math.abs(dy)) < DRAG_AXIS_THRESHOLD) {
			e.stopPropagation();
			return;
		}
		gesture.moved = true;
		if (!e.isShiftDown()) {
			gesture.axis = 0;
			original.call(ctx, e, st);
			return;
		}
		if (gesture.axis == 0)
			gesture.axis = Math.abs(dx) >= Math.abs(dy) ? 1 : 2;
		UIElement box = e.currentElement.getParent();
		AnimatedProperty property = ((AnimationTrackUIStateAccessor) st).lobecorp$getSelectedProperty();
		if (property == null || box == null || box.getContentHeight() <= 0)
			return;
		float x = e.x, y = e.y;
		boolean oldLock = lobecorp$lockTick;
		try {
			if (gesture.axis == 1) {
				float[] range = effectiveRange(property);
				e.y = box.getContentY() + box.getContentHeight() * (1 - (gesture.origin.y - range[0]) / (range[1] - range[0]));
			} else {
				e.x = box.getContentX() + (gesture.origin.x - ctx.scrollTicks()) * ctx.scale();
				lobecorp$lockTick = true;
			}
			original.call(ctx, e, st);
		} finally {
			e.x = x;
			e.y = y;
			lobecorp$lockTick = oldLock;
		}
	}

	@WrapOperation(method = {"onCurveDrag", "groupMoveKeys"}, at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/gui/editor/view/timeline/TimelineContext;snapKeyTick(DZ)D"))
	private double lobecorp$keepLockedTime(TimelineContext ctx, double tick, boolean ctrl, Operation<Double> original) {
		return lobecorp$lockTick ? tick : original.call(ctx, tick, ctrl);
	}

	@WrapMethod(method = "onCurveDragEnd")
	private void lobecorp$finishShiftDrag(TimelineContext ctx, AnimationTrackUIState st, Operation<Void> original) {
		CurveGesture gesture = lobecorp$gestures.remove(st);
		if (gesture != null && gesture.copyOnEnd) {
			var access = (AnimationTrackUIStateAccessor) st;
			var property = access.lobecorp$getDragProperty();
			if (property != null && access.lobecorp$getDragSnapshot() != null) {
				try {
					var copied = PhotonCurveEditUtil.finishDragCopy(property, access.lobecorp$getDragSnapshot(), gesture.copyKeys);
					if (copied.isEmpty()) {
						lobecorp$cancelCurve(ctx, st);
						return;
					}
					access.lobecorp$getSelectedKeys().clear();
					access.lobecorp$getSelectedKeys().addAll(copied);
					access.lobecorp$setPrimaryAxis(-1);
					access.lobecorp$setPrimaryIndex(-1);
				} catch (IllegalArgumentException error) {
					lobecorp$cancelCurve(ctx, st);
					return;
				}
				original.call(ctx, st);
				ctx.requestRebuild();
				return;
			}
		}
		if (gesture == null || gesture.moved || gesture.clickSelection == null) {
			original.call(ctx, st);
			return;
		}
		AnimationTrackUIStateAccessor access = (AnimationTrackUIStateAccessor) st;
		access.lobecorp$getSelectedKeys().clear();
		access.lobecorp$getSelectedKeys().addAll(gesture.clickSelection);
		access.lobecorp$setDragProperty(null);
		access.lobecorp$setDragSnapshot(null);
		access.lobecorp$setKeyGroupDrag(false);
		access.lobecorp$getKeyDragOrigins().clear();
		ctx.endScrub();
		ctx.refreshLaneLayout();
	}

	@Inject(method = "onCurveDrag", at = @At("RETURN"))
	private void lobecorp$alignHandles(TimelineContext ctx, UIEvent e, AnimationTrackUIState st, CallbackInfo ci) {
		PhotonEditorTools.syncTangent(ctx, st);
	}

	@WrapMethod(method = "onCurveWheel")
	private void lobecorp$zoomBaseRange(TimelineContext ctx, UIEvent e, AnimationTrackUIState st, Operation<Void> original) {
		AnimatedProperty property = ((AnimationTrackUIStateAccessor) st).lobecorp$getSelectedProperty();
		Float offset = lobecorp$viewOffsets.remove(property);
		try {
			original.call(ctx, e, st);
		} finally {
			if (offset != null)
				lobecorp$viewOffsets.put(property, offset);
			ctx.refreshLaneLayout();
		}
	}
}
