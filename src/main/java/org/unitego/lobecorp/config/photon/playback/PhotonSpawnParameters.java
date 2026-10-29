package org.unitego.lobecorp.config.photon.playback;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;

/// 发射器实例参数覆盖；对应 override 关闭时沿用模板原值。
public class PhotonSpawnParameters implements IPersistedSerializable {
	@Persisted
	public String targetName = "";
	@Persisted
	public boolean overrideLifetime;
	@Persisted
	public NumberFunction lifetime = NumberFunction.constant(PhotonSpawnConfig.DEFAULT_DURATION);
	@Persisted
	public boolean overrideSize;
	@Persisted
	public NumberFunction3 size = new NumberFunction3(1, 1, 1);
	@Persisted
	public boolean overrideColor;
	@Persisted
	public NumberFunction color = NumberFunction.color(-1);
	@Persisted
	public boolean overrideSpeed;
	@Persisted
	public NumberFunction speed = NumberFunction.constant(1);
	@Persisted
	public boolean overrideBeamLength;
	@Persisted
	public NumberFunction beamLength = NumberFunction.constant(1);
	@Persisted
	public boolean overrideBeamWidth;
	@Persisted
	public NumberFunction beamWidth = NumberFunction.constant(1);
	@Persisted
	public float playbackSpeed = 1;
}
