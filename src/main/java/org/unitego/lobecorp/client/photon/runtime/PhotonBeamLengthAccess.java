package org.unitego.lobecorp.client.photon.runtime;

import org.joml.Vector3f;

/**
 * 当前 Beam 播放实例的本地端点覆盖，不修改共享 BeamConfig。
 */
public interface PhotonBeamLengthAccess {
	public Vector3f lobecorp$getBeamEnd(Vector3f authored);

	public void lobecorp$setBeamLength(Float length);
}
