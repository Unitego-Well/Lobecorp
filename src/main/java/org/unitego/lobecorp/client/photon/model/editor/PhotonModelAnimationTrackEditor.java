package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.FXHierarchyView;
import com.lowdragmc.photon.gui.editor.view.timeline.ClipTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackUIState;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.emitter.model.PhotonModelEmitter;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelAnimationClip;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/// 使用原生片段交互编辑模型动画选择与采样参数，目标仅限模型发射器。
public class PhotonModelAnimationTrackEditor extends ClipTrackEditor {
	@Override
	public ColorPattern chipColor() {
		return ColorPattern.GREEN;
	}

	@Override
	public ColorPattern clipFillColor() {
		return ColorPattern.T_GREEN;
	}

	@Override
	public UIElement buildHeaderContent(TimelineContext context, Track track, TrackUIState state) {
		var slot = new UIElement().setId("timeline.trackHeader.target")
				.layout(layout -> layout.flex(1).heightPercent(100).paddingAll(2))
				.style(style -> style.backgroundTexture(ColorPattern.BLACK.rectTexture()));
		var label = new Label().bindDataSource(SupplierDataSource.of(() -> {
			var emitter = boundEmitter(context, track);
			return emitter == null ? Component.translatable(PhotonModelTextUtil.key("none")) : Component.literal(emitter.getName());
		}));
		TimelineContext.styleLabel(label);
		label.layout(layout -> layout.flex(1).heightPercent(100));
		slot.addChild(label);
		slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
			if (event.button == 0) {
				openTargetPicker(context, track, event.x, event.y);
			}
		});
		slot.addEventListener(UIEvents.DRAG_PERFORM, event -> {
			if (event.dragHandler.getDraggingObject() instanceof FXHierarchyView.DraggingNode(var node)
					&& node.getKey() instanceof PhotonModelEmitter emitter) {
				context.bind(track, emitter.id());
				event.stopPropagation();
			}
		});
		return slot;
	}

	private static void openTargetPicker(TimelineContext context, Track track, float x, float y) {
		var menu = TreeBuilder.Menu.start();
		menu.leaf(PhotonModelTextUtil.key("none"), () -> context.bind(track, null));

		for (var emitter : modelEmitters(context)) {
			menu.leaf(emitter.getName(), () -> context.bind(track, emitter.id()));
		}

		context.openMenu(x, y, menu);
	}

	@Override
	protected String clipLabel(TimelineContext context, Track track, Clip clip) {
		if (!(clip instanceof PhotonModelAnimationClip model)) {
			return null;
		}

		return model.animation.isEmpty() ? Component.translatable(PhotonModelTextUtil.key("clip")).getString() : model.animation;
	}

	@Override
	public boolean clipsConflict(Clip first, Clip second) {
		return false;
	}

	@Override
	protected void onLaneDoubleClick(TimelineContext context, Track track, float x, float y) {
		addClip(context, track, x);
	}

	@Override
	protected void onLaneRightClick(TimelineContext context, Track track, float x, float y) {
		var menu = TreeBuilder.Menu.start();
		menu.leaf("photon.gui.editor.timeline.add_clip", () -> addClip(context, track, x));
		context.openMenu(x, y, menu);
	}

	private static void addClip(TimelineContext context, Track track, float x) {
		var emitter = boundEmitter(context, track);
		var asset = emitter == null ? null : emitter.getAsset();
		var animation = asset == null ? null : asset.clips.stream()
				.filter(candidate -> candidate.id.equals(emitter.config.clip)).findFirst().orElse(null);

		if (animation == null && asset != null && !asset.clips.isEmpty()) {
			animation = asset.clips.getFirst();
		}

		var duration = animation == null ? context.defaultClipDuration(track.targetId()) : animation.duration;
		var clip = new PhotonModelAnimationClip(Math.max(0, Math.round(context.xToTick(x))), Math.max(1, duration), 1);

		if (animation != null) {
			clip.animation = animation.id;
		}

		context.addClip(track, clip);
	}

	@Override
	protected void buildHeaderConfigurator(ConfiguratorGroup group, TimelineContext context, Track track) {
		var emitters = modelEmitters(context);
		var candidates = new ArrayList<String>();
		candidates.add("");
		emitters.stream().map(emitter -> emitter.id().toString()).forEach(candidates::add);
		var selected = targetReference(track);

		if (!candidates.contains(selected)) {
			candidates.add(selected);
		}

		group.addConfigurator(new SelectorConfigurator<>(PhotonModelTextUtil.key("timeline.model_target"),
				() -> targetReference(track),
				value -> context.bind(track, value.isEmpty() ? null : UUID.fromString(value)), "", true, candidates,
				value -> emitters.stream().filter(emitter -> emitter.id().toString().equals(value))
						.map(PhotonModelEmitter::getName).findFirst().orElse(PhotonModelTextUtil.key("none"))));
	}

	@Override
	protected void buildClipConfigurator(ConfiguratorGroup group, TimelineContext context, Track track, Clip clip) {
		if (!(clip instanceof PhotonModelAnimationClip model)) {
			return;
		}

		var emitter = boundEmitter(context, track);
		var candidates = new ArrayList<String>();
		candidates.add("");

		if (emitter != null) {
			emitter.getAsset().clips.stream().map(animation -> animation.id).forEach(candidates::add);
		}

		if (!candidates.contains(model.animation)) {
			candidates.add(model.animation);
		}

		group.addConfigurators(
				new SelectorConfigurator<>(PhotonModelTextUtil.key("clip"), () -> model.animation,
						value -> edit(context, model, () -> model.animation = value, true), "", true, candidates,
						value -> value == null ? "" : value.isEmpty() ? PhotonModelTextUtil.key("none") : value),
				new NumberConfigurator(PhotonModelTextUtil.key("playback_speed"), model::speed,
						value -> edit(context, model, () -> model.speed(value.floatValue()), false), 1f, true)
						.setRange(0, Float.MAX_VALUE),
				new NumberConfigurator(PhotonModelTextUtil.key("animation_offset"), () -> model.offset,
						value -> edit(context, model, () -> model.offset = value.doubleValue(), false), 0d, true),
				new BooleanConfigurator(PhotonModelTextUtil.key("animation_loop"), () -> model.loop,
						value -> edit(context, model, () -> model.loop = value, false), false, true),
				new SelectorConfigurator<>(PhotonModelTextUtil.key("timeline.completion"), () -> model.completion,
						value -> edit(context, model, () -> model.completion = value, false), PhotonModelAnimationClip.Completion.RESTORE,
						true, List.of(PhotonModelAnimationClip.Completion.values()),
						value -> value == null ? "" : PhotonModelTextUtil.key("timeline.completion." + value.name().toLowerCase(Locale.ROOT))));
	}

	private static void edit(TimelineContext context, PhotonModelAnimationClip clip, Runnable action, boolean inspect) {
		var before = clip.copy();
		context.pushEdit("photon.gui.editor.timeline.edit_clip", () -> {
			action.run();
			refresh(context, clip, inspect);
		}, () -> {
			clip.restoreFrom(before);
			refresh(context, clip, inspect);
		});
	}

	private static void refresh(TimelineContext context, PhotonModelAnimationClip clip, boolean inspect) {
		context.requestRebuild();
		context.refreshPreview();
		var track = context.selectedClipTrack();

		if (!inspect || track == null || context.selectedClip() != clip) {
			return;
		}

		var type = track.type();

		if (type != null && type.editor() instanceof ClipTrackEditor editor) {
			context.inspectProperty(track, editor.clipConfigurator(context, track, clip));
		}
	}

	private static List<PhotonModelEmitter> modelEmitters(TimelineContext context) {
		var runtime = context.runtime();
		return runtime == null ? List.of() : runtime.objects.values().stream()
				.filter(PhotonModelEmitter.class::isInstance).map(PhotonModelEmitter.class::cast).toList();
	}

	private static @Nullable PhotonModelEmitter boundEmitter(TimelineContext context, Track track) {
		var runtime = context.runtime();
		return runtime != null && runtime.objects.get(track.targetId()) instanceof PhotonModelEmitter emitter ? emitter : null;
	}

	private static String targetReference(Track track) {
		var target = track.targetId();
		return target == null ? "" : target.toString();
	}
}
