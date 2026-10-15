package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.photon.gui.editor.FXEditor;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.client.photon.model.editor.PhotonModelEditor;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.io.File;

public class PhotonEditorTitleUtil {

	public static Component title(Editor editor, Component original) {
		var file = editor.getCurrentProjectFile();

		if (unsupported(editor) || editor.getCurrentProject() == null) {
			return original;
		}

		if (file == null) {
			return editor instanceof PhotonModelProjectEditor ? original
					: original.copy().append(" - ").append(Component.translatable(PhotonEditorTextUtil.key("unnamed_effect")));
		}

		var settings = PhotonEditorSettings.of(editor);

		if (!settings.enabled(PhotonEditorTextUtil.COMPACT_TITLE_FEATURE)) {
			if (editor instanceof PhotonModelProjectEditor) {
				return Component.translatable(PhotonModelTextUtil.key("project.title"), file.getAbsolutePath())
						.setStyle(original.getStyle());
			}

			return original;
		}

		var path = file.getName();

		if (settings.enabled(PhotonEditorTextUtil.TITLE_CONFLICT_FEATURE) && hasConflict(editor, file)) {
			path = file.getAbsolutePath();
		}

		if (editor instanceof PhotonModelProjectEditor) {
			var name = path.equals(file.getAbsolutePath()) ? path : modelName(path);
			return Component.translatable(PhotonModelTextUtil.key("project.title"), name).setStyle(original.getStyle());
		}

		return replacePath(original, file.getPath(), path);
	}

	public static void appendPathTooltip(Editor editor, UIEvent event) {
		var file = editor.getCurrentProjectFile();

		if (unsupported(editor) || file == null
				|| !PhotonEditorSettings.of(editor).enabled(PhotonEditorTextUtil.TITLE_PATH_TOOLTIP_FEATURE)) {
			return;
		}

		var tooltips = event.hoverTooltips;

		if (tooltips == null) {
			var styled = event.currentElement.getStyle().tooltips();
			tooltips = HoverTooltips.create((Object[]) styled.tooltips());
		}

		event.hoverTooltips = tooltips.append(Component.literal(file.getAbsolutePath()));
	}

	private static boolean hasConflict(Editor editor, File file) {
		var window = editor.getWindow();
		var project = editor.getCurrentProject();

		if (window == null || project == null) {
			return false;
		}

		for (var other : window.getEditors().keySet()) {
			var otherProject = other.getCurrentProject();

			if (other == editor || unsupported(other) || otherProject == null) {
				continue;
			}

			var otherFile = other.getCurrentProjectFile();

			if (otherFile == null || !otherProject.getName().equals(project.getName())) {
				continue;
			}

			var equalName = File.separatorChar == '\\' ? otherFile.getName().equalsIgnoreCase(file.getName())
					: otherFile.getName().equals(file.getName());

			if (equalName) {
				return true;
			}
		}

		return false;
	}

	/// 过滤不适用特效工程与独立模型工程共享标题设置的编辑器。
	private static boolean unsupported(Editor editor) {
		return !(editor instanceof FXEditor);
	}

	/// 模型工程名只移除约定的完整工程后缀，保留名称中的其他点号。
	private static String modelName(String filename) {
		return filename.endsWith(PhotonModelEditor.PROJECT_SUFFIX)
				? filename.substring(0, filename.length() - PhotonModelEditor.PROJECT_SUFFIX.length()) : filename;
	}

	private static Component replacePath(Component original, String originalPath, String displayedPath) {
		var result = original.copy();
		var siblings = result.getSiblings();
		var pathContents = Component.literal(originalPath).getContents();

		for (int index = siblings.size() - 1; index >= 0; index--) {
			var sibling = siblings.get(index);

			if (!sibling.getContents().equals(pathContents)) {
				continue;
			}

			var replacement = Component.literal(displayedPath).setStyle(sibling.getStyle());
			sibling.getSiblings().forEach(replacement::append);
			siblings.set(index, replacement);
			return result;
		}

		return original;
	}
}
