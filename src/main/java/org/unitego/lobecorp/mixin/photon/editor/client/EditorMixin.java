package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.registry.photon.client.LcPhotonEditorSettings;

@Mixin(Editor.class)
public abstract class EditorMixin {
	@Inject(method = "initEditorSettings", at = @At("RETURN"))
	private void lobecorp$registerPhotonSettings(CallbackInfo ci) {
		if ((Object) this instanceof FXEditor editor) {
			LcPhotonEditorSettings.register(editor);
		}
	}
}
