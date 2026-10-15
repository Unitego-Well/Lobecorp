package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.File;

@Mixin(Editor.class)
@SuppressWarnings("UnnecessaryModifier")
public interface PhotonEffectEditorAccessor {
	@Accessor("currentProjectFile")
	public void lobecorp$setProjectFile(File file);

	@Invoker("recordRecentProject")
	public void lobecorp$recordRecentProject();
}
