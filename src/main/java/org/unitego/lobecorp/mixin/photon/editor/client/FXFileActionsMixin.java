package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.gui.editor.FXFileActions;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.registry.translation.photon.editor.PhotonEditorTranslationKeys;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorNotificationUtil;

import java.io.File;

@Mixin(FXFileActions.class)
public abstract class FXFileActionsMixin {
	@Inject(method = "lambda$showExportFxDialog$0", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FXHelper;clearCache()I", shift = At.Shift.AFTER))
	private static void lobecorp$notifyFxExport(Editor editor, FXProject project, FX fx, File file, CallbackInfo ci) {
		if (!(editor instanceof PhotonEffectEditor) || editor.getCurrentProject() != project) {
			return;
		}

		Dialog.showNotification(PhotonEditorTranslationKeys.EXPORT_SUCCESS, PhotonEditorNotificationUtil.SUCCESS_DURATION_SECONDS)
				.show(editor);
	}

	@Inject(method = "reportResolution", at = @At("HEAD"))
	private static void lobecorp$notifyPackExport(Editor editor, File packFile, Identifier fxId, Component message, CallbackInfo ci) {
		if (!(editor instanceof PhotonEffectEditor)) {
			return;
		}

		var text = Component.translatable(PhotonEditorTranslationKeys.EXPORT_SUCCESS_DETAIL, message.getString()).getString();
		Dialog.showNotification(text, PhotonEditorNotificationUtil.SUCCESS_DURATION_SECONDS).show(editor);
	}
}
