package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;

/// 复用原 Burst 计数并记录零时刻触发的消费状态。
@SuppressWarnings("UnnecessaryModifier")
public interface PhotonEmissionBurstPrimeAccess {
	public int lobecorp$primeInitialBursts(ParticleEmitter emitter);
}
