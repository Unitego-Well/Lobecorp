package org.unitego.lobecorp.client.photon.runtime;

/**
 * 使用实际模拟步长的粒子存活时间，供旋转插值使用。
 */
public interface PhotonParticleAgeAccess {
	public float lobecorp$getRotationAge(float partialTicks);
}
