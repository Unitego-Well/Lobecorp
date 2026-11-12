package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.fx.timeline.TrackGroup;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDimension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.client.photon.playback.PhotonActivationTrackAccess;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions.EndBehavior;
import org.unitego.lobecorp.config.photon.playback.PhotonActivationOptions.StartBehavior;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslationKeys;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/// 配置当前原生激活轨道；修改进入原生撤销历史，不直接写磁盘。
public class PhotonActivationPanel {
	public static void open(FXEditor editor) {
		var selected = ((PhotonTimelineEditAccess) editor.timelineView).lobecorp$selectedTracks();
		if (selected.size() != 1 || !(selected.iterator().next() instanceof ActivatorTrack track) || editor.runtime == null) {
			Dialog.showNotification(key("control"), Component.translatable(key("invalid")).getString(), null).show(editor);
			return;
		}

		var project = editor.getCurrentProject();
		var runtime = editor.runtime;
		var initial = ((PhotonActivationTrackAccess) track).lobecorp$activationOptions();
		boolean[] flags = {initial.startControl(), initial.endControl(), initial.clearOnRestart()};
		StartBehavior[] start = {initial.startBehavior()};
		EndBehavior[] end = {initial.endBehavior()};
		var group = new ConfiguratorGroup("", false).hideTitle();
		group.addConfigurator(new BooleanConfigurator(key("control.start"), () -> flags[0], value -> flags[0] = value, flags[0], false));
		group.addConfigurator(new SelectorConfigurator<>(key("control.start_behavior"), () -> start[0], value -> start[0] = value,
				start[0], false, List.of(StartBehavior.values()), value -> Component.translatable(key("control." +
				(value == StartBehavior.RESTART ? "restart" : "continue"))).getString()));
		group.addConfigurator(new BooleanConfigurator(key("control.end"), () -> flags[1], value -> flags[1] = value, flags[1], false));
		group.addConfigurator(new SelectorConfigurator<>(key("control.end_behavior"), () -> end[0], value -> end[0] = value,
				end[0], false, List.of(EndBehavior.values()), value -> Component.translatable(key("control." + switch (value) {
			case ORIGINAL -> "original";
			case STOP_EMISSION -> "stop_emission";
			case CLEAR -> "clear";
		})).getString()));
		group.addConfigurator(new BooleanConfigurator(key("control.clear_on_restart"), () -> flags[2], value -> flags[2] = value, flags[2], false));
		var status = new TextElement();
		status.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true).textWrap(TextWrap.WRAP))
				.layout(layout -> layout.widthPercent(100));
		var dialog = new Dialog().setTitle(key("control")).width(TaffyDimension.length(350));
		dialog.contentContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
		dialog.addContent(group);
		dialog.addContent(status);
		dialog.addButton(new Button().setText(key("apply")).setOnClick(_ -> {
			try {
				if (editor.getCurrentProject() != project || editor.runtime != runtime) {
					throw new IllegalArgumentException("工程或预览实例已改变，请重新打开激活面板。");
				}

				apply(editor, track, new PhotonActivationOptions(flags[0], start[0], flags[1], end[0], flags[2]));
				dialog.close();
			} catch (IllegalArgumentException invalid) {
				status.setText(Component.literal(invalid.getMessage()));
			}
		}));
		dialog.addButton(new Button().setText(key("close")).setOnClick(_ -> dialog.close()));
		dialog.show(editor);
	}

	public static void apply(FXEditor editor, ActivatorTrack track, PhotonActivationOptions options) {
		if (!(editor.getCurrentProject() instanceof FXProject project) || editor.runtime == null
				|| !editor.runtime.fxData.timeline().leafTracks(true).contains(track)) {
			throw new IllegalArgumentException("激活轨道已不属于当前工程。");
		}

		var access = (PhotonActivationTrackAccess) track;
		if (access.lobecorp$activationOptions().equals(options)) {
			return;
		}

		var before = access.lobecorp$activationSource();
		var after = options.merge(before == null ? new CompoundTag() : before);
		var path = path(project.getFx().getFxData().timeline().tracks(), track);
		if (path == null) {
			throw new IllegalArgumentException("激活轨道已不属于当前工程。");
		}
		editor.timelineView.pushEdit(key("control"), () -> restore(editor, project, path, after),
				() -> restore(editor, project, path, before));
	}

	private static @Nullable List<Integer> path(List<Track> tracks, Track target) {
		for (int index = 0; index < tracks.size(); index++) {
			var current = tracks.get(index);
			if (current == target) {
				return List.of(index);
			}
			if (current instanceof TrackGroup group) {
				var nested = path(group.children(), target);
				if (nested != null) {
					var result = new ArrayList<Integer>();
					result.add(index);
					result.addAll(nested);
					return List.copyOf(result);
				}
			}
		}
		return null;
	}

	private static void restore(FXEditor editor, FXProject project, List<Integer> path, @Nullable CompoundTag source) {
		if (editor.getCurrentProject() != project) {
			throw new IllegalArgumentException("工程历史已失效。");
		}
		var tracks = project.getFx().getFxData().timeline().tracks();
		Track target = null;
		for (int index : path) {
			if (index >= tracks.size()) {
				throw new IllegalArgumentException("工程历史中的轨道路径已失效。");
			}
			target = tracks.get(index);
			tracks = target instanceof TrackGroup group ? group.children() : List.of();
		}
		if (!(target instanceof ActivatorTrack)) {
			throw new IllegalArgumentException("工程历史目标已不是激活轨道。");
		}
		((PhotonActivationTrackAccess) target).lobecorp$activationSource(source);
		editor.timelineView.requestRebuild();
		editor.timelineView.refreshPreview();
	}

	private static String key(String suffix) {
		return PhotonEditorTranslationKeys.key(suffix);
	}
}
