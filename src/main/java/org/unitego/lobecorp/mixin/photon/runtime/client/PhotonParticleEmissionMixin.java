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
import org.unitego.lobecorp.client.photon.playback.PhotonEmissionAccess;

import java.util.Map;
import java.util.Queue;

@Mixin(ParticleEmitter.class)
public abstract class PhotonParticleEmissionMixin implements PhotonEmissionAccess {
	@Shadow
	@Final
	private Queue<Runnable> pendingSubEmitterSpawns;
	@Unique
	private boolean lobecorp$stopped;
	@Unique
	private boolean lobecorp$preserve;

	@Override
	public boolean lobecorp$emissionStopped() {
		return lobecorp$stopped;
	}

	@Override
	public void lobecorp$emissionStopped(boolean stopped) {
		lobecorp$stopped = stopped;
	}

	@Override
	public void lobecorp$preserveParticles(boolean preserve) {
		lobecorp$preserve = preserve;
	}

	@Override
	public void lobecorp$clearParticles() {
		var emitter = (ParticleEmitter) (Object) this;
		emitter.getParticles().clear();
		emitter.waitToAdded.clear();
		pendingSubEmitterSpawns.clear();
	}

	@WrapOperation(method = "reset", at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V"))
	private void lobecorp$preserveParticles(Map<?, ?> particles, Operation<Void> original) {
		if (!lobecorp$preserve) {
			original.call(particles);
		}
	}

	@WrapOperation(method = "reset", at = @At(value = "INVOKE", target = "Ljava/util/Queue;clear()V"))
	private void lobecorp$preservePending(Queue<?> pending, Operation<Void> original) {
		if (!lobecorp$preserve) {
			original.call(pending);
		}
	}

	@WrapOperation(method = "emitParticle(F)V", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/data/EmissionSetting$Runtime;getEmissionCount(Lcom/lowdragmc/photon/client/gameobject/emitter/particle/ParticleEmitter;Lnet/minecraft/util/RandomSource;F)I"))
	private int lobecorp$emissionCount(EmissionSetting.Runtime setting, ParticleEmitter particleEmitter, RandomSource randomSource,
	                                   float dt, Operation<Integer> original) {
		return lobecorp$stopped ? 0 : original.call(setting, particleEmitter, randomSource, dt);
	}

	@Inject(method = "emitParticle(Lcom/lowdragmc/photon/client/gameobject/particle/IParticle;)V", at = @At("HEAD"), cancellable = true)
	private void lobecorp$blockNewParticle(IParticle particle, CallbackInfo ci) {
		if (lobecorp$stopped) {
			ci.cancel();
		}
	}

	@Inject(method = "reset", at = @At("RETURN"))
	private void lobecorp$resetStopped(CallbackInfo ci) {
		lobecorp$stopped = false;
	}
}
