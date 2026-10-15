package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionBurstPrimeAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionControlAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionPrimeAccess;
import org.unitego.lobecorp.util.photon.runtime.PhotonEmissionPrimeUtil;

import java.util.ArrayDeque;

@Mixin(ParticleEmitter.class)
public abstract class ParticleEmitterPrimeMixin extends Emitter implements PhotonEmissionPrimeAccess {
	@Shadow
	protected int particleBatchCount;
	@Shadow
	protected int particleBatchCursor;

	@Shadow
	protected abstract TileParticle createNewParticle();

	@Override
	public void lobecorp$primeInitialEmission() {
		var emitter = (ParticleEmitter) (Object) this;
		if (!lobecorp$canPrime(emitter)) {
			return;
		}

		var maximum = emitter.runtime().maxParticles.get();
		var remaining = maximum - emitter.getParticleAmount();
		if (remaining <= 0 || !(emitter.runtime().emission instanceof PhotonEmissionBurstPrimeAccess access)) {
			return;
		}

		var available = Math.min(access.lobecorp$primeInitialBursts(emitter), remaining);
		if (available <= 0) {
			return;
		}

		particleBatchCount = available;
		particleBatchCursor = 0;
		PhotonEmissionPrimeUtil.duringPrime(() -> lobecorp$createParticles(emitter, available));
		lobecorp$flushParticles(emitter);
	}

	@Unique
	private boolean lobecorp$canPrime(ParticleEmitter emitter) {
		if (removed || PhotonEmissionPrimeUtil.isOutsideScene(emitter) || emitter.getAgeF() != 0
				|| emitter.getDelay() > 0 || !emitter.isActive() || !emitter.isAlive()) {
			return false;
		}

		if (emitter instanceof PhotonEmissionControlAccess control && control.lobecorp$isEmissionStopped()) {
			return false;
		}

		if (emitter.runtime().prewarm.get() <= 0) {
			return true;
		}

		// 非循环预热最多为 duration - 1，时长不超过一刻时原逻辑不会预热。
		return !emitter.isLooping() && emitter.getLifetime() <= 1;
	}

	@Unique
	private void lobecorp$createParticles(ParticleEmitter emitter, int count) {
		for (int index = 0; index < count; index++) {
			emitter.emitParticle(createNewParticle());
		}
	}

	@Unique
	private void lobecorp$flushParticles(ParticleEmitter emitter) {
		// 零时刻只接入已有渲染队列，首次正常更新仍负责出生事件和寿命推进。
		for (var particle : emitter.waitToAdded) {
			emitter.getParticles().computeIfAbsent(particle.getRenderType(), _ -> new ArrayDeque<>())
					.add(particle);
		}

		emitter.waitToAdded.clear();
	}
}
