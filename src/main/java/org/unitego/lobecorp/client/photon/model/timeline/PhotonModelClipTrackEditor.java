package org.unitego.lobecorp.client.photon.model.timeline;

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
import org.unitego.lobecorp.client.photon.model.PhotonModelOutput;
import org.unitego.lobecorp.registry.translation.photon.model.PhotonModelTranslations;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// 拖动/缩放/选择沿用原生片段UI；模型字段修改通过同一原生撤销事务提交。
public class PhotonModelClipTrackEditor extends ClipTrackEditor {
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
		var slot = new UIElement().layout(layout -> layout.flex(1).heightPercent(100));
		var label = new Label().bindDataSource(SupplierDataSource.of(() -> {
			var model = bound(context, track);
			return model == null ? Component.translatable(PhotonModelTranslations.NONE) : Component.literal(model.getName());
		}));
		TimelineContext.styleLabel(label);
		slot.addChild(label);
		slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
			var runtime = context.runtime();

			if (event.button != 0 || runtime == null) {
				return;
			}

			var menu = TreeBuilder.Menu.start();
			menu.leaf(PhotonModelTranslations.NONE, () -> context.bind(track, null));

			for (var object : runtime.objects.values()) {
				if (object instanceof PhotonModelOutput model) {
					menu.leaf(model.getName(), () -> context.bind(track, model.id()));
				}
			}

			context.openMenu(event.x, event.y, menu);
		});
		slot.addEventListener(UIEvents.DRAG_PERFORM, event -> {
			if (event.dragHandler.getDraggingObject() instanceof FXHierarchyView.DraggingNode(var node)
					&& node.getKey() instanceof PhotonModelOutput model) {
				context.bind(track, model.id());
				event.stopPropagation();
			}
		});
		return slot;
	}

	@Override
	protected @Nullable String clipLabel(TimelineContext context, Track track, Clip value) {
		return value instanceof PhotonModelClip clip ? clip.animation : null;
	}

	@Override
	public boolean clipsConflict(Clip first, Clip second) {
		return false;
	}

	@Override
	protected void onLaneDoubleClick(TimelineContext context, Track track, float x, float y) {
		add(context, track, x);
	}

	@Override
	protected void onLaneRightClick(TimelineContext context, Track track, float x, float y) {
		var menu = TreeBuilder.Menu.start();
		menu.leaf("photon.gui.editor.timeline.add_clip", () -> add(context, track, x));
		context.openMenu(x, y, menu);
	}

	private static void add(TimelineContext context, Track track, float x) {
		var model = bound(context, track);
		var ids = model == null ? List.<String>of() : model.animationIds();
		var selected = model != null && ids.contains(model.config.clip) ? model.config.clip : ids.isEmpty() ? "" : ids.getFirst();
		double duration = model == null || selected.isEmpty() ? context.defaultClipDuration(track.targetId()) : model.animationDuration(selected);
		var clip = new PhotonModelClip(Math.max(0, Math.round(context.xToTick(x))), Math.max(1, duration), 1);
		clip.animation = selected;
		context.addClip(track, clip);
	}

	@Override
	protected void buildClipConfigurator(ConfiguratorGroup group, TimelineContext context, Track track, Clip value) {
		var clip = (PhotonModelClip) value;
		var model = bound(context, track);
		var choices = new ArrayList<String>();
		choices.add("");

		if (model != null) {
			choices.addAll(model.animationIds());
		}

		if (!choices.contains(clip.animation)) {
			choices.add(clip.animation);
		}

		group.addConfigurators(
				new SelectorConfigurator<>(PhotonModelTranslations.CLIP, () -> clip.animation,
						next -> edit(context, clip, () -> clip.animation = next), "", true, choices,
						id -> id.isEmpty() ? PhotonModelTranslations.NONE : id),
				new NumberConfigurator(PhotonModelTranslations.PLAYBACK_SPEED, clip::speed,
						next -> edit(context, clip, () -> clip.speed(next.floatValue())), 1f, true).setRange(0, Float.MAX_VALUE),
				new NumberConfigurator(PhotonModelTranslations.ANIMATION_OFFSET, () -> clip.offset,
						next -> edit(context, clip, () -> clip.offset = next.doubleValue()), 0d, true).setRange(-Float.MAX_VALUE, Float.MAX_VALUE),
				new BooleanConfigurator(PhotonModelTranslations.ANIMATION_LOOP, () -> clip.loop,
						next -> edit(context, clip, () -> clip.loop = next), false, true),
				new SelectorConfigurator<>(PhotonModelTranslations.TIMELINE_COMPLETION, () -> clip.completion,
						next -> edit(context, clip, () -> clip.completion = next), PhotonModelClip.Completion.RESTORE, true,
						List.of(PhotonModelClip.Completion.values()),
						next -> PhotonModelTranslations.key("timeline.completion." + next.name().toLowerCase(Locale.ROOT))));
	}

	private static void edit(TimelineContext context, PhotonModelClip clip, Runnable change) {
		var before = clip.copy();
		context.pushEdit("photon.gui.editor.timeline.edit_clip", () -> {
			try {
				change.run();
				clip.validate();
			} catch (RuntimeException failure) {
				clip.restore(before);
				throw failure;
			}

			context.requestRebuild();
			context.refreshPreview();
		}, () -> {
			clip.restore(before);
			context.requestRebuild();
			context.refreshPreview();
		});
	}

	private static @Nullable PhotonModelOutput bound(TimelineContext context, Track track) {
		var runtime = context.runtime();
		return runtime != null && runtime.objects.get(track.targetId()) instanceof PhotonModelOutput model ? model : null;
	}
}
