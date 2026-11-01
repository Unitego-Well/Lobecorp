package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.gui.editor.FXProject;
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

	@Inject(method = "deserialize", at = @At("HEAD"))
	private void lobecorp$readRootPreview(ValueInput input, CallbackInfo ci) {
		input.child("data").ifPresent(data -> lobecorp$readProjectPreview(data, ci));
	}

	@Inject(method = "deserializeProject", at = @At("RETURN"))
	private void lobecorp$readProjectPreview(ValueInput input, CallbackInfo ci) {
		var saved = input.read(PhotonProjectPreviewUtil.PROJECT_FIELD, ExtraCodecs.NBT);

		if (saved.isPresent() && !(saved.get() instanceof CompoundTag)) {
			throw new IllegalArgumentException("工程测试定义必须为NBT复合标签。");
		}

		lobecorp$projectPreview(saved.orElse(null) instanceof CompoundTag tag ? tag : null);
	}

}
