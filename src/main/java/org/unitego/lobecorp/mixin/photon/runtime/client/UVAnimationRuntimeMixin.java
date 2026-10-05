package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.UVAnimationSetting;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;

@Mixin(UVAnimationSetting.Runtime.class)
public abstract class UVAnimationRuntimeMixin {
	@Shadow
	@Final
	private UVAnimationSetting config;

	@Shadow
	public abstract boolean isEnable();

	@ModifyExpressionValue(method = "getUVs", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/particle/IParticle;getT(F)F"))
	private float lobecorp$sampleUV(float original, IParticle particle, float partialTicks) {
		return PhotonLifetimeCycleUtil.sampleUV(original, particle, partialTicks, this.config, isEnable());
	}
}
