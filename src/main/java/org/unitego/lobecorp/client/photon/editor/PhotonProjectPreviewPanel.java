package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import com.lowdragmc.photon.client.fx.timeline.Timeline;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.unitego.lobecorp.config.photon.playback.PhotonNamedLibrary.Completion;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedHandle;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedPlan;
import org.unitego.lobecorp.client.photon.playback.PhotonProjectPreviewAccess;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/// 工程测试的制作与定位入口；修改进入工程历史，不写入发布库。
public class PhotonProjectPreviewPanel {
	public static final String TITLE = "photon.lobecorp.animation.test_timeline";
	private final FXEditor editor;
	private final FXProject project;
	private final FXRuntime runtime;
	private final TextElement status = new TextElement();
	private @Nullable PhotonNamedRuntime playback;
	private @Nullable PhotonNamedHandle handle;
	private double time;

	public PhotonProjectPreviewPanel(FXEditor editor, FXProject project, FXRuntime runtime) {
		this.editor = editor;
		this.project = project;
		this.runtime = runtime;
		time = editor.timelineView.currentTimeTicks();
	}

	public static void open(FXEditor editor) {
		if (!(editor.getCurrentProject() instanceof FXProject project) || editor.runtime == null) {
			return;
		}

		new PhotonProjectPreviewPanel(editor, project, editor.runtime).show();
	}

	private void show() {
		var dialog = new Dialog().setTitle(TITLE).width(TaffyDimension.length(350));
		var content = new UIElement().layout(layout -> layout.widthPercent(100).flexDirection(FlexDirection.COLUMN).gapAll(4));
		content.addChild(new TextElement().setText(TITLE + ".tooltip")
				.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true).textWrap(TextWrap.WRAP))
				.layout(layout -> layout.widthPercent(100).flexShrink(0)));
		content.addChild(new NumberConfigurator("photon.lobecorp.animation.time", () -> time,
				value -> time = value.doubleValue(), time, false).setRange(0, Double.MAX_VALUE));
		content.addChild(status.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true).textWrap(TextWrap.WRAP))
				.layout(layout -> layout.widthPercent(100).flexShrink(0)));
		dialog.addContent(content);
		dialog.addButton(new Button().setText("photon.lobecorp.animation.inspector_panel").setOnClick(_ -> edit()));
		dialog.addButton(new Button().setText("photon.lobecorp.animation.play").setOnClick(_ -> start()));
		dialog.addButton(new Button().setText("photon.lobecorp.editor.apply").setOnClick(_ -> seek()));
		dialog.addButton(new Button().setText("photon.lobecorp.animation.stop").setOnClick(_ -> stop()));
		dialog.addButton(new Button().setText("photon.lobecorp.editor.close").setOnClick(_ -> dialog.close()));
		dialog.setOnClose(this::stop);
		dialog.show(editor);
	}

	public void edit() {
		try {
			requireCurrent();
			var before = ((PhotonProjectPreviewAccess) project).lobecorp$projectPreview();
			var source = before == null ? PhotonProjectPreviewUtil.create() : before.copy();
			var definition = PhotonProjectPreviewUtil.read(source);
			String[] name = {definition.name()};
			double[] duration = {definition.duration()};
			boolean[] flags = {definition.autoDuration(), definition.loop()};
			int[] priority = {definition.priority()};
			Completion[] completion = {definition.completion()};
			var group = new ConfiguratorGroup("", false).hideTitle();
			group.addConfigurator(new StringConfigurator("photon.lobecorp.animation.name", () -> name[0], value -> name[0] = value, name[0], false));
			group.addConfigurator(new NumberConfigurator("photon.lobecorp.animation.duration", () -> duration[0], value -> duration[0] = value.doubleValue(), duration[0], false).setRange(0, Double.MAX_VALUE));
			group.addConfigurator(new BooleanConfigurator("photon.lobecorp.animation.auto_duration", () -> flags[0], value -> flags[0] = value, flags[0], false));
			group.addConfigurator(new BooleanConfigurator("photon.lobecorp.animation.loop", () -> flags[1], value -> flags[1] = value, flags[1], false));
			group.addConfigurator(new NumberConfigurator("photon.lobecorp.animation.priority", () -> priority[0], value -> priority[0] = value.intValue(), priority[0], false).setRange(Integer.MIN_VALUE, Integer.MAX_VALUE));
			group.addConfigurator(new SelectorConfigurator<>("photon.lobecorp.animation.completion", () -> completion[0], value -> completion[0] = value, completion[0], false, List.of(Completion.values()), value -> Component.translatable("photon.lobecorp.animation.enum." + value.name()).getString()));
			var message = new TextElement().textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(true).textWrap(TextWrap.WRAP));
			var dialog = new Dialog().setTitle(TITLE).width(TaffyDimension.length(400));
			dialog.contentContainer.layout(layout -> layout.flexDirection(FlexDirection.COLUMN));
			dialog.addContent(group);
			dialog.addContent(message);
			dialog.addButton(new Button().setText("photon.lobecorp.editor.action.duplicate").setOnClick(_ -> {
				try {
					requireCurrent();
					copySelectedTracks(source);
					message.setText(Component.literal(Integer.toString(source.getListOrEmpty("tracks").size())));
				} catch (IllegalArgumentException | IllegalStateException invalid) {
					message.setText(Component.literal(invalid.getMessage()));
				}
			}));
			dialog.addButton(new Button().setText("photon.lobecorp.editor.apply").setOnClick(_ -> {
				try {
					requireCurrent();
					applyDefinition(editor, project, before, PhotonProjectPreviewUtil.edit(source, name[0], duration[0], flags[0], flags[1], priority[0], completion[0]));
					stop();
					dialog.close();
				} catch (IllegalArgumentException | IllegalStateException invalid) {
					message.setText(Component.literal(invalid.getMessage()));
				}
			}));
			dialog.addButton(new Button().setText("photon.lobecorp.editor.close").setOnClick(_ -> dialog.close()));
			dialog.show(editor);
		} catch (IOException | IllegalArgumentException | IllegalStateException invalid) {
			showFailure(invalid);
		}
	}

	/// 比较完整原树，防止另一个面板或历史跳转后覆盖较新的测试定义。
	public static void applyDefinition(FXEditor editor, FXProject project, @Nullable CompoundTag expected, CompoundTag source) {
		if (editor.getCurrentProject() != project || !Objects.equals(expected, ((PhotonProjectPreviewAccess) project).lobecorp$projectPreview())) {
			throw new IllegalStateException("工程测试定义已改变，请重新打开制作面板。");
		}

		var before = expected == null ? null : expected.copy();
		var after = PhotonProjectPreviewUtil.checkedCopy(source);
		PhotonEditorProjectGuardUtil.requireSupported(after);
		if (editor.runtime == null) {
			throw new IllegalStateException("工程预览实例已失效。");
		}

		try {
			new PhotonNamedPlan(Platform.getFrozenRegistry(), editor.runtime, PhotonProjectPreviewUtil.read(after), true);
		} catch (IOException invalid) {
			throw new IllegalArgumentException("工程测试定义无法读取。", invalid);
		}

		if (Objects.equals(before, after)) {
			return;
		}

		editor.timelineView.pushEdit(TITLE, () -> restoreDefinition(editor, project, after), () -> restoreDefinition(editor, project, before));
	}

	private static void restoreDefinition(FXEditor editor, FXProject project, @Nullable CompoundTag source) {
		if (editor.getCurrentProject() != project) {
			throw new IllegalStateException("工程测试历史已失效。");
		}

		((PhotonProjectPreviewAccess) project).lobecorp$projectPreview(source);
	}

	/// 只复制最外层选中轨道，保持主场景数据、目标身份和组内顺序。
	private void copySelectedTracks(CompoundTag source) {
		var selected = ((PhotonTimelineEditAccess) editor.timelineView).lobecorp$selectedTracks();
		if (selected.isEmpty()) {
			throw new IllegalArgumentException("请先在主场景时间轴选择需要复制的轨道。");
		}

		var tracks = source.get("tracks") instanceof ListTag saved ? saved.copy() : new ListTag();
		for (var track : selected) {
			if (selected.stream().anyMatch(parent -> parent != track && Timeline.isDescendant(parent, track))) {
				continue;
			}

			var entry = Timeline.writeTrack(track, Platform.getFrozenRegistry());
			PhotonEditorProjectGuardUtil.requireSupported(entry);
			tracks.add(entry);
		}

		source.put("tracks", tracks);
	}

	public void start() {
		try {
			requireCurrent();
			var source = ((PhotonProjectPreviewAccess) project).lobecorp$projectPreview();

			if (source == null) {
				throw new IllegalArgumentException("工程没有保存专用测试时间轴。");
			}

			var current = ((PhotonNamedAccess) runtime).lobecorp$namedRuntime();

			if (current == null || current.closed()) {
				throw new IllegalArgumentException("请先启动原生场景预览并确认命名控制器有效。");
			}

			var definition = PhotonProjectPreviewUtil.read(source);
			handle = current.preview(definition, time);
			playback = current;
			status.setText(Component.literal(definition.name() + " @ " + time));
		} catch (IOException | IllegalArgumentException | IllegalStateException invalid) {
			showFailure(invalid);
		}
	}

	public void seek() {
		try {
			requireCurrent();

			if (playback == null || handle == null || !playback.ownsPreview(handle)
					|| ((PhotonNamedAccess) runtime).lobecorp$namedRuntime() != playback) {
				throw new IllegalArgumentException("工程测试已停止或场景已重建，请重新启动测试。");
			}

			playback.seekPreview(time);
			status.setText(Component.literal(handle.definition().name() + " @ " + time));
		} catch (IllegalArgumentException | IllegalStateException invalid) {
			showFailure(invalid);
		}
	}

	public void stop() {
		if (playback != null && handle != null && playback.ownsPreview(handle)) {
			playback.stopPreview();
		}

		handle = null;
		playback = null;
		status.setText("photon.lobecorp.animation.stop");
	}

	private void requireCurrent() {
		if (editor.getCurrentProject() != project || editor.runtime != runtime || !Double.isFinite(time) || time < 0) {
			throw new IllegalArgumentException("工程、预览实例或定位时间已失效，请重新打开测试面板。");
		}
	}

	private void showFailure(Exception invalid) {
		status.setText(Component.translatable("photon.lobecorp.animation.invalid")
				.append(Component.literal(": " + invalid.getMessage())));
	}
}
