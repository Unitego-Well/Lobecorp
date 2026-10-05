package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import java.util.function.Supplier;

import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.RotationOverLifetimeSetting;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Unique;
import org.joml.Vector3f;
import net.minecraft.util.Mth;
import org.unitego.lobecorp.client.photon.runtime.PhotonParticleAgeAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonRotationCycleAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess.Component;
import org.unitego.lobecorp.util.photon.runtime.PhotonLifetimeCycleUtil;

@Mixin(RotationOverLifetimeSetting.Runtime.class)
public abstract class RotationOverLifetimeRuntimeMixin {
	@Shadow
	@Final
	private RotationOverLifetimeSetting config;

	@Shadow
	public abstract boolean isEnable();

	@Shadow
	@Final
	public RuntimeValue<NumberFunction> roll;
	@Shadow
	@Final
	public RuntimeValue<NumberFunction> pitch;
	@Shadow
	@Final
	public RuntimeValue<NumberFunction> yaw;

	@ModifyExpressionValue(method = "getRotation", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/particle/IParticle;getT(F)F"))
	private float lobecorp$sampleInfiniteRotation(float original, IParticle particle, float partialTicks) {
		if (!isEnable() || !(particle instanceof TileParticle tile)) {
			return original;
		}
		PhotonRotationCycleAccess cycle = (PhotonRotationCycleAccess) this.config;
		int ticks = cycle.lobecorp$getRotationCycleTicks();
		if (!cycle.lobecorp$isRotationCycleEnabled() || ticks <= 0) {
			return original;
		}
		float age = ((PhotonParticleAgeAccess) tile).lobecorp$getRotationAge(partialTicks);
		return PhotonLifetimeCycleUtil.phase(age, ticks);
	}

	@ModifyReturnValue(method = "getRotation", at = @At("RETURN"))
	private Vector3f lobecorp$continueRotation(Vector3f original, IParticle particle, float partialTicks) {
		if (!isEnable() || !(particle instanceof TileParticle tile)) {
			return original;
		}
		PhotonRotationCycleAccess cycle = (PhotonRotationCycleAccess) this.config;
		int ticks = cycle.lobecorp$getRotationCycleTicks();
		if (!cycle.lobecorp$isRotationCycleEnabled() || ticks <= 0) {
			return original;
		}
		float age = ((PhotonParticleAgeAccess) tile).lobecorp$getRotationAge(partialTicks);
		PhotonCycleOffsetAccess offsets = (PhotonCycleOffsetAccess) this.config;
		// 每个轴按偏移后的时间累计整轮增量，保持跨周期旋转连续。
		return original.add(new Vector3f(
				lobecorp$cycleDelta(yaw.get(), particle, "rol2") * (float) Math.floor((age + offsets.lobecorp$getCycleOffset(Component.YAW)) / ticks),
				lobecorp$cycleDelta(pitch.get(), particle, "rol1") * (float) Math.floor((age + offsets.lobecorp$getCycleOffset(Component.PITCH)) / ticks),
				lobecorp$cycleDelta(roll.get(), particle, "rol0") * (float) Math.floor((age + offsets.lobecorp$getCycleOffset(Component.ROLL)) / ticks)).mul(Mth.TWO_PI / 360));
	}

	@WrapOperation(method = "getRotation", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction;get(FLjava/util/function/Supplier;)Ljava/lang/Number;", ordinal = 0))
	private Number lobecorp$sampleYAW(NumberFunction function, float time, Supplier<Float> random, Operation<Number> original,
	                                  IParticle particle, float partialTicks) {
		return original.call(function, PhotonLifetimeCycleUtil.sampleComponent(time, particle, partialTicks, this.config, isEnable(), Component.YAW), random);
	}

	@WrapOperation(method = "getRotation", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction;get(FLjava/util/function/Supplier;)Ljava/lang/Number;", ordinal = 1))
	private Number lobecorp$samplePITCH(NumberFunction function, float time, Supplier<Float> random, Operation<Number> original,
	                                    IParticle particle, float partialTicks) {
		return original.call(function, PhotonLifetimeCycleUtil.sampleComponent(time, particle, partialTicks, this.config, isEnable(), Component.PITCH), random);
	}

	@WrapOperation(method = "getRotation", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction;get(FLjava/util/function/Supplier;)Ljava/lang/Number;", ordinal = 2))
	private Number lobecorp$sampleROLL(NumberFunction function, float time, Supplier<Float> random, Operation<Number> original,
	                                   IParticle particle, float partialTicks) {
		return original.call(function, PhotonLifetimeCycleUtil.sampleComponent(time, particle, partialTicks, this.config, isEnable(), Component.ROLL), random);
	}

	@Unique
	private float lobecorp$cycleDelta(NumberFunction function, IParticle particle, String randomKey) {
		return function.get(1, () -> particle.getMemRandom(randomKey)).floatValue()
				- function.get(0, () -> particle.getMemRandom(randomKey)).floatValue();
	}
}
