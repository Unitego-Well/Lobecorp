package org.unitego.lobecorp.util.photon.runtime;

import com.lowdragmc.photon.client.gameobject.particle.IParticle;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import com.lowdragmc.photon.client.gameobject.particle.BeamParticle;
import com.lowdragmc.photon.client.gameobject.particle.aratrail.AraTrailParticle;
import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3Config;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunctionConfig;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.configurator.NumberFunction3Accessor;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.function.Supplier;
import java.util.function.Consumer;

import org.unitego.lobecorp.client.photon.runtime.PhotonLifetimeCycleAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonParticleAgeAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonCycleOffsetAccess.Component;
import org.unitego.lobecorp.client.photon.runtime.PhotonRotationCycleAccess;

public class PhotonLifetimeCycleUtil {
	/**
	 * 曲线默认一个游戏秒循环一次；单位 tick，与粒子寿命独立。
	 */
	public static final int DEFAULT_CYCLE_TICKS = 20;

	public static float sample(float original, IParticle particle, float partialTicks, Object config, boolean enabled) {
		if (!enabled || !(particle instanceof TileParticle tile)
				|| !(config instanceof PhotonLifetimeCycleAccess cycle) || !cycle.lobecorp$isLifetimeCycleEnabled()) {
			return original;
		}
		int ticks = cycle.lobecorp$getLifetimeCycleTicks();
		if (ticks <= 0) {
			return original;
		}
		float age = ((PhotonParticleAgeAccess) tile).lobecorp$getRotationAge(partialTicks);
		return phase(age, ticks);
	}

	/**
	 * 三轴时间偏移只允许常量，单位 tick；复用 Photon 的合轴/分轴控件。
	 */
	@NumberFunction3Config(common = @NumberFunctionConfig(min = -Float.MAX_VALUE, max = Float.MAX_VALUE))
	private static final Object TIME_OFFSET_CONFIG = null;

	public static Configurator offsetConfigurator(String name, Supplier<NumberFunction3> supplier, Consumer<NumberFunction3> consumer, Object owner) {
		try {
			Field field = PhotonLifetimeCycleUtil.class.getDeclaredField("TIME_OFFSET_CONFIG");
			return new NumberFunction3Accessor().create(name, supplier, consumer, true, field, owner);
		} catch (NoSuchFieldException exception) {
			throw new IllegalStateException(exception);
		}
	}

	public static float sampleUV(float original, IParticle particle, float partialTicks, Object config, boolean enabled) {
		if (!enabled || !(config instanceof PhotonLifetimeCycleAccess cycle)
				|| !cycle.lobecorp$isLifetimeCycleEnabled() || cycle.lobecorp$getLifetimeCycleTicks() <= 0)
			return original;
		PhotonParticleAgeAccess age;
		if (particle instanceof TileParticle tile) {
			age = (PhotonParticleAgeAccess) tile;
		} else {
			IParticleEmitter emitter = particle instanceof BeamParticle beam ? beam.getEmitter()
					: particle instanceof AraTrailParticle trail ? trail.emitter : null;
			if (!(emitter instanceof PhotonParticleAgeAccess emitterAge))
				return original;
			age = emitterAge;
		}
		float offset = config instanceof PhotonCycleOffsetAccess offsets ? offsets.lobecorp$getCycleOffset(Component.UV) : 0;
		return phase(age.lobecorp$getRotationAge(partialTicks) + offset, cycle.lobecorp$getLifetimeCycleTicks());
	}

	public static float phase(float age, int ticks) {
		double time = (double) age / ticks;
		return (float) (time - Math.floor(time));
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public static boolean isLooping(Object config, boolean enabled, IParticle particle) {
		if (!enabled || !(particle instanceof TileParticle))
			return false;
		return config instanceof PhotonLifetimeCycleAccess lifetime
				&& lifetime.lobecorp$isLifetimeCycleEnabled() && lifetime.lobecorp$getLifetimeCycleTicks() > 0
				|| config instanceof PhotonRotationCycleAccess rotation
				&& rotation.lobecorp$isRotationCycleEnabled() && rotation.lobecorp$getRotationCycleTicks() > 0;
	}

	public static float sampleComponent(float original, IParticle particle, float partialTicks, Object config, boolean enabled, Component component) {
		if (!isLooping(config, enabled, particle) || !(config instanceof PhotonCycleOffsetAccess offsets))
			return original;
		int ticks = config instanceof PhotonLifetimeCycleAccess lifetime
				? lifetime.lobecorp$getLifetimeCycleTicks() : ((PhotonRotationCycleAccess) config).lobecorp$getRotationCycleTicks();
		return phase(((PhotonParticleAgeAccess) particle).lobecorp$getRotationAge(partialTicks)
				+ offsets.lobecorp$getCycleOffset(component), ticks);
	}

	public static Vector3f sampleVector(NumberFunction3 function, float time, Supplier<Float> random, IParticle particle,
	                                    float partialTicks, Object config, boolean enabled, Component x, Component y, Component z) {
		return new Vector3f(
				function.x.get(sampleComponent(time, particle, partialTicks, config, enabled, x), random).floatValue(),
				function.y.get(sampleComponent(time, particle, partialTicks, config, enabled, y), random).floatValue(),
				function.z.get(sampleComponent(time, particle, partialTicks, config, enabled, z), random).floatValue());
	}
}
