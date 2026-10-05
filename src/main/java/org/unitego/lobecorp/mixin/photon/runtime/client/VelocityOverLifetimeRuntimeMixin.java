package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;

import java.util.function.Supplier;

import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.VelocityOverLifetimeSetting;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess.Component;
import org.joml.Vector3f;

@Mixin(VelocityOverLifetimeSetting.Runtime.class)
public abstract class VelocityOverLifetimeRuntimeMixin {
	@Shadow
	@Final
	private VelocityOverLifetimeSetting config;

	@Shadow
	public abstract boolean isEnable();

	@WrapOperation(method = "getVelocityAddition", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction3;get(FLjava/util/function/Supplier;)Lorg/joml/Vector3f;", ordinal = 0))
	private Vector3f lobecorp$offsetLinear(NumberFunction3 function, float t, Supplier<Float> lerp, Operation<Vector3f> original,
	                                       TileParticle particle) {
		if (!PhotonLifetimeCycleUtil.isLooping(this.config, isEnable(), particle))
			return original.call(function, t, lerp);
		return PhotonLifetimeCycleUtil.sampleVector(function, t, lerp, particle, 0, this.config, true,
				Component.X, Component.Y, Component.Z);
	}

	@WrapOperation(method = "getVelocityAddition", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction3;get(FLjava/util/function/Supplier;)Lorg/joml/Vector3f;", ordinal = 1))
	private Vector3f lobecorp$offsetOrbital(NumberFunction3 function, float t, Supplier<Float> lerp, Operation<Vector3f> original,
	                                        TileParticle particle) {
		if (!PhotonLifetimeCycleUtil.isLooping(this.config, isEnable(), particle))
			return original.call(function, t, lerp);
		return PhotonLifetimeCycleUtil.sampleVector(function, t, lerp, particle, 0, this.config, true,
				Component.ORBITAL_X, Component.ORBITAL_Y, Component.ORBITAL_Z);
	}

	@WrapOperation(method = "getVelocityAddition", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction3;get(FLjava/util/function/Supplier;)Lorg/joml/Vector3f;", ordinal = 2))
	private Vector3f lobecorp$offsetCenter(NumberFunction3 function, float t, Supplier<Float> lerp, Operation<Vector3f> original,
	                                       TileParticle particle) {
		if (!PhotonLifetimeCycleUtil.isLooping(this.config, isEnable(), particle))
			return original.call(function, t, lerp);
		return PhotonLifetimeCycleUtil.sampleVector(function, t, lerp, particle, 0, this.config, true,
				Component.CENTER_X, Component.CENTER_Y, Component.CENTER_Z);
	}

	@WrapOperation(method = "getVelocityAddition", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction;get(FLjava/util/function/Supplier;)Ljava/lang/Number;"))
	private Number lobecorp$offsetRadial(NumberFunction function, float time, Supplier<Float> random, Operation<Number> original, TileParticle particle) {
		return original.call(function, PhotonLifetimeCycleUtil.sampleComponent(time, particle, 0, this.config, isEnable(), Component.RADIAL), random);
	}

	@WrapOperation(method = "getVelocityMultiplier", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction;get(FLjava/util/function/Supplier;)Ljava/lang/Number;"))
	private Number lobecorp$offsetMultiplier(NumberFunction function, float time, Supplier<Float> random, Operation<Number> original, IParticle particle) {
		return original.call(function, PhotonLifetimeCycleUtil.sampleComponent(time, particle, 0, this.config, isEnable(), Component.MULTIPLIER), random);
	}

	@ModifyExpressionValue(method = "getVelocityAddition", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/particle/TileParticle;getT()F"))
	private float lobecorp$sampleVelocity(float original, TileParticle particle) {
		return PhotonLifetimeCycleUtil.sample(original, particle, 0, this.config, isEnable());
	}

	@ModifyExpressionValue(method = "getVelocityMultiplier", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/particle/IParticle;getT()F"))
	private float lobecorp$sampleMultiplier(float original, IParticle particle) {
		return PhotonLifetimeCycleUtil.sample(original, particle, 0, this.config, isEnable());
	}
}
