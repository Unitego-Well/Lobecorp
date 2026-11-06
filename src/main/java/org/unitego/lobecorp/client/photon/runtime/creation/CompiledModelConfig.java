package org.unitego.lobecorp.client.photon.runtime.creation;

import net.minecraft.nbt.Tag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.util.photon.creation.PhotonFrozenTag;

import java.util.Objects;

/// 实例仅共享私有资产快照；其余注解字段由构造计划恢复为独占配置。
public class CompiledModelConfig extends PhotonModelOutputConfig {
	private PhotonFrozenTag frozenSnapshot;

	public CompiledModelConfig(PhotonFrozenTag snapshot) {
		super();
		frozenSnapshot = Objects.requireNonNull(snapshot);
	}

	public PhotonFrozenTag sharedAsset() {
		return frozenSnapshot;
	}

	public void restoreSnapshot(PhotonFrozenTag snapshot) {
		frozenSnapshot = Objects.requireNonNull(snapshot);
	}

	@Override
	public Tag assetSnapshot() {
		return frozenSnapshot.copy();
	}

	@Override
	public void setAssetSnapshot(Tag value) {
		super.setAssetSnapshot(value);
		frozenSnapshot = new PhotonFrozenTag(value);
	}

	@Override
	public void serialize(@NotNull ValueOutput output) {
		super.serialize(output);

		output.store(ASSET_KEY, ExtraCodecs.NBT, frozenSnapshot.copy());
	}

	@Override
	public void deserialize(@NotNull ValueInput input) {
		super.deserialize(input);
	}
}
