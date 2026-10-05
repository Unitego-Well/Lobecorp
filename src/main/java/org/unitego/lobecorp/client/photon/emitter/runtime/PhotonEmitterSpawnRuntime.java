package org.unitego.lobecorp.client.photon.emitter.runtime;

import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import org.unitego.lobecorp.config.photon.emitter.PhotonEmitterSpawnConfig;

/**
 * 本实例的动画槽；恢复或重启时回到保存的配置，不改共享模板。
 */
public class PhotonEmitterSpawnRuntime {
	public final RuntimeValue<Boolean> enabled;
	public final RuntimeValue<Integer> duration;
	public final RuntimeValue<Boolean> looping;
	public final RuntimeValue<Integer> startDelay;
	public final RuntimeValue<Integer> interval;
	public final RuntimeValue<NumberFunction> count;
	public final RuntimeValue<NumberFunction> rate;
	public final RuntimeValue<NumberFunction> probability;
	public final RuntimeValue<Integer> maxInstances;
	public final RuntimeValue<NumberFunction> launchSpeed;
	public final RuntimeValue<NumberFunction> lifetime;
	public final RuntimeValue<NumberFunction3> size;
	public final RuntimeValue<NumberFunction> color;
	public final RuntimeValue<NumberFunction> speed;
	public final RuntimeValue<NumberFunction> beamLength;
	public final RuntimeValue<NumberFunction> beamWidth;
	public final RuntimeValue<Float> playbackSpeed;

	public PhotonEmitterSpawnRuntime(PhotonEmitterSpawnConfig config) {
		enabled = new RuntimeValue<>(() -> config.enabled);
		duration = new RuntimeValue<>(() -> config.duration);
		looping = new RuntimeValue<>(() -> config.looping);
		startDelay = new RuntimeValue<>(() -> config.startDelay);
		interval = new RuntimeValue<>(() -> config.interval);
		count = new RuntimeValue<>(() -> config.count);
		rate = new RuntimeValue<>(() -> config.rate);
		probability = new RuntimeValue<>(() -> config.probability);
		maxInstances = new RuntimeValue<>(() -> config.maxInstances);
		launchSpeed = new RuntimeValue<>(() -> config.launchSpeed);
		lifetime = new RuntimeValue<>(() -> config.parameters.lifetime);
		size = new RuntimeValue<>(() -> config.parameters.size);
		color = new RuntimeValue<>(() -> config.parameters.color);
		speed = new RuntimeValue<>(() -> config.parameters.speed);
		beamLength = new RuntimeValue<>(() -> config.parameters.beamLength);
		beamWidth = new RuntimeValue<>(() -> config.parameters.beamWidth);
		playbackSpeed = new RuntimeValue<>(() -> config.parameters.playbackSpeed);
	}

	public void clear() {
		enabled.clear();
		duration.clear();
		looping.clear();
		startDelay.clear();
		interval.clear();
		count.clear();
		rate.clear();
		probability.clear();
		maxInstances.clear();
		launchSpeed.clear();
		lifetime.clear();
		size.clear();
		color.clear();
		speed.clear();
		beamLength.clear();
		beamWidth.clear();
		playbackSpeed.clear();
	}
}

