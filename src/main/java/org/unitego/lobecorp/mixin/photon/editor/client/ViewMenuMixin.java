package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.editor.settings.EditorSettings;
import com.lowdragmc.lowdraglib2.editor.ui.menu.ViewMenu;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorViewScaleUtil;

@Mixin(ViewMenu.class)
public abstract class ViewMenuMixin {

	@WrapOperation(method = "lambda$createDefaultMenu$2", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/editor/settings/EditorSettings;isDirty()Z"))
	private boolean lobecorp$applySelectedViewScale(EditorSettings settings, Operation<Boolean> original) {
		return settings.editor instanceof FXEditor || original.call(settings);
	}

	@WrapOperation(method = "lambda$createDefaultMenu$2", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/editor/settings/EditorSettings;applyCurrentSettings()V"))
	private void lobecorp$applyViewScale(EditorSettings settings, Operation<Void> original) {
		PhotonEditorViewScaleUtil.applyFromViewMenu(settings, () -> original.call(settings));
	}
}
