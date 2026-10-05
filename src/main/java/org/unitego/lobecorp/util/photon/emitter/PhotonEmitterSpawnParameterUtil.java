package org.unitego.lobecorp.util.photon.emitter;

import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import org.joml.Vector4f;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.client.photon.emitter.runtime.PhotonSpawnOverrides;
import org.unitego.lobecorp.client.photon.runtime.PhotonBeamLengthAccess;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnConfig;

/**
 * 每次发射采样一次参数，转换为持久的实例动画槽覆盖，兼容子实例重启。
 */
public class PhotonEmitterSpawnParameterUtil {
	public static PhotonSpawnOverrides sample(PhotonEmitterSpawner emitter) {
		var config = emitter.config.parameters;
		var values = emitter.runtime();
		var random = emitter.getRandomSource();
		var t = emitter.getT();
		var color = config.overrideColor ? unpackColor(values.color.get().get(random, t).intValue()) : null;
		return new PhotonSpawnOverrides(config.targetName,
				config.overrideLifetime ? values.lifetime.get().get(random, t).intValue() : null,
				config.overrideSize ? values.size.get().get(random, t) : null,
				color,
				config.overrideSpeed ? values.speed.get().get(random, t).floatValue() : null,
				config.overrideBeamLength ? values.beamLength.get().get(random, t).floatValue() : null,
				config.overrideBeamWidth ? values.beamWidth.get().get(random, t).floatValue() : null,
				Math.clamp(values.playbackSpeed.get(), 0, PhotonEmitterSpawnConfig.MAX_PLAYBACK_SPEED));
	}

	private static Vector4f unpackColor(int color) {
		return new Vector4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
				(color & 255) / 255f, (color >>> 24) / 255f);
	}

	public static OverrideBinding bind(PhotonSpawnOverrides values) {
		return new OverrideBinding(values);
	}

	/**
	 * 缓存常量函数，逐 tick 重新写入动画槽而不逐 tick 分配函数对象。
	 */
	public static class OverrideBinding {
		private final PhotonSpawnOverrides values;
		private final NumberFunction lifetime;
		private final NumberFunction3 size;
		private final NumberFunction color;
		private final NumberFunction speed;
		private final NumberFunction beamLength;
		private final NumberFunction beamWidth;

		public OverrideBinding(PhotonSpawnOverrides values) {
			this.values = values;
			lifetime = values.lifetime() == null ? null : NumberFunction.constant(values.lifetime());
			var sizeValue = values.size();
			size = sizeValue == null ? null : new NumberFunction3(sizeValue.x, sizeValue.y, sizeValue.z);
			var colorValue = values.color();
			color = colorValue == null ? null : NumberFunction.color(packColor(colorValue));
			speed = values.speed() == null ? null : NumberFunction.constant(values.speed());
			beamLength = values.beamLength() == null ? null : NumberFunction.constant(values.beamLength());
			beamWidth = values.beamWidth() == null ? null : NumberFunction.constant(values.beamWidth());
		}

		public void apply(IFXObject object) {
			if (!values.targetName().isEmpty() && !values.targetName().equals(object.getName())) {
				return;
			}

			if (object instanceof ParticleEmitter emitter) {
				var runtime = emitter.runtime();
				if (lifetime != null) {
					runtime.startLifetime.set(lifetime);
				}

				if (size != null) {
					runtime.startSize.set(size);
				}

				if (color != null) {
					runtime.startColor.set(color);
				}

				if (speed != null) {
					runtime.startSpeed.set(speed);
				}
				return;
			}

			if (object instanceof BeamEmitter emitter) {
				if (beamWidth != null) {
					emitter.runtime().width.set(beamWidth);
				}

				if (beamLength != null) {
					((PhotonBeamLengthAccess) emitter.runtime()).lobecorp$setBeamLength(values.beamLength());
				}

				if (color != null) {
					emitter.runtime().color.set(color);
				}
			}
		}

		private static int packColor(Vector4f color) {
			return Math.round(color.w * 255) << 24 | Math.round(color.x * 255) << 16
					| Math.round(color.y * 255) << 8 | Math.round(color.z * 255);
		}
	}
}
