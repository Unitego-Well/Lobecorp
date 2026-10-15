package org.unitego.lobecorp.client.photon.runtime;

/// 将已通过加入限额的待加入粒子转入原生分组，不推进模拟。
@SuppressWarnings("UnnecessaryModifier")
public interface PhotonPendingParticlePrimeAccess {
	public void lobecorp$flushPendingParticles();
}
