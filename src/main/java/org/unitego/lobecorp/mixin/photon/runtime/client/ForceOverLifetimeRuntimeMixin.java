package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;

import java.util.function.Supplier;

import com.lowdragmc.photon.client.gameobject.emitter.data.ForceOverLifetimeSetting;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess.Component;
import org.joml.Vector3f;

@Mixin(ForceOverLifetimeSetting.Runtime.class)
public abstract class ForceOverLifetimeRuntimeMixin {
	@Shadow
	@Final
	private ForceOverLifetimeSetting config;

	@Shadow
	public abstract boolean isEnable();

	@WrapOperation(method = "getForce", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction3;get(FLjava/util/function/Supplier;)Lorg/joml/Vector3f;"))
	private Vector3f lobecorp$offsetOutput(NumberFunction3 function, float t, Supplier<Float> lerp, Operation<Vector3f> original,
	                                       IParticle particle) {
		if (!PhotonLifetimeCycleUtil.isLooping(this.config, isEnable(), particle))
			return original.call(function, t, lerp);
		return PhotonLifetimeCycleUtil.sampleVector(function, t, lerp, particle, 0, this.config, true,
				Component.X, Component.Y, Component.Z);
	}

	@ModifyExpressionValue(method = "getForce", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/particle/IParticle;getT()F"))
	private float lobecorp$sampleLifetime(float original, IParticle particle) {
		return PhotonLifetimeCycleUtil.sample(original, particle, 0, this.config, isEnable());
	}
}
