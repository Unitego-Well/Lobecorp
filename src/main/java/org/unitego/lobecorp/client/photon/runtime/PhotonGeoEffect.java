package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/// 一个权威表现快照拥有一个原生FX运行实例；宿主失效或世界切换时显式销毁。
public class PhotonGeoEffect implements IEffectExecutor, AutoCloseable {
	private final Level level;
	private final FXRuntime runtime;
	private boolean started;
	private boolean stopped;

	public PhotonGeoEffect(FX definition, Level level) {
		this.level = level;
		runtime = definition.createRuntime(true);
	}

	@Override
	public Level getLevel() {
		return level;
	}

	public FXRuntime runtime() {
		return runtime;
	}

	public void anchor(Vector3f position, Quaternionf rotation, Vector3f scale) {
		if (stopped) {
			return;
		}
		runtime.root.updatePos(new Vector3f(position));
		runtime.root.updateRotation(new Quaternionf(rotation));
		runtime.root.updateScale(new Vector3f(scale));
	}

	public void start() {
		if (!started && !stopped) {
			started = true;
			runtime.emit(this);
		}
	}

	// 停止或换世界即失效；启动后还检查运行是否结束、有效性与宿主代次。
	public boolean isAlive() {
		return !stopped && Minecraft.getInstance().level == level
				&& (!started || !runtime.isFinished() && runtime.isValid()
				&& (runtime.getHost() == null || runtime.getHost().generation() == runtime.getGenerationAtEmit()));
	}

	public void stop(boolean force) {
		if (!stopped || force) {
			stopped = true;
			runtime.destroy(force);
		}
	}

	@Override
	public void close() {
		stop(true);
	}
}
