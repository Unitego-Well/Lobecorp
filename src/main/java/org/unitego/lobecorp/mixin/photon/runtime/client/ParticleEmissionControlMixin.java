package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionControlAccess;

import java.util.Queue;
import java.util.Map;

@Mixin(ParticleEmitter.class)
public abstract class ParticleEmissionControlMixin implements PhotonEmissionControlAccess {
	@Shadow
	@Final
	private Queue<Runnable> pendingSubEmitterSpawns;
	@Unique
	private boolean lobecorp$emissionStopped;
	@Unique
	private boolean lobecorp$preserveOnReset;

	@Override
	public void lobecorp$setPreserveOnReset(boolean preserve) {
		lobecorp$preserveOnReset = preserve;
	}

	@WrapOperation(method = "reset", at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V"))
	private void lobecorp$resetParticles(Map<?, ?> particles, Operation<Void> original) {
		if (!lobecorp$preserveOnReset)
			original.call(particles);
	}

	@WrapOperation(method = "reset", at = @At(value = "INVOKE", target = "Ljava/util/Queue;clear()V"))
	private void lobecorp$resetPendingSpawns(Queue<?> pending, Operation<Void> original) {
		if (!lobecorp$preserveOnReset)
			original.call(pending);
	}

	@Override
	public boolean lobecorp$isEmissionStopped() {
		return lobecorp$emissionStopped;
	}

	@Override
	public void lobecorp$setEmissionStopped(boolean stopped) {
		lobecorp$emissionStopped = stopped;
	}

	@Override
	public void lobecorp$clearParticles() {
		var emitter = (ParticleEmitter) (Object) this;
		emitter.getParticles().clear();
		emitter.waitToAdded.clear();
		pendingSubEmitterSpawns.clear();
	}

	@WrapOperation(method = "emitParticle(F)V", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/EmissionSetting$Runtime;getEmissionCount(Lcom/lowdragmc/photon/client/gameobject/emitter/particle/ParticleEmitter;Lnet/minecraft/util/RandomSource;F)I"))
	private int lobecorp$emissionCount(EmissionSetting.Runtime setting, ParticleEmitter particleEmitter, RandomSource randomSource, float dt, Operation<Integer> original) {
		return lobecorp$emissionStopped ? 0 : original.call(setting, particleEmitter, randomSource, dt);
	}

	@Inject(method = "emitParticle(Lcom/lowdragmc/photon/client/gameobject/particle/IParticle;)V", at = @At("HEAD"), cancellable = true)
	private void lobecorp$blockNewParticle(IParticle particle, CallbackInfo ci) {
		if (lobecorp$emissionStopped)
			ci.cancel();
	}

	@Inject(method = "reset", at = @At("RETURN"))
	private void lobecorp$resetEmission(CallbackInfo ci) {
		lobecorp$emissionStopped = false;
	}
}
