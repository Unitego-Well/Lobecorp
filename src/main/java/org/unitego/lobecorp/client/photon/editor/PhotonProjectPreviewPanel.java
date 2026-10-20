package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedHandle;
import org.unitego.lobecorp.client.photon.playback.PhotonNamedRuntime;
import org.unitego.lobecorp.client.photon.playback.PhotonProjectPreviewAccess;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;

import javax.annotation.Nullable;
import java.io.IOException;

/// 工程测试的定位入口；只持有当前工程实例，不修改制作定义或发布库。
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
		dialog.addButton(new Button().setText("photon.lobecorp.animation.play").setOnClick(_ -> start()));
		dialog.addButton(new Button().setText("photon.lobecorp.editor.apply").setOnClick(_ -> seek()));
		dialog.addButton(new Button().setText("photon.lobecorp.animation.stop").setOnClick(_ -> stop()));
		dialog.addButton(new Button().setText("photon.lobecorp.editor.close").setOnClick(_ -> dialog.close()));
		dialog.setOnClose(this::stop);
		dialog.show(editor);
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
