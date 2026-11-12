package org.unitego.lobecorp.registry.client.photon;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.config.photon.VisualQualitySettings;

public class LcPhotonEditorSettings {
	public static void register(FXEditor editor) {
		register((Editor) editor);
	}

	/// 仅给扩展特效编辑器登记共享设置。
	public static void register(Editor editor) {
		if (!PhotonEditorTools.isPhotonEditor(editor)) {
			return;
		}

		editor.editorSettings.registerSettings(new PhotonEditorSettings(), PhotonEditorSettings.CODEC);
		editor.editorSettings.registerSettings(new VisualQualitySettings(), VisualQualitySettings.CODEC);
	}
}
