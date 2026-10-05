package org.unitego.lobecorp.registry.photon.client;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.util.photon.editor.PhotonEmitterSpawnerTextUtil;

import java.util.List;

/**
 * 通过 Photon 原生类型注册与动画槽接口接入 Lobecorp 发射器。
 */
public class LcPhotonEmitterTypes {
	/**
	 * 发射器保存格式和 Photon 菜单使用的稳定类型 ID。
	 */
	public static final String TYPE_NAME = "lobecorp_emitter_spawner";
	/**
	 * Photon 已有对象类型注册表 ID，不创建平行注册表。
	 */
	private static final String REGISTRY = "photon:fx_object";
	/**
	 * 原生时间轴支持的调度和参数动画槽。
	 */
	private static final List<RuntimeBinding> BINDINGS = List.of(
			new RuntimeBinding("enabled", PhotonEmitterSpawnerTextUtil.ENABLED, ConfigValueType.BOOL,
					object -> ((PhotonEmitterSpawner) object).runtime().enabled),
			new RuntimeBinding("duration", PhotonEmitterSpawnerTextUtil.DURATION, ConfigValueType.INT,
					object -> ((PhotonEmitterSpawner) object).runtime().duration),
			new RuntimeBinding("looping", PhotonEmitterSpawnerTextUtil.LOOPING, ConfigValueType.BOOL,
					object -> ((PhotonEmitterSpawner) object).runtime().looping),
			new RuntimeBinding("startDelay", PhotonEmitterSpawnerTextUtil.START_DELAY, ConfigValueType.INT,
					object -> ((PhotonEmitterSpawner) object).runtime().startDelay),
			new RuntimeBinding("interval", PhotonEmitterSpawnerTextUtil.INTERVAL, ConfigValueType.INT,
					object -> ((PhotonEmitterSpawner) object).runtime().interval),
			new RuntimeBinding("count", PhotonEmitterSpawnerTextUtil.COUNT, ConfigValueType.NUMBER_FUNCTION,
					object -> ((PhotonEmitterSpawner) object).runtime().count),
			new RuntimeBinding("rate", PhotonEmitterSpawnerTextUtil.RATE, ConfigValueType.NUMBER_FUNCTION,
					object -> ((PhotonEmitterSpawner) object).runtime().rate),
			new RuntimeBinding("probability", PhotonEmitterSpawnerTextUtil.PROBABILITY, ConfigValueType.NUMBER_FUNCTION,
					object -> ((PhotonEmitterSpawner) object).runtime().probability),
			new RuntimeBinding("maxInstances", PhotonEmitterSpawnerTextUtil.MAX_INSTANCES, ConfigValueType.INT,
					object -> ((PhotonEmitterSpawner) object).runtime().maxInstances),
			new RuntimeBinding("launchSpeed", PhotonEmitterSpawnerTextUtil.LAUNCH_SPEED, ConfigValueType.NUMBER_FUNCTION,
					object -> ((PhotonEmitterSpawner) object).runtime().launchSpeed),
			new RuntimeBinding("parameters.lifetime", PhotonEmitterSpawnerTextUtil.LIFETIME, ConfigValueType.NUMBER_FUNCTION,
					object -> ((PhotonEmitterSpawner) object).runtime().lifetime),
			new RuntimeBinding("parameters.size", PhotonEmitterSpawnerTextUtil.SIZE, ConfigValueType.NUMBER_FUNCTION3,
					object -> ((PhotonEmitterSpawner) object).runtime().size),
			new RuntimeBinding("parameters.color", PhotonEmitterSpawnerTextUtil.COLOR, ConfigValueType.COLOR,
					object -> ((PhotonEmitterSpawner) object).runtime().color),
			new RuntimeBinding("parameters.speed", PhotonEmitterSpawnerTextUtil.SPEED, ConfigValueType.NUMBER_FUNCTION,
					object -> ((PhotonEmitterSpawner) object).runtime().speed),
			new RuntimeBinding("parameters.beamLength", PhotonEmitterSpawnerTextUtil.BEAM_LENGTH, ConfigValueType.NUMBER_FUNCTION,
					object -> ((PhotonEmitterSpawner) object).runtime().beamLength),
			new RuntimeBinding("parameters.beamWidth", PhotonEmitterSpawnerTextUtil.BEAM_WIDTH, ConfigValueType.NUMBER_FUNCTION,
					object -> ((PhotonEmitterSpawner) object).runtime().beamWidth),
			new RuntimeBinding("parameters.playbackSpeed", PhotonEmitterSpawnerTextUtil.PLAYBACK_SPEED, ConfigValueType.FLOAT,
					object -> ((PhotonEmitterSpawner) object).runtime().playbackSpeed)
	);

	@LDLRegisterClient(name = TYPE_NAME, registry = REGISTRY)
	public static final FXObjectType EMITTER_SPAWNER = new FXObjectType() {
		@Override
		public IFXObject create() {
			return new PhotonEmitterSpawner();
		}

		@Override
		public IGuiTexture icon() {
			return ParticleEmitter.ICON;
		}

		@Override
		public List<RuntimeBinding> runtimeBindings() {
			return BINDINGS;
		}
	};
}

