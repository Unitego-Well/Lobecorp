package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.property.ColorAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.ExprClip;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import com.lowdragmc.photon.gui.editor.view.timeline.AnimationTrackEditor.AnimationTrackUIState;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import org.joml.Vector2f;
import org.unitego.lobecorp.client.photon.editor.PhotonTimelineEditAccess;
import org.unitego.lobecorp.client.photon.editor.PhotonCurveViewAccess;
import org.unitego.lobecorp.mixin.photon.editor.client.AnimationTrackUIStateAccessor;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTimelineUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.math.BigDecimal;

public class PhotonCurveEditUtil {
	/// 避免曲线时间键重叠的最小 tick 间隔，沿用 Photon 拖动规则。
	public static final float KEY_GAP = 0.001f;
	/// 适配视图时在数据上下预留的比例。
	private static final float VIEW_PADDING = 0.1f;
	/// 空数值曲线及恒定曲线的默认半幅，单位与属性值一致。
	private static final float DEFAULT_VIEW_EXTENT = 1f;
	/// 空角度曲线及恒定角度曲线的默认半幅，单位为度。
	private static final float DEFAULT_ROTATION_EXTENT = 360f;
	/// 初始化时每个表达式片段的采样间隔数，避免按片段 tick 长度分配工作量。
	private static final int EXPRESSION_RANGE_SAMPLES = 64;
	/// 颜色停止点没有贝塞尔通道，用此标记参与统一时间编辑。
	public static final int COLOR_AXIS = -1;

	public record Selection(AnimationTrack track, AnimatedProperty property, Set<Long> keys) {
	}

	public record Key(Selection selection, int axis, int index, Vector2f point, Vector2f in, Vector2f out) {
	}

	public static List<Selection> selections(FXTimelineView timeline) {
		var result = new ArrayList<Selection>();
		var access = (PhotonTimelineEditAccess) timeline;
		var model = PhotonAnimationTimelineUtil.of(timeline);
		access.lobecorp$states().forEach((track, state) -> {
			if (model.parentListOf(track) == null) {
				return;
			}

			if (track instanceof AnimationTrack animation && state instanceof AnimationTrackUIState st) {
				var ui = (AnimationTrackUIStateAccessor) st;
				var property = ui.lobecorp$getSelectedProperty();

				if (property != null && !animation.properties().contains(property)) {
					return;
				}

				if (property != null && !ui.lobecorp$getSelectedKeys().isEmpty()) {
					if (track.lock()) {
						throw new IllegalArgumentException();
					}
					result.add(new Selection(animation, property, Set.copyOf(ui.lobecorp$getSelectedKeys())));
				} else if (property instanceof ColorAnimatedProperty color && ui.lobecorp$getSelectedStop() != null) {
					if (track.lock()) {
						throw new IllegalArgumentException();
					}
					int index = color.stops().indexOf(ui.lobecorp$getSelectedStop());

					if (index >= 0) {
						result.add(new Selection(animation, property, Set.of(encode(COLOR_AXIS, index))));
					}
				}
			}
		});

		for (var track : access.lobecorp$selectedTracks()) {
			if (model.parentListOf(track) == null || !(track instanceof AnimationTrack animation) || !timeline.isTrackSelected(track)) {
				continue;
			}

			if (track.lock()) {
				throw new IllegalArgumentException();
			}

			for (var property : animation.properties()) {
				var keys = new HashSet<Long>();

				if (property instanceof ColorAnimatedProperty color) {
					for (int k = 0; k < color.stops().size(); k++) {
						keys.add(encode(COLOR_AXIS, k));
					}
				} else {

					for (int axis = 0; axis < property.channelCount(); axis++) {
						for (int k = 0; k < property.keyCount(axis); k++) {
							keys.add(encode(axis, k));
						}
					}
				}

				if (!keys.isEmpty() && result.stream().noneMatch(s -> s.property() == property)) {
					result.add(new Selection(animation, property, Set.copyOf(keys)));
				}
			}
		}

		return result;
	}

	private static long encode(int axis, int index) {
		return (long) axis << 32 | index & 0xffffffffL;
	}

	public static List<Key> keys(List<Selection> selections) {
		var result = new ArrayList<Key>();

		for (var selection : selections) {
			for (long id : selection.keys()) {
				int axis = (int) (id >>> 32), index = (int) id;

				if (axis == COLOR_AXIS && selection.property() instanceof ColorAnimatedProperty color) {
					if (index < 0 || index >= color.stops().size()) {
						throw new IllegalArgumentException();
					}
					result.add(new Key(selection, axis, index, new Vector2f(color.stops().get(index).tick, 0), null, null));
					continue;
				}

				if (axis < 0 || axis >= selection.property().channelCount() || index < 0
						|| index >= selection.property().keyCount(axis)) {
					throw new IllegalArgumentException();
				}
				var property = selection.property();
				result.add(new Key(selection, axis, index, property.key(axis, index),
						property.inHandle(axis, index), property.outHandle(axis, index)));
			}
		}

		return result;
	}

	public static void transform(FXTimelineView timeline, List<Selection> selections, float timeDelta, float valueDelta, float factor) {
		var keys = keys(selections);

		if (keys.isEmpty() || !Float.isFinite(timeDelta) || !Float.isFinite(valueDelta)
				|| !Float.isFinite(factor) || factor <= 0) {
			throw new IllegalArgumentException();
		}
		float pivot = (float) keys.stream().mapToDouble(k -> k.point().x).min().orElseThrow();
		var before = snapshot(selections);

		try {
			for (var key : keys) {
				float tick = pivot + (key.point().x - pivot) * factor + timeDelta;
				float value = key.selection().property().type().clampValue(key.point().y + valueDelta);

				if (tick < 0 || !Float.isFinite(tick) || !Float.isFinite(value)) {
					throw new IllegalArgumentException();
				}
				var property = key.selection().property();

				if (property instanceof ColorAnimatedProperty color) {
					if (valueDelta != 0) {
						throw new IllegalArgumentException();
					}
					color.stops().get(key.index()).tick = tick;
					continue;
				}

				property.moveKey(key.axis(), key.index(), tick, value);

				if (key.in() != null) {
					property.setInHandle(key.axis(), key.index(),
							tick + (key.in().x - key.point().x) * factor, value + key.in().y - key.point().y);
				}

				if (key.out() != null) {
					property.setOutHandle(key.axis(), key.index(),
							tick + (key.out().x - key.point().x) * factor, value + key.out().y - key.point().y);
				}
			}

			validate(selections);
		} catch (RuntimeException error) {
			restore(before);
			syncColorSelection(timeline, selections);
			throw error;
		}

		commit(timeline, selections, before, snapshot(selections));
	}

	public static void tangent(FXTimelineView timeline, List<Selection> selections, String preset) {
		var before = snapshot(selections);

		try {
			for (var key : keys(selections)) {
				var property = key.selection().property();

				if (key.axis() == COLOR_AXIS) {
					continue;
				}

				if (preset.equals("step")) {
					if (key.index() + 1 >= property.keyCount(key.axis())) {
						continue;
					}
					var end = property.key(key.axis(), key.index() + 1);

					for (var clip : property.exprClips(key.axis())) {
						if (clip.start() < end.x && clip.end() > key.point().x) {
							throw new IllegalArgumentException();
						}
					}

					var clip = new ExprClip(key.point().x, end.x - key.point().x,
							BigDecimal.valueOf(key.point().y).toPlainString());

					if (clip.compiled() == null) {
						throw new IllegalArgumentException();
					}
					property.addExprClip(key.axis(), clip);
					continue;
				}

				PhotonCurveEasingUtil.applyTangent(property, key.axis(), key.index(), preset);
			}

			commit(timeline, selections, before, snapshot(selections));
		} catch (RuntimeException error) {
			restore(before);
			throw error;
		}
	}

	private static Map<AnimatedProperty, AnimatedProperty> snapshot(List<Selection> selections) {
		var result = new LinkedHashMap<AnimatedProperty, AnimatedProperty>();

		for (var selection : selections) {
			if (selection.track().lock() || !selection.track().properties().contains(selection.property())) {
				throw new IllegalArgumentException();
			}
			result.putIfAbsent(selection.property(), selection.property().copy());
		}

		return result;
	}

	private static void validate(List<Selection> selections) {
		for (var selection : selections) {
			var property = selection.property();

			if (property instanceof ColorAnimatedProperty color) {
				for (int k = 0; k < color.stops().size(); k++) {
					float tick = color.stops().get(k).tick;

					if (!Float.isFinite(tick) || tick < 0
							|| (k > 0 && tick - color.stops().get(k - 1).tick < KEY_GAP)) {
						throw new IllegalArgumentException();
					}
				}
			}

			for (int axis = 0; axis < property.channelCount(); axis++) {
				for (int k = 0; k < property.keyCount(axis); k++) {
					var point = property.key(axis, k);

					if (!Float.isFinite(point.x) || !Float.isFinite(point.y) || point.x < 0
							|| (k > 0 && point.x - property.key(axis, k - 1).x < KEY_GAP)) {
						throw new IllegalArgumentException();
					}
				}
			}
		}
	}

	private static void restore(Map<AnimatedProperty, AnimatedProperty> state) {
		state.forEach(AnimatedProperty::restoreFrom);
	}

	private static void commit(FXTimelineView timeline, List<Selection> selections, Map<AnimatedProperty, AnimatedProperty> before,
	                           Map<AnimatedProperty, AnimatedProperty> after) {
		timeline.pushApplied(PhotonEditorTextUtil.key("feature.precise"),
				() -> {
					restore(after);
					syncColorSelection(timeline, selections);
					timeline.refreshLaneLayout();
					timeline.refreshPreview();
					noteRecordingEdit(timeline, selections);
				},
				() -> {
					restore(before);
					syncColorSelection(timeline, selections);
					timeline.refreshLaneLayout();
					timeline.refreshPreview();
					noteRecordingEdit(timeline, selections);
				});
		syncColorSelection(timeline, selections);
		timeline.refreshLaneLayout();
		timeline.refreshPreview();
		noteRecordingEdit(timeline, selections);
	}

	private static void noteRecordingEdit(FXTimelineView timeline, List<Selection> selections) {
		var access = (PhotonTimelineEditAccess) timeline;

		for (var selection : selections) {
			var state = access.lobecorp$states().get(selection.track());
			var trackEditor = access.lobecorp$trackEditor(selection.track());

			if (state instanceof AnimationTrackUIState animationState && trackEditor instanceof PhotonCurveViewAccess curve) {
				curve.lobecorp$noteCurveEdit(timeline, selection.track(), animationState);
			}
		}
	}

	private static void syncColorSelection(FXTimelineView timeline, List<Selection> selections) {
		var states = ((PhotonTimelineEditAccess) timeline).lobecorp$states();

		for (var selection : selections) {
			if (!(selection.property() instanceof ColorAnimatedProperty color) || selection.keys().size() != 1
					|| !(states.get(selection.track()) instanceof AnimationTrackUIState st)) {
				continue;
			}
			int index = (int) (long) selection.keys().iterator().next();
			var access = (AnimationTrackUIStateAccessor) st;

			if (access.lobecorp$getSelectedProperty() == color && index < color.stops().size()) {
				access.lobecorp$setSelectedStop(color.stops().get(index));
			}
		}
	}

	public static Set<Long> finishDragCopy(AnimatedProperty property, ECBCurves[] before, Set<Long> selection) {
		var originals = new ArrayList<Key>();

		for (long id : selection) {
			int axis = (int) (id >>> 32), index = (int) id;
			originals.add(new Key(null, axis, index, property.key(axis, index),
					property.inHandle(axis, index), property.outHandle(axis, index)));
		}

		property.restoreChannels(before);
		boolean changed = originals.stream().anyMatch(k -> !k.point().equals(property.key(k.axis(), k.index())));

		if (!changed) {
			return Set.of();
		}
		var handles = new ArrayList<Key>();

		for (int axis = 0; axis < property.channelCount(); axis++) {
			for (int k = 0; k < property.keyCount(axis); k++) {
				handles.add(new Key(null, axis, k, property.key(axis, k), property.inHandle(axis, k), property.outHandle(axis, k)));
			}
		}

		try {
			for (var key : originals) {
				for (int k = 0; k < property.keyCount(key.axis()); k++) {
					if (Math.abs(property.key(key.axis(), k).x - key.point().x) < KEY_GAP) {
						throw new IllegalArgumentException();
					}
				}
				int inserted = property.addKey(key.axis(), key.point().x, key.point().y);

				if (inserted < 0) {
					throw new IllegalArgumentException();
				}
			}

			handles.addAll(originals);

			for (var key : handles) {
				for (int k = 0; k < property.keyCount(key.axis()); k++) {
					if (!property.key(key.axis(), k).equals(key.point())) {
						continue;
					}

					if (key.in() != null) {
						property.setInHandle(key.axis(), k, key.in().x, key.in().y);
					}

					if (key.out() != null) {
						property.setOutHandle(key.axis(), k, key.out().x, key.out().y);
					}
				}
			}

			var result = new HashSet<Long>();

			for (var key : originals) {
				for (int k = 0; k < property.keyCount(key.axis()); k++) {
					if (property.key(key.axis(), k).equals(key.point())) {
						result.add(encode(key.axis(), k));
					}
				}
			}

			return Set.copyOf(result);
		} catch (RuntimeException error) {
			property.restoreChannels(before);
			throw error;
		}
	}

	public static void fit(AnimatedProperty property, Set<Long> selected) {
		var range = keyRange(property, selected);

		if (!Float.isFinite(range[0]) || !Float.isFinite(range[1])) {
			return;
		}

		float padding = Math.max(KEY_GAP, range[1] - range[0]) * VIEW_PADDING;
		applyRange(property, range, padding);
	}

	public static void initializeRange(AnimatedProperty property) {
		if (property instanceof ColorAnimatedProperty) {
			return;
		}

		float extent = property.type().angular() ? DEFAULT_ROTATION_EXTENT : DEFAULT_VIEW_EXTENT;
		var range = keyRange(property, Set.of());
		includeExpressionRange(property, range);

		if (!Float.isFinite(range[0]) || !Float.isFinite(range[1])) {
			applyRange(property, new float[]{-extent, extent}, 0);
			return;
		}

		double span = (double) range[1] - range[0];
		double padding = span < KEY_GAP ? extent : Math.max(KEY_GAP, span) * VIEW_PADDING;
		padding = Math.max(padding, Math.max(Math.ulp(range[0]), Math.ulp(range[1])));
		applyRange(property, range, padding);
	}

	private static float[] keyRange(AnimatedProperty property, Set<Long> selected) {
		var range = new float[]{Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};

		for (int axis = 0; axis < property.channelCount(); axis++) {
			for (int k = 0; k < property.keyCount(axis); k++) {
				long id = (long) axis << 32 | k & 0xffffffffL;

				if (!selected.isEmpty() && !selected.contains(id)) {
					continue;
				}

				includeValue(range, property.key(axis, k).y);

				for (var handle : new Vector2f[]{property.inHandle(axis, k), property.outHandle(axis, k)}) {
					if (handle != null) {
						includeValue(range, handle.y);
					}
				}
			}
		}

		return range;
	}

	private static void includeExpressionRange(AnimatedProperty property, float[] range) {
		for (int axis = 0; axis < property.channelCount(); axis++) {
			for (var clip : property.exprClips(axis)) {
				if (!Double.isFinite(clip.start()) || !Double.isFinite(clip.end())
						|| !Double.isFinite(clip.duration()) || clip.duration() <= 0 || clip.compiled() == null) {
					continue;
				}

				for (int sample = 0; sample <= EXPRESSION_RANGE_SAMPLES; sample++) {
					float tick = (float) (clip.start() + clip.duration() * ((double) sample / EXPRESSION_RANGE_SAMPLES));

					if (tick < clip.start()) {
						tick = Math.nextUp(tick);
					}

					if (tick >= clip.end()) {
						tick = Math.nextDown(tick);
					}

					if (!Float.isFinite(tick)) {
						continue;
					}

					Float value = property.evalExpr(axis, tick);

					if (value != null) {
						includeValue(range, value);
					}
				}
			}
		}
	}

	private static void includeValue(float[] range, float value) {
		if (!Float.isFinite(value)) {
			return;
		}

		range[0] = Math.min(range[0], value);
		range[1] = Math.max(range[1], value);
	}

	private static void applyRange(AnimatedProperty property, float[] range, double padding) {
		Float fixed = property.type().fixedRangeMin();
		double lower = fixed == null ? range[0] - padding : fixed;
		float min = (float) Math.max(-Float.MAX_VALUE, lower);
		float max = (float) Math.clamp(range[1] + padding, lower + KEY_GAP, Float.MAX_VALUE);

		if (max <= min) {
			if (fixed == null && min > -Float.MAX_VALUE) {
				min = Math.nextDown(min);
			}

			if (max < Float.MAX_VALUE) {
				max = Math.nextUp(max);
			}
		}

		property.setRange(min, max);
	}
}
