package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import net.minecraft.network.chat.Component;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslationKeys;

import java.io.File;

public class PhotonEditorTitleUtil {

	public static Component title(Editor editor, Component original) {
		var file = editor.getCurrentProjectFile();

		if (unsupported(editor) || editor.getCurrentProject() == null) {
			return original;
		}

		if (file == null) {
			return original.copy().append(" - ").append(Component.translatable(PhotonEditorTranslationKeys.UNNAMED_EFFECT));
		}

		var path = file.getName();

		if (hasConflict(editor, file)) {
			path = file.getAbsolutePath();
		}


		return replacePath(original, file.getPath(), path);
	}

	public static void appendPathTooltip(Editor editor, UIEvent event) {
		var file = editor.getCurrentProjectFile();

		if (unsupported(editor) || file == null) {
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

	/// 仅扩展特效编辑器使用这组标题设置。
	private static boolean unsupported(Editor editor) {
		return !(editor instanceof PhotonEffectEditor);
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
