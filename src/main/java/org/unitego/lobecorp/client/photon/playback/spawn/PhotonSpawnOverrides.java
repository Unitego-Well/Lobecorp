package org.unitego.lobecorp.client.photon.playback.spawn;

import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;

import javax.annotation.Nullable;

/// 每次生成只采样一次常量，原生重启后仍由该子实例重施覆盖。
public class PhotonSpawnOverrides {
	private final String target;
	private final @Nullable NumberFunction lifetime;
	private final @Nullable NumberFunction3 size;
	private final @Nullable NumberFunction color;
	private final @Nullable NumberFunction speed;
	private final @Nullable NumberFunction width;
	private final @Nullable Float length;

	public PhotonSpawnOverrides(PhotonChildSpawner owner) {
		var config = owner.config.parameters;
		var random = owner.getRandomSource();
		float time = owner.getT();
		target = config.targetName;
		lifetime = config.overrideLifetime ? NumberFunction.constant(owner.function("parameters.lifetime").get(random, time).intValue()) : null;
		var sampledSize = config.overrideSize ? ((NumberFunction3) owner.slot("parameters.size").get()).get(random, time) : null;
		size = sampledSize == null ? null : new NumberFunction3(sampledSize.x, sampledSize.y, sampledSize.z);
		color = config.overrideColor ? NumberFunction.color(owner.function("parameters.color").get(random, time).intValue()) : null;
		speed = config.overrideSpeed ? NumberFunction.constant(owner.function("parameters.speed").get(random, time)) : null;
		width = config.overrideBeamWidth ? NumberFunction.constant(owner.function("parameters.beamWidth").get(random, time)) : null;
		length = config.overrideBeamLength ? owner.function("parameters.beamLength").get(random, time).floatValue() : null;
	}

	public void apply(IFXObject object) {
		if (!target.isEmpty() && !target.equals(object.getName())) {
			return;
		}

		if (object instanceof ParticleEmitter particle) {
			if (lifetime != null) {
				particle.runtime().startLifetime.set(lifetime);
			}

			if (size != null) {
				particle.runtime().startSize.set(size);
			}

			if (color != null) {
				particle.runtime().startColor.set(color);
			}

			if (speed != null) {
				particle.runtime().startSpeed.set(speed);
			}
		} else if (object instanceof BeamEmitter beam) {
			if (width != null) {
				beam.runtime().width.set(width);
			}

			if (color != null) {
				beam.runtime().color.set(color);
			}

			if (length != null) {
				throw new IllegalStateException("旧光束长度覆盖尚待原生等价适配。");
			}
		}
	}
}
