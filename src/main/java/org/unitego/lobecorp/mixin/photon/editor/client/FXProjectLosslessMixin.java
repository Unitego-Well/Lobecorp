package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
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
import org.unitego.lobecorp.client.photon.editor.PhotonProjectRawAccess;
import org.unitego.lobecorp.client.photon.playback.PhotonProjectPreviewAccess;
import org.unitego.lobecorp.util.photon.compat.PhotonLosslessNbtUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonProjectNbtUtil;
import org.unitego.lobecorp.util.photon.playback.PhotonProjectPreviewUtil;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

/// 当前FX协议仍由原生序列化；原树只承接已知模型的变化，不通过重建摘要覆盖文件。
@Mixin(FXProject.class)
public abstract class FXProjectLosslessMixin implements IProject, PhotonProjectRawAccess {
	@Unique
	private CompoundTag lobecorp$rawRoot = new CompoundTag();
	@Unique
	private CompoundTag lobecorp$rawData = new CompoundTag();
	@Unique
	private CompoundTag lobecorp$knownBefore = new CompoundTag();

	@Override
	public CompoundTag lobecorp$rawContainer() {
		return lobecorp$rawRoot.copy();
	}

	@Override
	public void lobecorp$rawContainer(CompoundTag source) {
		lobecorp$rawRoot = source.copy();
	}

	@Inject(method = "deserialize", at = @At("HEAD"))
	private void lobecorp$captureContainer(ValueInput input, CallbackInfo ci) {
		lobecorp$rawContainer(PhotonProjectNbtUtil.read(input));
	}

	@Inject(method = "deserializeProject", at = @At("HEAD"))
	private void lobecorp$captureData(ValueInput input, CallbackInfo ci) {
		lobecorp$rawData = PhotonProjectNbtUtil.read(input);
	}

	@Inject(method = "deserializeProject", at = @At("RETURN"))
	private void lobecorp$captureKnown(ValueInput input, CallbackInfo ci) {
		lobecorp$knownBefore = lobecorp$currentData();
		var preview = input.read(PhotonProjectPreviewUtil.PROJECT_FIELD, ExtraCodecs.NBT).orElse(null);
		lobecorp$knownBefore.remove(PhotonProjectPreviewUtil.PROJECT_FIELD);
		if (preview != null) {
			lobecorp$knownBefore.put(PhotonProjectPreviewUtil.PROJECT_FIELD, preview.copy());
		}
	}

	@Inject(method = "serializeProject", at = @At("RETURN"))
	private void lobecorp$preserveData(ValueOutput output, CallbackInfo ci) {
		var saved = PhotonLosslessNbtUtil.changed(lobecorp$rawData, lobecorp$knownBefore, lobecorp$currentData());
		for (var entry : saved.entrySet()) {
			output.store(entry.getKey(), ExtraCodecs.NBT, entry.getValue().copy());
		}
	}

	@Unique
	private CompoundTag lobecorp$currentData() {
		var result = new CompoundTag();
		result.put("fx", ((FXProject) (Object) this).getFx().serializeNBT(Platform.getFrozenRegistry()));
		var preview = ((PhotonProjectPreviewAccess) this).lobecorp$projectPreview();
		if (preview != null) {
			result.put(PhotonProjectPreviewUtil.PROJECT_FIELD, preview);
		}
		return result;
	}

	@Inject(method = "serializeMetadata", at = @At("RETURN"))
	private void lobecorp$preserveMetadata(ValueOutput output, CallbackInfo ci) {
		var known = Set.of("version", "suffix", "name", "version_num");
		for (var entry : lobecorp$rawContainer().getCompoundOrEmpty("meta").entrySet()) {
			if (!known.contains(entry.getKey())) {
				output.store(entry.getKey(), ExtraCodecs.NBT, entry.getValue().copy());
			}
		}
	}

	/// 目标只继承IProject默认实现；新增覆盖继续调用原默认过程，再补容器的非协议字段。
	@Override
	public void serialize(@NotNull ValueOutput output) {
		IProject.super.serialize(output);
		for (var entry : lobecorp$rawContainer().entrySet()) {
			if (!entry.getKey().equals("data") && !entry.getKey().equals("meta")) {
				output.store(entry.getKey(), ExtraCodecs.NBT, entry.getValue().copy());
			}
		}
	}
}
