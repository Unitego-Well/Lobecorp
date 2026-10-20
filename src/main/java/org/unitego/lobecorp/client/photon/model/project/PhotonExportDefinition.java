package org.unitego.lobecorp.client.photon.model.project;

import com.lowdragmc.photon.client.fx.FX;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.unitego.lobecorp.util.photon.compat.PhotonLosslessNbtUtil;

import javax.annotation.ParametersAreNonnullByDefault;

import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;

/// 导出副本在已知模型上展开依赖，同时保留工程中对应FX原树的未知数据。
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PhotonExportDefinition extends FX {
	private final CompoundTag original;
	private final CompoundTag baseline;

	public PhotonExportDefinition(CompoundTag source, HolderLookup.Provider provider) {
		original = source.copy();
		super.deserializeNBT(provider, source.copy());
		baseline = super.serializeNBT(provider);
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		return PhotonLosslessNbtUtil.changed(original, baseline, super.serializeNBT(provider));
	}
}
