package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.ColorOverLifetimeSetting;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess.Component;
import org.joml.Vector4f;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;

@Mixin(ColorOverLifetimeSetting.Runtime.class)
public abstract class ColorOverLifetimeRuntimeMixin {
	@Shadow
	@Final
	private ColorOverLifetimeSetting config;

	@Shadow
	public abstract boolean isEnable();

	@Shadow
	@Final
	public RuntimeValue<NumberFunction> color;

	@ModifyReturnValue(method = "getColor", at = @At("RETURN"))
	private Vector4f lobecorp$offsetOutput(Vector4f original, IParticle particle, float partialTicks) {
		if (!PhotonLifetimeCycleUtil.isLooping(this.config, isEnable(), particle))
			return original;
		return original.set(
				lobecorp$sampleColor(particle, partialTicks, Component.RED, 16),
				lobecorp$sampleColor(particle, partialTicks, Component.GREEN, 8),
				lobecorp$sampleColor(particle, partialTicks, Component.BLUE, 0),
				lobecorp$sampleColor(particle, partialTicks, Component.ALPHA, 24));
	}

	@Unique
	private float lobecorp$sampleColor(IParticle particle, float partialTicks, Component component, int shift) {
		float time = PhotonLifetimeCycleUtil.sampleComponent(0, particle, partialTicks, this.config, true, component);
		return (color.get().get(time, () -> particle.getMemRandom(this)).intValue() >> shift & 0xff) / 255f;
	}

	@ModifyExpressionValue(method = "getColor", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/particle/IParticle;getT(F)F"))
	private float lobecorp$sampleLifetime(float original, IParticle particle, float partialTicks) {
		return PhotonLifetimeCycleUtil.sample(original, particle, partialTicks, this.config, isEnable());
	}
}
