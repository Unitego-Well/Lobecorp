package org.unitego.lobecorp.client.photon.runtime;

import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.IEffectExecutor;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/// 一个权威表现快照拥有一个原生FX运行实例；宿主失效或世界切换时显式销毁。
public class PhotonGeoEffect implements IEffectExecutor, AutoCloseable {
	private final Level level;
	private final FXRuntime runtime;
	private final PhotonRuntimeFactory.Lease lease;
	private boolean closed;
	private boolean started;
	private boolean stopped;

	public PhotonGeoEffect(FX definition, Level level) {
		this.level = level;
		lease = PhotonRuntimeFactory.acquire(definition, level);
		runtime = lease.runtime();
	}

	@Override
	public Level getLevel() {
		return level;
	}

	public FXRuntime runtime() {
		return lease.runtime();
	}

	public void anchor(Vector3f position, Quaternionf rotation, Vector3f scale) {
		if (closed || !lease.current() || stopped) {
			return;
		}
		runtime.root.updatePos(new Vector3f(position));
		runtime.root.updateRotation(new Quaternionf(rotation));
		runtime.root.updateScale(new Vector3f(scale));
	}

	public void start() {
		if (!closed && lease.current() && !started && !stopped) {
			started = true;
			runtime.emit(this);
		}
	}

	/// 复用前调用：先由 stop(true) 销毁，再重新 emit 同一对象树；失败返回 false 由调用方回退新建。
	public boolean restart() {
		if (!stopped || closed || !lease.current()) {
			return false;
		}

		stopped = false;
		started = false;
		start();
		return started;
	}

	// 停止或换世界即失效；启动后还检查运行是否结束、有效性与宿主代次。
	public boolean isAlive() {
		return !closed && lease.current() && !stopped && Minecraft.getInstance().level == level
				&& (!started || !runtime.isFinished() && runtime.isValid()
				&& (runtime.getHost() == null || runtime.getHost().generation() == runtime.getGenerationAtEmit()));
	}

	public void stop(boolean force) {
		if (!closed && lease.current() && (!stopped || force)) {
			stopped = true;
			runtime.destroy(force);
		}
	}

	@Override
	public void close() {
		if (!closed) {
		stop(true);
			closed = true;
			lease.close(true);
		}
	}
}
