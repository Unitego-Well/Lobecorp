package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.photon.client.gameobject.emitter.data.UVAnimationSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.ToggleGroup;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.runtime.PhotonLifetimeCycleAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;
import org.unitego.lobecorp.util.TranslationKeys;

@Mixin(UVAnimationSetting.class)
@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal", "unused"})
public abstract class UVAnimationSettingMixin extends ToggleGroup implements PhotonLifetimeCycleAccess, PhotonCycleOffsetAccess {
	@Unique
	@Configurable(name = TranslationKeys.PHOTON_LIFETIME_CYCLE_ENABLED_KEY)
	private boolean lobecorp$lifetimeCycleEnabled;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_LIFETIME_CYCLE_TICKS_KEY)
	@ConfigNumber(range = {1, Integer.MAX_VALUE})
	private int lobecorp$lifetimeCycleTicks = PhotonLifetimeCycleUtil.DEFAULT_CYCLE_TICKS;

	@Unique
	@Configurable(name = TranslationKeys.PHOTON_CYCLE_TIME_OFFSET_UV_KEY)
	@ConfigNumber(range = {-Float.MAX_VALUE, Float.MAX_VALUE})
	private float lobecorp$cycleOffsetUV;

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
		return component == Component.UV ? this.lobecorp$cycleOffsetUV : 0;
	}

	@ModifyExpressionValue(method = "getUVs", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/particle/IParticle;getT(F)F"))
	private float lobecorp$sampleUV(float original, IParticle particle, float partialTicks) {
		return PhotonLifetimeCycleUtil.sampleUV(original, particle, partialTicks, this, isEnable());
	}
}
