package org.unitego.lobecorp.client.photon.runtime;

/**
 * 无限寿命旋转的编辑器配置；有限寿命粒子继续使用 Photon 原有采样。
 */
public interface PhotonRotationCycleAccess {
	public boolean lobecorp$isRotationCycleEnabled();

	public int lobecorp$getRotationCycleTicks();
}
