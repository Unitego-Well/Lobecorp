package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;


import javax.annotation.ParametersAreNonnullByDefault;

/// 与原生入口使用不同窗口身份，标签和恢复流程始终保留扩展编辑器类型。
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PhotonEffectEditor extends FXEditor {
	public static final String COMMAND_NAME = "photon_editor";
	public static final String OPEN_COMMAND = Lobecorp.NAMESPACE + " " + COMMAND_NAME;
	public static final Identifier WINDOW_ID = Lobecorp.id(COMMAND_NAME);

	private final PhotonCompositionWorkspace compositionWorkspace;

	public PhotonEffectEditor() {
		compositionWorkspace = new PhotonCompositionWorkspace(this);
		bottomWindow.getLeftTop().addView(compositionWorkspace);
	}

	@Override
	protected void closeCurrentProject() {
		if (compositionWorkspace != null) {
			compositionWorkspace.projectClosed();
		}
		super.closeCurrentProject();
	}

	@Override
	protected Editor createNewEditorInstance() {
		return new PhotonEffectEditor();
	}

	public static boolean open() {
		var minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return false;
		}

		var existing = EditorWindow.osWindowOf(WINDOW_ID);
		if (existing != null && existing.isOpen()) {
			return existing.getEditorWindow().dockIntoGameWindow();
		}

		EditorWindow.open(WINDOW_ID, PhotonEffectEditor::new).hostAsScreen();
		return true;
	}
}
