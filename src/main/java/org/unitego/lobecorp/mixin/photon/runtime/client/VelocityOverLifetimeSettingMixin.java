package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.configurator.IToggleConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.VelocityOverLifetimeSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.runtime.PhotonLifetimeCycleAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;
import org.unitego.lobecorp.util.TranslationKeys;

@Mixin(VelocityOverLifetimeSetting.class)
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "unused"})
public abstract class VelocityOverLifetimeSettingMixin implements PhotonLifetimeCycleAccess, PhotonCycleOffsetAccess, IToggleConfigurable {
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

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetOrbitalX;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetOrbitalY;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetOrbitalZ;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetCenterX;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetCenterY;

	@Unique
	@Persisted
	private float lobecorp$cycleOffsetCenterZ;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_CYCLE_OFFSET_RADIAL_KEY)
	@ConfigNumber(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
	private float lobecorp$cycleOffsetRadial;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_CYCLE_OFFSET_MULTIPLIER_KEY)
	@ConfigNumber(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
	private float lobecorp$cycleOffsetMultiplier;

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
		father.addConfigurators(PhotonLifetimeCycleUtil.offsetConfigurator(TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_LINEAR_KEY,
				() -> new NumberFunction3(this.lobecorp$cycleOffsetX, this.lobecorp$cycleOffsetY, this.lobecorp$cycleOffsetZ),
				value -> {
					this.lobecorp$cycleOffsetX = value.x.get(0, () -> 0f).floatValue();
					this.lobecorp$cycleOffsetY = value.y.get(0, () -> 0f).floatValue();
					this.lobecorp$cycleOffsetZ = value.z.get(0, () -> 0f).floatValue();
				}, this));
		father.addConfigurators(PhotonLifetimeCycleUtil.offsetConfigurator(TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_ORBITAL_KEY,
				() -> new NumberFunction3(this.lobecorp$cycleOffsetOrbitalX, this.lobecorp$cycleOffsetOrbitalY, this.lobecorp$cycleOffsetOrbitalZ),
				value -> {
					this.lobecorp$cycleOffsetOrbitalX = value.x.get(0, () -> 0f).floatValue();
					this.lobecorp$cycleOffsetOrbitalY = value.y.get(0, () -> 0f).floatValue();
					this.lobecorp$cycleOffsetOrbitalZ = value.z.get(0, () -> 0f).floatValue();
				}, this));
		father.addConfigurators(PhotonLifetimeCycleUtil.offsetConfigurator(TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_CENTER_KEY,
				() -> new NumberFunction3(this.lobecorp$cycleOffsetCenterX, this.lobecorp$cycleOffsetCenterY, this.lobecorp$cycleOffsetCenterZ),
				value -> {
					this.lobecorp$cycleOffsetCenterX = value.x.get(0, () -> 0f).floatValue();
					this.lobecorp$cycleOffsetCenterY = value.y.get(0, () -> 0f).floatValue();
					this.lobecorp$cycleOffsetCenterZ = value.z.get(0, () -> 0f).floatValue();
				}, this));
	}

	@Override
	public float lobecorp$getCycleOffset(Component component) {
		return switch (component) {
			case X -> this.lobecorp$cycleOffsetX;
			case Y -> this.lobecorp$cycleOffsetY;
			case Z -> this.lobecorp$cycleOffsetZ;
			case ORBITAL_X -> this.lobecorp$cycleOffsetOrbitalX;
			case ORBITAL_Y -> this.lobecorp$cycleOffsetOrbitalY;
			case ORBITAL_Z -> this.lobecorp$cycleOffsetOrbitalZ;
			case CENTER_X -> this.lobecorp$cycleOffsetCenterX;
			case CENTER_Y -> this.lobecorp$cycleOffsetCenterY;
			case CENTER_Z -> this.lobecorp$cycleOffsetCenterZ;
			case RADIAL -> this.lobecorp$cycleOffsetRadial;
			case MULTIPLIER -> this.lobecorp$cycleOffsetMultiplier;
			default -> 0;
		};
	}
}
