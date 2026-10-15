package org.unitego.lobecorp.config.photon;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.editor.settings.Settings;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;

@SuppressWarnings("FieldMayBeFinal")
public class PhotonEditorShortcutSettings implements Settings {
	/// 保留原设置 ID 与扩展总开关；绑定数据由原生 Keymap 保存。
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
		return "Keymap";
	}

	@Override
	public void onApply(Editor editor) {
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		PhotonEditorSettings.of(editor).buildShortcuts(father);
	}
}
