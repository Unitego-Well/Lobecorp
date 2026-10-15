package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorNotificationUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelExportUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelFileUtil;

import java.io.File;
import java.lang.ref.WeakReference;

@Mixin(FXProject.class)
public abstract class FXProjectMixin {
	@Unique
	private WeakReference<Editor> lobecorp$exportEditor = new WeakReference<>(null);

	@Inject(method = "onLoad", at = @At("RETURN"))
	private void lobecorp$bindExportEditor(Editor editor, CallbackInfo ci) {
		lobecorp$exportEditor = new WeakReference<>(editor);
	}

	@Inject(method = "onClosed", at = @At("RETURN"))
	private void lobecorp$clearExportEditor(Editor editor, CallbackInfo ci) {
		if (lobecorp$exportEditor.get() == editor) {
			lobecorp$exportEditor.clear();
		}
	}

	@Inject(method = "lambda$showExportFxDialog$0", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/fx/FXHelper;clearCache()I", shift = At.Shift.AFTER))
	private void lobecorp$notifyFxExport(File file, CallbackInfo ci) {
		var editor = lobecorp$exportEditor.get();
		if (editor == null || !lobecorp$isExportProject(editor)
				|| !PhotonEditorSettings.of(editor).enabled(PhotonEditorTextUtil.NOTIFICATIONS_FEATURE)) {
			return;
		}

		Dialog.showNotification(PhotonEditorTextUtil.key("export_success"), PhotonEditorNotificationUtil.SUCCESS_DURATION_SECONDS)
				.show(editor);
	}

	@Unique
	@SuppressWarnings("ConstantValue") // Mixin 合并后 this 为 FXProject，模型工程持有该导出委托。
	private boolean lobecorp$isExportProject(Editor editor) {
		var project = editor.getCurrentProject();
		Object delegate = this;
		return project == delegate || project instanceof PhotonModelProject model && model.exportProject() == delegate;
	}

	@ModifyConstant(method = {"showExportFxDialog", "lambda$showExportFxDialog$0"},
			constant = @Constant(stringValue = PhotonModelFileUtil.FX_SUFFIX))
	private String lobecorp$modelDefinitionSuffix(String suffix) {
		return PhotonModelExportUtil.isModelExportProject(this) ? PhotonModelFileUtil.DEFINITION_SUFFIX : suffix;
	}

	@ModifyArg(method = "lambda$showExportFxDialog$0", at = @At(value = "INVOKE",
			target = "Ljava/io/File;<init>(Ljava/io/File;Ljava/lang/String;)V"), index = 1)
	private String lobecorp$modelDefinitionFileName(String name) {
		if (!PhotonModelExportUtil.isModelExportProject(this) || !name.endsWith(PhotonModelFileUtil.FX_SUFFIX)) {
			return name;
		}

		return name.substring(0, name.length() - PhotonModelFileUtil.FX_SUFFIX.length()) + PhotonModelFileUtil.DEFINITION_SUFFIX;
	}

	@ModifyConstant(method = {"showExportFxPackDialog", "lambda$showExportFxPackDialog$0", "showManageFxPackDialog", "exportIntoFxPack"},
			constant = @Constant(stringValue = PhotonModelFileUtil.FXPACK_SUFFIX))
	private String lobecorp$modelPackSuffix(String suffix) {
		return PhotonModelExportUtil.isModelExportProject(this) ? PhotonModelFileUtil.PACK_SUFFIX : suffix;
	}

	@ModifyArg(method = "lambda$showExportFxPackDialog$0", at = @At(value = "INVOKE",
			target = "Ljava/io/File;<init>(Ljava/io/File;Ljava/lang/String;)V"), index = 1)
	private String lobecorp$modelPackFileName(String name) {
		if (!PhotonModelExportUtil.isModelExportProject(this) || !name.endsWith(PhotonModelFileUtil.FXPACK_SUFFIX)) {
			return name;
		}

		return name.substring(0, name.length() - PhotonModelFileUtil.FXPACK_SUFFIX.length()) + PhotonModelFileUtil.PACK_SUFFIX;
	}

	@ModifyArg(method = "showFxNameDialog", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;stringEditorDialog(Ljava/lang/String;Ljava/lang/String;Ljava/util/function/Predicate;Ljava/util/function/Consumer;)Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;"), index = 1)
	private String lobecorp$modelExportName(String initial) {
		var editor = lobecorp$exportEditor.get();
		return editor != null && lobecorp$isExportProject(editor)
				&& editor.getCurrentProject() instanceof PhotonModelProject model ? model.displayModelName() : initial;
	}

	@WrapOperation(method = "exportIntoFxPack", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;showNotification(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Runnable;)Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;", ordinal = 1))
	private Dialog lobecorp$notifyPackExport(String title, String info, Runnable onClosed, Operation<Dialog> original) {
		var editor = lobecorp$exportEditor.get();
		if (editor == null || !PhotonEditorSettings.of(editor).enabled(PhotonEditorTextUtil.NOTIFICATIONS_FEATURE)) {
			return original.call(title, info, onClosed);
		}

		var text = Component.translatable(PhotonEditorTextUtil.key("export_success_detail"), info).getString();
		return Dialog.showNotification(text, PhotonEditorNotificationUtil.SUCCESS_DURATION_SECONDS);
	}
}
