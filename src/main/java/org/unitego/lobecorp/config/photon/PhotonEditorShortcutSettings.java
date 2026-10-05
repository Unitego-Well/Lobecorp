package org.unitego.lobecorp.config.photon;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.editor.settings.Settings;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;

@SuppressWarnings("FieldMayBeFinal")
public class PhotonEditorShortcutSettings implements Settings {
	/**
	 * 快捷键分类 ID；绑定数据由 PhotonEditorSettings 保存。
	 */
	public static final Identifier ID = Lobecorp.id("photon_editor_shortcuts");
	private Editor editor;

	public PhotonEditorShortcutSettings(Editor editor) {
		this.editor = editor;
	}

	@Override
	public Identifier getId() {
		return ID;
	}

	@Override
	public String getPath() {
		return "Lobecorp.lobecorp_photon_shortcuts";
	}

	@Override
	public void onApply(Editor editor) {
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		PhotonEditorSettings.of(editor).buildShortcuts(father);
	}
}
