package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorHost;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import org.jetbrains.annotations.Nullable;

public class PhotonEditorSaveUtil {

	/// 保留原特效编辑器公共入口；模型工程使用通用解析方法。
	@SuppressWarnings("unused")
	public static @Nullable FXEditor currentEditor(ModularUI modularUI, @Nullable UIElement target) {
		return currentProjectEditor(modularUI, target) instanceof FXEditor editor
				&& editor.getCurrentProject() instanceof FXProject ? editor : null;
	}

	/// 解析特效或模型工程的活动宿主，保存命令与共享设置沿用同一窗口边界。
	public static @Nullable Editor currentProjectEditor(ModularUI modularUI, @Nullable UIElement target) {
		Editor hostedEditor = null;

		for (var element = target; element != null; element = element.getParent()) {
			if (element instanceof EditorWindow window) {
				return projectEditor(window.getCurrentEditor());
			}

			if (hostedEditor == null && element instanceof EditorHost host) {
				hostedEditor = host.getHostedEditor();
			}
		}

		if (hostedEditor != null) {
			return projectEditor(hostedEditor);
		}

		if (modularUI.ui.rootElement instanceof EditorHost host) {
			return projectEditor(host.getHostedEditor());
		}

		EditorWindow currentWindow = null;

		for (var window : modularUI.getElementsByType(EditorWindow.class)) {
			if (!isAvailable(window)) {
				continue;
			}

			if (currentWindow != null) {
				return null;
			}

			currentWindow = window;
		}

		return currentWindow == null ? null : projectEditor(currentWindow.getCurrentEditor());
	}

	/// 解析元素所属编辑器，包括浮动面板；空工程也可使用文本编辑与设置操作。
	public static @Nullable Editor findEditor(@Nullable UIElement target) {
		for (var element = target; element != null; element = element.getParent()) {
			if (element instanceof EditorHost host) {
				return supportedEditor(host.getHostedEditor());
			}

			if (element instanceof EditorWindow window) {
				return supportedEditor(window.getCurrentEditor());
			}
		}

		return null;
	}

	private static @Nullable Editor projectEditor(@Nullable Editor editor) {
		var supported = supportedEditor(editor);
		return supported == null || supported.getCurrentProject() == null ? null : supported;
	}

	private static @Nullable Editor supportedEditor(@Nullable Editor editor) {
		if (!(editor instanceof FXEditor)) {
			return null;
		}

		return editor;
	}

	private static boolean isAvailable(UIElement element) {
		for (var parent = element; parent != null; parent = parent.getParent()) {
			if (!parent.isDisplayed() || !parent.isActive()) {
				return false;
			}
		}

		return true;
	}
}
