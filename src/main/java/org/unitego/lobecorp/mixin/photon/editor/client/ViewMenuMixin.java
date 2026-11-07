package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.editor.settings.EditorSettings;
import com.lowdragmc.lowdraglib2.editor.ui.menu.ViewMenu;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorViewScaleUtil;

@Mixin(ViewMenu.class)
public abstract class ViewMenuMixin {

	@WrapOperation(method = "lambda$addScaleLeaf$1", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/editor/settings/EditorSettings;isDirty()Z"))
	private boolean lobecorp$applySelectedViewScale(EditorSettings settings, Operation<Boolean> original) {
		return settings.editor instanceof PhotonEffectEditor || original.call(settings);
	}

	@WrapOperation(method = "lambda$addScaleLeaf$1", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/editor/settings/EditorSettings;applyCurrentSettings()V"))
	private void lobecorp$applyViewScale(EditorSettings settings, Operation<Void> original) {
		PhotonEditorViewScaleUtil.applyFromViewMenu(settings, () -> original.call(settings));
	}
}
