package org.unitego.lobecorp.registry.photon.client;

import com.lowdragmc.photon.gui.editor.FXEditor;
import com.mojang.serialization.MapCodec;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.config.photon.PhotonEditorShortcutSettings;
import org.unitego.lobecorp.config.photon.PhotonEditorCategorySettings;

public class LcPhotonEditorSettings {
	public static void register(FXEditor editor) {
		editor.editorSettings.registerSettings(new PhotonEditorSettings(), PhotonEditorSettings.CODEC);
		for (var category : PhotonEditorSettings.Category.values()) {
			var settings = new PhotonEditorCategorySettings(editor, category);
			editor.editorSettings.registerSettings(settings, MapCodec.unit(settings).codec());
		}
		var shortcuts = new PhotonEditorShortcutSettings(editor);
		editor.editorSettings.registerSettings(shortcuts, MapCodec.unit(shortcuts).codec());
	}
}
