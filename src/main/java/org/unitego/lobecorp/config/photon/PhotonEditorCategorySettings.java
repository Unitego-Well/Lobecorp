package org.unitego.lobecorp.config.photon;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.editor.settings.Settings;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.Lobecorp;

public class PhotonEditorCategorySettings implements Settings {
	private final Editor editor;
	private final PhotonEditorSettings.Category category;

	public PhotonEditorCategorySettings(Editor editor, PhotonEditorSettings.Category category) {
		this.editor = editor;
		this.category = category;
	}

	@Override
	public Identifier getId() {
		return Lobecorp.id("photon_editor_" + category.id());
	}

	@Override
	public String getPath() {
		return "Lobecorp.lobecorp_photon_" + category.id();
	}

	@Override
	public void onApply(Editor editor) {
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		PhotonEditorSettings.of(editor).buildCategory(father, category);
	}
}

