package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.editor.settings.AppearanceSettings;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorViewScaleUtil;

@Mixin(AppearanceSettings.class)
public abstract class AppearanceSettingsMixin {

	@WrapOperation(method = "onApply", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/editor/settings/AppearanceSettings;applyPreferredHost(Lcom/lowdragmc/lowdraglib2/editor/ui/Editor;)V"))
	private void lobecorp$keepViewScaleHost(AppearanceSettings settings, Editor editor, Operation<Void> original) {
		if (PhotonEditorViewScaleUtil.isApplyingViewScale(editor)) {
			return;
		}

		original.call(settings, editor);
	}

	@WrapOperation(method = "onApply", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/editor/ui/EditorWindow;isInOsWindow()Z"))
	private boolean lobecorp$allowViewScale(EditorWindow window, Operation<Boolean> original, Editor editor) {
		return original.call(window) && !PhotonEditorViewScaleUtil.isApplyingViewScale(editor);
	}
}
