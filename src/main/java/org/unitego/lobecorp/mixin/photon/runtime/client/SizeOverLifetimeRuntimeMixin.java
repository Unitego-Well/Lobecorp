package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;

import java.util.function.Supplier;

import com.lowdragmc.photon.client.gameobject.emitter.data.SizeOverLifetimeSetting;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess.Component;
import org.joml.Vector3f;

@Mixin(SizeOverLifetimeSetting.Runtime.class)
public abstract class SizeOverLifetimeRuntimeMixin {
	@Shadow
	@Final
	private SizeOverLifetimeSetting config;

	@Shadow
	public abstract boolean isEnable();

	@WrapOperation(method = "getSize", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction3;get(FLjava/util/function/Supplier;)Lorg/joml/Vector3f;"))
	private Vector3f lobecorp$offsetOutput(NumberFunction3 function, float t, Supplier<Float> lerp, Operation<Vector3f> original,
	                                       IParticle particle, float partialTicks) {
		if (!PhotonLifetimeCycleUtil.isLooping(this.config, isEnable(), particle))
			return original.call(function, t, lerp);
		return PhotonLifetimeCycleUtil.sampleVector(function, t, lerp, particle, partialTicks, this.config, true,
				Component.X, Component.Y, Component.Z);
	}

	@ModifyExpressionValue(method = "getSize", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/particle/IParticle;getT(F)F"))
	private float lobecorp$sampleLifetime(float original, IParticle particle, float partialTicks) {
		return PhotonLifetimeCycleUtil.sample(original, particle, partialTicks, this.config, isEnable());
	}
}
