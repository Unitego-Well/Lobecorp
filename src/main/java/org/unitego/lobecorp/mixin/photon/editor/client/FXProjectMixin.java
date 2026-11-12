package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.gui.editor.FXProject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonCompositionProjectAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonProjectPreviewAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaserComposition;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;

import javax.annotation.Nullable;


@Mixin(FXProject.class)
public abstract class FXProjectMixin implements PhotonProjectPreviewAccess, PhotonCompositionProjectAccess {
	@Unique
	private CompoundTag lobecorp$composition;
	@Unique
	private @Nullable CompoundTag lobecorp$projectPreview;

	@Override
	public CompoundTag lobecorp$composition() {
		return lobecorp$composition == null ? null : lobecorp$composition.copy();
	}

	@Override
	public void lobecorp$composition(CompoundTag value) {
		if (value != null) {
			PhotonOwnedLaserComposition.deserialize(value.getCompoundOrEmpty("definition"));
		}
		lobecorp$composition = value == null ? null : value.copy();
	}

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
		if (lobecorp$composition != null) {
			output.store("lobecorp_owned_composition", ExtraCodecs.NBT, lobecorp$composition.copy());
		}
		if (lobecorp$projectPreview != null) {
			output.store(PhotonProjectPreviewUtil.PROJECT_FIELD, ExtraCodecs.NBT, lobecorp$projectPreview.copy());
		}
	}

	@Inject(method = "deserialize", at = @At("HEAD"))
	private void lobecorp$readProjectExtensions(ValueInput input, CallbackInfo ci) {
		input.child("data").ifPresent(data -> lobecorp$readProjectPreview(data, ci));
	}

	@Inject(method = "deserializeProject", at = @At("RETURN"))
	private void lobecorp$readProjectPreview(ValueInput input, CallbackInfo ci) {
		var composition = input.read("lobecorp_owned_composition", ExtraCodecs.NBT);
		if (composition.isPresent() && !(composition.get() instanceof CompoundTag)) {
			throw new IllegalArgumentException("组合作者定义必须为NBT复合标签。");
		}
		lobecorp$composition(composition.orElse(null) instanceof CompoundTag tag ? tag : null);
		var saved = input.read(PhotonProjectPreviewUtil.PROJECT_FIELD, ExtraCodecs.NBT);

		if (saved.isPresent() && !(saved.get() instanceof CompoundTag)) {
			throw new IllegalArgumentException("工程测试定义必须为NBT复合标签。");
		}

		lobecorp$projectPreview(saved.orElse(null) instanceof CompoundTag tag ? tag : null);
	}

}
