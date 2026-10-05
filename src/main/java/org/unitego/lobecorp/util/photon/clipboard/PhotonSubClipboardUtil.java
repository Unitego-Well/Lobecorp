package org.unitego.lobecorp.util.photon.clipboard;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.math.GradientColor;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.CurveClip;
import com.lowdragmc.photon.client.fx.timeline.ExprClip;
import com.lowdragmc.photon.client.fx.timeline.GradientClip;
import com.lowdragmc.photon.client.fx.timeline.SubClip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.property.ColorAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigAnimatedProperty;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.gui.editor.view.timeline.AnimationTrackEditor.AnimationTrackUIState;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import com.lowdragmc.photon.utils.ValueIONbt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.mixin.photon.editor.client.AnimationTrackUIStateAccessor;
import org.unitego.lobecorp.util.photon.editor.PhotonCurveEditUtil;

import java.util.ArrayList;
import java.util.List;

public class PhotonSubClipboardUtil {
	/**
	 * 系统剪切板中动画属性子片段的协议类型。
	 */
	public static final String KIND = "animation";

	private enum Selection {GRADIENT, CURVE, EXPR, STOP}

	public static boolean copy(AnimationTrackUIState state) {
		var access = (AnimationTrackUIStateAccessor) state;
		Selection kind;
		List<? extends SubClip> clips;
		if (!access.lobecorp$getGradientClips().isEmpty()) {
			kind = Selection.GRADIENT;
			clips = new ArrayList<>(access.lobecorp$getGradientClips());
		} else if (!access.lobecorp$getCurveClips().isEmpty()) {
			kind = Selection.CURVE;
			clips = new ArrayList<>(access.lobecorp$getCurveClips());
		} else if (!access.lobecorp$getExprClips().isEmpty()) {
			kind = Selection.EXPR;
			clips = new ArrayList<>(access.lobecorp$getExprClips());
		} else if (access.lobecorp$getSelectedStop() != null) {
			kind = Selection.STOP;
			clips = List.of();
		} else
			return false;
		var entries = new ListTag();
		double anchor = kind == Selection.STOP ? access.lobecorp$getSelectedStop().tick
				: clips.stream().mapToDouble(SubClip::start).min().orElseThrow();
		for (var clip : clips) {
			var entry = new CompoundTag();
			entry.putDouble("start", clip.start() - anchor);
			entry.putDouble("duration", clip.duration());
			switch (clip) {
				case GradientClip gradient -> {
					if (gradient.gradient() == null)
						throw new IllegalArgumentException();
					entry.put("gradient", ValueIONbt.toTag(gradient.gradient(), Platform.getFrozenRegistry()));
				}
				case CurveClip curve -> {
					if (curve.curve() == null)
						throw new IllegalArgumentException();
					entry.put("curve", curve.curve().serializeWrapper());
				}
				case ExprClip expression -> entry.putString("expr", expression.expression());
				default -> throw new IllegalArgumentException();
			}
			entries.add(entry);
		}
		if (kind == Selection.STOP) {
			var stop = access.lobecorp$getSelectedStop();
			var entry = new CompoundTag();
			entry.putInt("argb", stop.argb);
			entry.putFloat("intensity", stop.intensity);
			entries.add(entry);
		}
		var data = new CompoundTag();
		data.putString("selection", kind.name());
		data.put("entries", entries);
		PhotonSystemClipboardUtil.write(KIND, data);
		return true;
	}

	public static boolean compatible(AnimatedProperty property) {
		var data = PhotonSystemClipboardUtil.read(KIND);
		if (data == null || property == null)
			return false;
		try {
			return switch (Selection.valueOf(data.getStringOr("selection", ""))) {
				case GRADIENT, STOP -> property instanceof ColorAnimatedProperty;
				case CURVE -> property instanceof ConfigAnimatedProperty && property.channelCount() > 0;
				case EXPR -> property.channelCount() > 0;
			};
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	public static boolean paste(TimelineContext ctx, Track track, AnimationTrackUIState state, double tick) {
		var access = (AnimationTrackUIStateAccessor) state;
		var property = access.lobecorp$getSelectedProperty();
		if (track.lock() || !compatible(property) || !Double.isFinite(tick) || tick < 0)
			return false;
		var before = property.copy();
		try {
			var data = PhotonSystemClipboardUtil.read(KIND);
			if (data == null)
				return false;
			var kind = Selection.valueOf(data.getStringOr("selection", ""));
			var entries = data.getListOrEmpty("entries");
			if (entries.isEmpty() || entries.size() > PhotonSystemClipboardUtil.MAX_ITEMS)
				return false;
			int axis = Math.max(0, access.lobecorp$getSelectedAxis());
			if (kind == Selection.STOP) {
				if (entries.size() != 1 || !Float.isFinite((float) tick))
					return false;
				var color = (ColorAnimatedProperty) property;
				var entry = entries.getCompound(0).orElseThrow(IllegalArgumentException::new);
				float intensity = entry.getFloatOr("intensity", Float.NaN);
				if (!Float.isFinite(intensity) || intensity < 0 || !entry.contains("argb")
						|| color.stops().stream().anyMatch(stop -> Math.abs(stop.tick - (float) tick) < PhotonCurveEditUtil.KEY_GAP))
					return false;
				color.addStop((float) tick, entry.getIntOr("argb", 0), intensity);
			} else {
				if (kind != Selection.GRADIENT && axis >= property.channelCount())
					return false;
				var candidates = new ArrayList<SubClip>();
				for (var value : entries) {
					if (!(value instanceof CompoundTag entry))
						return false;
					double relative = entry.getDoubleOr("start", Double.NaN), duration = entry.getDoubleOr("duration", Double.NaN);
					double start = tick + relative;
					if (!Double.isFinite(relative) || relative < 0 || !Double.isFinite(start)
							|| !Double.isFinite(duration) || duration <= 0 || !Double.isFinite(start + duration))
						return false;
					candidates.add(switch (kind) {
						case GRADIENT -> {
							var gradient = new GradientColor();
							ValueIONbt.fromTag(gradient, Platform.getFrozenRegistry(), entry.getCompound("gradient").orElseThrow(IllegalArgumentException::new));
							yield new GradientClip(start, duration, gradient);
						}
						case CURVE -> {
							var curve = NumberFunction.deserializeWrapper(entry.getCompound("curve").orElseThrow(IllegalArgumentException::new));
							if (curve == null)
								throw new IllegalArgumentException();
							yield new CurveClip(start, duration, curve);
						}
						case EXPR ->
								new ExprClip(start, duration, entry.getString("expr").orElseThrow(IllegalArgumentException::new));
						case STOP -> throw new IllegalArgumentException();
					});
				}
				List<? extends SubClip> existing = switch (kind) {
					case GRADIENT -> ((ColorAnimatedProperty) property).gradientClips();
					case CURVE -> ((ConfigAnimatedProperty) property).curveClips(axis);
					case EXPR -> property.exprClips(axis);
					case STOP -> throw new IllegalArgumentException();
				};
				for (int i = 0; i < candidates.size(); i++) {
					var clip = candidates.get(i);
					if (existing.stream().anyMatch(other -> overlaps(clip, other)))
						return false;
					for (int j = i + 1; j < candidates.size(); j++)
						if (overlaps(clip, candidates.get(j)))
							return false;
				}
				for (var clip : candidates)
					switch (clip) {
						case GradientClip gradient -> ((ColorAnimatedProperty) property).gradientClips().add(gradient);
						case CurveClip curve -> ((ConfigAnimatedProperty) property).curveClips(axis).add(curve);
						case ExprClip expression -> property.addExprClip(axis, expression);
						default -> throw new IllegalArgumentException();
					}
			}
		} catch (RuntimeException ignored) {
			property.restoreFrom(before);
			return false;
		}
		var after = property.copy();
		clearSelection(access);
		ctx.pushApplied("photon.gui.editor.timeline.edit_curve",
				() -> {
					property.restoreFrom(after);
					clearSelection(access);
					ctx.requestRebuild();
					ctx.refreshPreview();
				},
				() -> {
					property.restoreFrom(before);
					clearSelection(access);
					ctx.requestRebuild();
					ctx.refreshPreview();
				});
		ctx.requestRebuild();
		ctx.refreshPreview();
		return true;
	}

	private static boolean overlaps(SubClip clip, SubClip other) {
		return clip.start() < other.end() && other.start() < clip.end();
	}

	private static void clearSelection(AnimationTrackUIStateAccessor access) {
		access.lobecorp$getGradientClips().clear();
		access.lobecorp$getCurveClips().clear();
		access.lobecorp$getExprClips().clear();
		access.lobecorp$getSelectedKeys().clear();
		access.lobecorp$setSelectedStop(null);
		access.lobecorp$setPrimaryAxis(-1);
		access.lobecorp$setPrimaryIndex(-1);
	}
}
