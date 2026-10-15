package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionBurstPrimeAccess;

import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(EmissionSetting.Runtime.class)
public abstract class EmissionSettingRuntimePrimeMixin implements PhotonEmissionBurstPrimeAccess {
	@Shadow
	@Final
	private EmissionSetting config;

	@Unique
	private boolean lobecorp$initialBurstPrimed;
	// 保留零时刻 Burst 的随机数量，首次正常窗口复用，避免再次抽样。
	@Unique
	private final Map<EmissionSetting.Burst, Integer> lobecorp$primedCounts = new IdentityHashMap<>();

	@Shadow
	private int burstTriggers(EmissionSetting.Burst bust, int count, RandomSource randomSource, float start, float end) {
		throw new AssertionError();
	}

	@Override
	public int lobecorp$primeInitialBursts(ParticleEmitter particleEmitter) {
		if (lobecorp$initialBurstPrimed) {
			return 0;
		}

		lobecorp$initialBurstPrimed = true;
		var randomSource = particleEmitter.getRandomSource();
		var number = 0;

		for (var bust : config.getBursts()) {
			if (bust.time != 0) {
				continue;
			}

			var count = bust.getCount().get(randomSource, particleEmitter.getT()).intValue();
			lobecorp$primedCounts.put(bust, count);
			// 半开区间只包含零触发，周期、概率与数量仍由原方法计算。
			number += burstTriggers(bust, count, randomSource, 0, Math.nextUp(0f));
		}

		return number;
	}

	@WrapOperation(method = "distanceAndBursts", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/number/NumberFunction;get(Lnet/minecraft/util/RandomSource;F)Ljava/lang/Number;"))
	private Number lobecorp$reuseInitialCount(NumberFunction function, RandomSource randomSource, float t,
	                                          Operation<Number> original, @Local(name = "bust") EmissionSetting.Burst bust,
	                                          @Local(argsOnly = true, name = "particleEmitter") ParticleEmitter particleEmitter) {
		var count = lobecorp$primedCounts.get(bust);
		if (particleEmitter.getAgeF() == 0 && count != null) {
			return count;
		}

		return original.call(function, randomSource, t);
	}

	@WrapOperation(method = "distanceAndBursts", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/EmissionSetting$Runtime;burstTriggers(Lcom/lowdragmc/photon/client/gameobject/emitter/data/EmissionSetting$Burst;ILnet/minecraft/util/RandomSource;FF)I"))
	private int lobecorp$skipConsumedInitialBurst(EmissionSetting.Runtime setting, EmissionSetting.Burst bust,
	                                              int count, RandomSource randomSource, float start, float end,
	                                              Operation<Integer> original,
	                                              @Local(argsOnly = true, name = "particleEmitter") ParticleEmitter particleEmitter) {
		if (particleEmitter.getAgeF() == 0 && start == 0 && end > 0 && lobecorp$primedCounts.remove(bust) != null) {
			return original.call(setting, bust, count, randomSource, Math.nextUp(0f), end);
		}

		return original.call(setting, bust, count, randomSource, start, end);
	}

	@Inject(method = "getEmissionCount(Lcom/lowdragmc/photon/client/gameobject/emitter/particle/ParticleEmitter;Lnet/minecraft/util/RandomSource;F)I",
			at = @At("RETURN"))
	private void lobecorp$releaseInitialCounts(ParticleEmitter particleEmitter, RandomSource randomSource, float dt,
	                                           CallbackInfoReturnable<Integer> cir) {
		if (dt > 0) {
			lobecorp$primedCounts.clear();
		}
	}

	@Inject(method = "clear", at = @At("RETURN"))
	private void lobecorp$clearInitialBurst(CallbackInfo ci) {
		lobecorp$initialBurstPrimed = false;
		lobecorp$primedCounts.clear();
	}
}
