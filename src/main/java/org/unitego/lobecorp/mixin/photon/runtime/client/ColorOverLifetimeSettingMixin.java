package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.photon.client.gameobject.emitter.data.ColorOverLifetimeSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.runtime.PhotonLifetimeCycleAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;
import org.unitego.lobecorp.util.TranslationKeys;

@Mixin(ColorOverLifetimeSetting.class)
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "unused"})
public abstract class ColorOverLifetimeSettingMixin implements PhotonLifetimeCycleAccess, PhotonCycleOffsetAccess {
	@Unique
	@Configurable(name = TranslationKeys.PHOTON_LIFETIME_CYCLE_ENABLED_KEY)
	private boolean lobecorp$lifetimeCycleEnabled;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_LIFETIME_CYCLE_TICKS_KEY)
	@ConfigNumber(range = {1, Integer.MAX_VALUE})
	private int lobecorp$lifetimeCycleTicks = PhotonLifetimeCycleUtil.DEFAULT_CYCLE_TICKS;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_CYCLE_OFFSET_RED_KEY)
	@ConfigNumber(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
	private float lobecorp$cycleOffsetRed;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_CYCLE_OFFSET_GREEN_KEY)
	@ConfigNumber(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
	private float lobecorp$cycleOffsetGreen;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_CYCLE_OFFSET_BLUE_KEY)
	@ConfigNumber(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
	private float lobecorp$cycleOffsetBlue;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_CYCLE_OFFSET_ALPHA_KEY)
	@ConfigNumber(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
	private float lobecorp$cycleOffsetAlpha;

	@Override
	public boolean lobecorp$isLifetimeCycleEnabled() {
		return this.lobecorp$lifetimeCycleEnabled;
	}

	@Override
	public int lobecorp$getLifetimeCycleTicks() {
		return this.lobecorp$lifetimeCycleTicks;
	}

	@Override
	public float lobecorp$getCycleOffset(Component component) {
		return switch (component) {
			case RED -> this.lobecorp$cycleOffsetRed;
			case GREEN -> this.lobecorp$cycleOffsetGreen;
			case BLUE -> this.lobecorp$cycleOffsetBlue;
			case ALPHA -> this.lobecorp$cycleOffsetAlpha;
			default -> 0;
		};
	}
}
