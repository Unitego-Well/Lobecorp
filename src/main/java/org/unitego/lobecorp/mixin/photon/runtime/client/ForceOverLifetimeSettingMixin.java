package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.ForceOverLifetimeSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.runtime.PhotonLifetimeCycleAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;
import org.unitego.lobecorp.util.TranslationKeys;

@Mixin(ForceOverLifetimeSetting.class)
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "unused"})
public abstract class ForceOverLifetimeSettingMixin implements PhotonLifetimeCycleAccess, PhotonCycleOffsetAccess, IToggleConfigurable {
	@Unique
	@Configurable(name = TranslationKeys.PHOTON_LIFETIME_CYCLE_ENABLED_KEY)
	private boolean lobecorp$lifetimeCycleEnabled;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_LIFETIME_CYCLE_TICKS_KEY)
	@ConfigNumber(range = {1, Integer.MAX_VALUE})
	private int lobecorp$lifetimeCycleTicks = PhotonLifetimeCycleUtil.DEFAULT_CYCLE_TICKS;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetX;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetY;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetZ;

	@Override
	public boolean lobecorp$isLifetimeCycleEnabled() {
		return this.lobecorp$lifetimeCycleEnabled;
	}

	@Override
	public int lobecorp$getLifetimeCycleTicks() {
		return this.lobecorp$lifetimeCycleTicks;
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		IToggleConfigurable.super.buildConfigurator(father);
		father.addConfigurators(PhotonLifetimeCycleUtil.offsetConfigurator(TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_AXES_KEY,
				() -> new NumberFunction3(this.lobecorp$cycleOffsetX, this.lobecorp$cycleOffsetY, this.lobecorp$cycleOffsetZ),
				value -> {
					this.lobecorp$cycleOffsetX = value.x.get(0, () -> 0f).floatValue();
					this.lobecorp$cycleOffsetY = value.y.get(0, () -> 0f).floatValue();
					this.lobecorp$cycleOffsetZ = value.z.get(0, () -> 0f).floatValue();
				}, this));
	}

	@Override
	public float lobecorp$getCycleOffset(Component component) {
		return switch (component) {
			case X -> this.lobecorp$cycleOffsetX;
			case Y -> this.lobecorp$cycleOffsetY;
			case Z -> this.lobecorp$cycleOffsetZ;
			default -> 0;
		};
	}
}
