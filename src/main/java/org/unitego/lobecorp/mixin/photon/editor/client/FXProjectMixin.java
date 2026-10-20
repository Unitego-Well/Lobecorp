package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import javax.annotation.Nullable;
import org.unitego.lobecorp.client.photon.playback.PhotonProjectPreviewAccess;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorNotificationUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;

import java.io.File;
import java.lang.ref.WeakReference;

@Mixin(FXProject.class)
public abstract class FXProjectMixin implements PhotonProjectPreviewAccess {
	@Unique
	private @Nullable CompoundTag lobecorp$projectPreview;

	@Override
	public @Nullable CompoundTag lobecorp$projectPreview() {
		return lobecorp$projectPreview == null ? null : lobecorp$projectPreview.copy();
	}

	@Override
	public void lobecorp$projectPreview(@Nullable CompoundTag source) {
		lobecorp$projectPreview = source == null ? null : PhotonProjectPreviewUtil.checkedCopy(source);
	}

	@Inject(method = "serializeProject", at = @At("RETURN"))
	private void lobecorp$storeProjectPreview(ValueOutput output, CallbackInfo ci) {
		if (lobecorp$projectPreview != null) {
			output.store(PhotonProjectPreviewUtil.PROJECT_FIELD, ExtraCodecs.NBT, lobecorp$projectPreview.copy());
		}
	}

	@Inject(method = "deserializeProject", at = @At("RETURN"))
	private void lobecorp$readProjectPreview(ValueInput input, CallbackInfo ci) {
		var saved = input.read(PhotonProjectPreviewUtil.PROJECT_FIELD, ExtraCodecs.NBT);

		if (saved.isPresent() && !(saved.get() instanceof CompoundTag)) {
			throw new IllegalArgumentException("工程测试定义必须为NBT复合标签。");
		}

		lobecorp$projectPreview(saved.orElse(null) instanceof CompoundTag tag ? tag : null);
	}

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
		if (!(editor instanceof PhotonEffectEditor) || !lobecorp$isExportProject(editor)) {
			return;
		}

		Dialog.showNotification(PhotonEditorTextUtil.key("export_success"), PhotonEditorNotificationUtil.SUCCESS_DURATION_SECONDS)
				.show(editor);
	}

	@Unique
	private boolean lobecorp$isExportProject(Editor editor) {
		var project = editor.getCurrentProject();
		Object delegate = this;
		return project == delegate;
	}






	@WrapOperation(method = "exportIntoFxPack", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;showNotification(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Runnable;)Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;", ordinal = 1))
	private Dialog lobecorp$notifyPackExport(String title, String info, Runnable onClosed, Operation<Dialog> original) {
		var editor = lobecorp$exportEditor.get();
		if (!(editor instanceof PhotonEffectEditor)) {
			return original.call(title, info, onClosed);
		}

		var text = Component.translatable(PhotonEditorTextUtil.key("export_success_detail"), info).getString();
		return Dialog.showNotification(text, PhotonEditorNotificationUtil.SUCCESS_DURATION_SECONDS);
	}
}
