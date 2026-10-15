package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationProjectAccess;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationMigrationGuardUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationProjectPreviewUtil;

import java.io.File;
import java.util.Objects;

/// 导出对话框和最终 FXPack 写入均检查迁移，避免覆盖尚待用户处理的旧规则。
@Mixin(FXProject.class)
public abstract class PhotonAnimationProjectMixin implements PhotonAnimationProjectAccess {
	@Unique
	private @Nullable PhotonAnimationDefinition lobecorp$previewDefinition;

	@Override
	public PhotonAnimationDefinition lobecorp$previewDefinition() {
		if (lobecorp$previewDefinition == null) {
			lobecorp$previewDefinition = PhotonAnimationProjectPreviewUtil.create();
		}

		return lobecorp$previewDefinition;
	}

	@Override
	public void lobecorp$setPreviewDefinition(PhotonAnimationDefinition definition) {
		lobecorp$previewDefinition = Objects.requireNonNull(definition);
	}

	@Inject(method = "serializeProject", at = @At("RETURN"))
	private void lobecorp$savePreview(ValueOutput output, CallbackInfo ci) {
		if (lobecorp$previewDefinition == null) {
			return;
		}

		output.store(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD, ExtraCodecs.NBT,
				PhotonAnimationProjectPreviewUtil.write(Platform.getFrozenRegistry(), lobecorp$previewDefinition));
	}

	@Inject(method = "deserializeProject", at = @At("RETURN"))
	private void lobecorp$loadPreview(ValueInput input, CallbackInfo ci) {
		lobecorp$previewDefinition = input.read(PhotonAnimationProjectPreviewUtil.PROJECT_FIELD, ExtraCodecs.NBT)
				.orElse(null) instanceof CompoundTag tag
				? PhotonAnimationProjectPreviewUtil.read(Platform.getFrozenRegistry(), tag) : null;
	}

	@Inject(method = {"showExportFxDialog", "showExportFxPackDialog"}, at = @At("HEAD"), cancellable = true)
	private void lobecorp$guardExportDialog(Editor editor, CallbackInfo ci) {
		if (PhotonAnimationMigrationGuardUtil.rejectExport(editor, (FXProject) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "exportIntoFxPack", at = @At("HEAD"), cancellable = true)
	private void lobecorp$guardPackWrite(Editor editor, File fxpackFile, String fxName, CallbackInfo ci) {
		if (PhotonAnimationMigrationGuardUtil.rejectExport(editor, (FXProject) (Object) this)) {
			ci.cancel();
		}
	}
}
