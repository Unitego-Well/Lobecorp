package org.unitego.lobecorp.config.photon.emitter;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3Config;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Color;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Gradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.RandomColor;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.RandomGradient;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.RandomCurve;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;

/**
 * 只覆盖生成实例的参数；默认沿用模板，数值在每次发射时采样。
 */
public class PhotonEmitterSpawnParameters implements IConfigurable, IPersistedSerializable {
	@Configurable(name = PhotonEmitterSpawnerTextUtil.TARGET)
	public String targetName = "";
	@Configurable(name = PhotonEmitterSpawnerTextUtil.OVERRIDE_LIFETIME)
	public boolean overrideLifetime;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.LIFETIME)
	@NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class}, numberType = ConfigNumber.Type.INTEGER, min = 0)
	public NumberFunction lifetime = NumberFunction.constant(PhotonEmitterSpawnConfig.DEFAULT_DURATION);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.OVERRIDE_SIZE)
	public boolean overrideSize;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.SIZE)
	@NumberFunction3Config(common = @NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class}, min = 0))
	public NumberFunction3 size = new NumberFunction3(1, 1, 1);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.OVERRIDE_COLOR)
	public boolean overrideColor;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.COLOR)
	@NumberFunctionConfig(types = {Color.class, RandomColor.class, Gradient.class, RandomGradient.class}, defaultValue = -1)
	public NumberFunction color = NumberFunction.color(-1);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.OVERRIDE_SPEED)
	public boolean overrideSpeed;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.SPEED)
	@NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class})
	public NumberFunction speed = NumberFunction.constant(1);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.OVERRIDE_BEAM_LENGTH)
	public boolean overrideBeamLength;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.BEAM_LENGTH)
	@NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class}, min = 0)
	public NumberFunction beamLength = NumberFunction.constant(1);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.OVERRIDE_BEAM_WIDTH)
	public boolean overrideBeamWidth;
	@Configurable(name = PhotonEmitterSpawnerTextUtil.BEAM_WIDTH)
	@NumberFunctionConfig(types = {Constant.class, RandomConstant.class, Curve.class, RandomCurve.class}, min = 0)
	public NumberFunction beamWidth = NumberFunction.constant(1);
	@Configurable(name = PhotonEmitterSpawnerTextUtil.PLAYBACK_SPEED)
	@ConfigNumber(range = {0, PhotonEmitterSpawnConfig.MAX_PLAYBACK_SPEED})
	public float playbackSpeed = 1;
}

