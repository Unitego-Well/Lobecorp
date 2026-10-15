package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.fx.timeline.property.ColorAnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.property.ColorPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigAnimatedProperty;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import dev.vfyjxf.taffy.style.TaffyDimension;
import org.joml.Vector2f;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTimelineUtil;

import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class PhotonCurveEasingUtil {
	/// 原生选择弹窗宽度，与编辑器工具弹窗保持一致，单位为 UI 像素。
	private static final int DIALOG_WIDTH = 260;
	/// 贝塞尔控制点在线性段上的默认比例。
	private static final float HANDLE_FRACTION = 1f / 3f;
	/// 创建确认期间的预设作用域，避免影响其他曲线创建或撤销。
	private static final ThreadLocal<String> CREATION_PRESET = new ThreadLocal<>();
	/// 只保留当前编辑曲线的新增键预设，弱键随预览及项目对象一起释放。
	private static final Map<AnimatedProperty, String> CREATED_PRESETS = new WeakHashMap<>();

	public static void addProperty(TimelineContext context, AnimationTrack track, AnimatedPropertyType type, Runnable add) {
		var runtime = context.runtime();
		var settings = PhotonEditorSettings.of(context.editor());

		if (runtime == null || track.lock() || type.stepped() || type instanceof ColorPropertyType
				|| !settings.enabled(PhotonEditorTextUtil.CURVE_EASING_FEATURE)) {
			add.run();
			return;
		}

		var targetId = track.targetId();

		if (targetId == null || !(runtime.objects.get(targetId) instanceof FXObject target)) {
			add.run();
			return;
		}

		var model = PhotonAnimationTimelineUtil.of(context);
		choose(context, "add_curve_easing", "curve_easing_tip", preset -> {
			if (context.runtime() != runtime || PhotonAnimationTimelineUtil.of(context) != model
					|| model.parentListOf(track) == null || track.lock() || track.property(type) != null
					|| !Objects.equals(track.targetId(), targetId) || runtime.objects.get(targetId) != target
					|| target.getFXObjectType().animatableProperties(target).stream()
					.noneMatch(candidate -> candidate.key().equals(type.key())
							&& candidate.channelCount() == type.channelCount())) {
				throw new IllegalArgumentException();
			}

			String previous = CREATION_PRESET.get();
			CREATION_PRESET.set(preset);

			try {
				add.run();
			} finally {
				if (previous == null) {
					CREATION_PRESET.remove();
				} else {
					CREATION_PRESET.set(previous);
				}
			}
		});
	}

	public static void created(AnimatedProperty property) {
		String preset = CREATION_PRESET.get();

		if (preset == null || property instanceof ColorAnimatedProperty || property.type().stepped()) {
			return;
		}

		CREATED_PRESETS.put(property, preset);

		for (int axis = 0; axis < property.channelCount(); axis++) {
			for (int index = 0; index + 1 < property.keyCount(axis); index++) {
				applySegment(property, axis, index, preset);
			}
		}
	}

	public static void applyCreatedKey(TimelineContext context, AnimatedProperty property, int axis, int index) {
		var settings = PhotonEditorSettings.of(context.editor());

		if (!settings.enabled(PhotonEditorTextUtil.CURVE_EASING_FEATURE)
				|| property instanceof ColorAnimatedProperty || property.type().stepped() || index < 0) {
			return;
		}

		String preset = CREATED_PRESETS.getOrDefault(property, settings.curveEasingPreset());
		Set<Long> keys = Set.of((long) axis << 32 | index & 0xffffffffL);

		if (settings.enabled(PhotonEditorTextUtil.SCALE_LINK_FEATURE)) {
			keys = PhotonScaleCurveUtil.linkedKeys(property, keys);
		}

		for (long key : keys) {
			int keyAxis = (int) (key >>> 32), keyIndex = (int) key;

			if (keyIndex > 0) {
				applySegment(property, keyAxis, keyIndex - 1, preset);
			}

			if (keyIndex + 1 < property.keyCount(keyAxis)) {
				applySegment(property, keyAxis, keyIndex, preset);
			}
		}
	}

	public static void choose(TimelineContext context, String title, String tip, Consumer<String> apply) {
		String[] selected = {PhotonEditorSettings.of(context.editor()).curveEasingPreset()};
		var group = new ConfiguratorGroup("", false).hideTitle();
		group.addConfigurator(PhotonEditorTooltipUtil.tips(new SelectorConfigurator<>(PhotonEditorTextUtil.key("curve_easing"),
				() -> selected[0], value -> selected[0] = value, selected[0], false,
				PhotonEditorTextUtil.CURVE_EASING_PRESETS, PhotonEditorTextUtil::key), PhotonEditorTextUtil.key(tip)));
		group.addChild(new TextElement().setText(PhotonEditorTextUtil.key(tip))
				.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true).textWrap(TextWrap.WRAP))
				.layout(layout -> layout.widthPercent(100)));
		var dialog = new Dialog().width(TaffyDimension.length(DIALOG_WIDTH)).setTitle(PhotonEditorTextUtil.key(title));
		dialog.addContent(group);
		dialog.addButton(new Button().setText(PhotonEditorTextUtil.key("close"))
				.setOnClick(_ -> dialog.close()));
		dialog.addButton(new Button().setText(PhotonEditorTextUtil.key("apply"))
				.setOnClick(_ -> {
					try {
						apply.accept(selected[0]);
						dialog.close();
					} catch (IllegalArgumentException error) {
						Dialog.showNotification(PhotonEditorTextUtil.key("curve_easing"),
								PhotonEditorTextUtil.key("invalid"), null).show(context.editor());
					}
				}));
		dialog.show(context.editor());
	}

	public static Map<AnimatedProperty, AnimatedProperty> captureSelection(List<PhotonCurveEditUtil.Selection> selections) {
		var snapshots = new LinkedHashMap<AnimatedProperty, AnimatedProperty>();

		for (var selection : selections) {
			snapshots.putIfAbsent(selection.property(), selection.property().copy());
		}

		return snapshots;
	}

	public static boolean selectionUnchanged(Timeline model, List<PhotonCurveEditUtil.Selection> selections,
	                                         Map<AnimatedProperty, AnimatedProperty> snapshots) {
		for (var selection : selections) {
			var property = selection.property();
			var snapshot = snapshots.get(property);

			if (model.parentListOf(selection.track()) == null || selection.track().lock()
					|| !selection.track().properties().contains(property) || snapshot == null
					|| property.channelCount() != snapshot.channelCount()) {
				return false;
			}

			for (long id : selection.keys()) {
				if (!keyUnchanged(property, snapshot, (int) (id >>> 32), (int) id)) {
					return false;
				}
			}
		}

		return true;
	}

	private static boolean keyUnchanged(AnimatedProperty property, AnimatedProperty snapshot, int axis, int index) {
		if (axis == PhotonCurveEditUtil.COLOR_AXIS && property instanceof ColorAnimatedProperty color
				&& snapshot instanceof ColorAnimatedProperty original) {
			return index >= 0 && index < color.stops().size() && color.stops().size() == original.stops().size()
					&& Float.compare(color.stops().get(index).tick, original.stops().get(index).tick) == 0;
		}

		if (axis < 0 || axis >= property.channelCount() || index < 0 || index >= property.keyCount(axis)
				|| property.keyCount(axis) != snapshot.keyCount(axis)) {
			return false;
		}

		return property.key(axis, index).equals(snapshot.key(axis, index))
				&& Objects.equals(property.inHandle(axis, index), snapshot.inHandle(axis, index))
				&& Objects.equals(property.outHandle(axis, index), snapshot.outHandle(axis, index));
	}

	public static void applyTangent(AnimatedProperty property, int axis, int index, String preset) {
		if (property.type().stepped() || property instanceof ColorAnimatedProperty) {
			return;
		}

		if (preset.equals("align")) {
			var point = property.key(axis, index);
			var in = property.inHandle(axis, index);
			var out = property.outHandle(axis, index);

			if (in != null && out != null) {
				if (point.x - in.x > PhotonCurveEditUtil.KEY_GAP) {
					property.setOutHandle(axis, index, out.x,
							point.y + (point.y - in.y) / (point.x - in.x) * (out.x - point.x));
				}

				return;
			}
		}

		applyHandle(property, axis, index, -1, preset);
		applyHandle(property, axis, index, 1, preset);
	}

	private static void applySegment(AnimatedProperty property, int axis, int index, String preset) {
		float start = property.key(axis, index).x;
		float end = property.key(axis, index + 1).x;

		if (property.exprClips(axis).stream().anyMatch(clip -> clip.start() < end && clip.end() > start)
				|| property instanceof ConfigAnimatedProperty config
				&& config.curveClips(axis).stream().anyMatch(clip -> clip.start() < end && clip.end() > start)) {
			return;
		}

		applyHandle(property, axis, index, 1, preset);
		applyHandle(property, axis, index + 1, -1, preset);
	}

	private static void applyHandle(AnimatedProperty property, int axis, int index, int direction, String preset) {
		int neighbor = index + direction;

		if (neighbor < 0 || neighbor >= property.keyCount(axis)) {
			return;
		}

		var point = property.key(axis, index);
		var end = property.key(axis, neighbor);
		var handle = new Vector2f((float) (point.x + ((double) end.x - point.x) * HANDLE_FRACTION),
				(float) (point.y + ((double) end.y - point.y) * HANDLE_FRACTION));

		if (preset.equals("flat") || preset.equals("ease_both")
				|| preset.equals("ease_in") && direction > 0 || preset.equals("ease_out") && direction < 0) {
			handle.y = point.y;
		}

		if (direction < 0) {
			property.setInHandle(axis, index, handle.x, handle.y);
		} else {
			property.setOutHandle(axis, index, handle.x, handle.y);
		}
	}
}
