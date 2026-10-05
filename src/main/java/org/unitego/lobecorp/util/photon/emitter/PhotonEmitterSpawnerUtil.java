package org.unitego.lobecorp.util.photon.emitter;

import com.lowdragmc.photon.client.fx.FXRuntime;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.emitter.PhotonEmitterSpawner;
import org.unitego.lobecorp.client.photon.emitter.runtime.PhotonSpawnOverrides;

import java.util.List;
import java.util.UUID;

/**
 * 客户端线程上的实例控制入口；只作用于指定 FXRuntime，不修改特效资源。
 */
@SuppressWarnings("unused")
public class PhotonEmitterSpawnerUtil {
	@Nullable
	public static PhotonEmitterSpawner find(FXRuntime runtime, String name) {
		var objects = runtime.findObjects(name);
		return objects.size() == 1 && objects.getFirst() instanceof PhotonEmitterSpawner emitter ? emitter : null;
	}

	public static int emit(FXRuntime runtime, String name, int count) {
		var emitter = find(runtime, name);
		return emitter == null ? 0 : emitter.emitNow(count);
	}

	public static boolean stop(FXRuntime runtime, String name) {
		var emitter = find(runtime, name);
		if (emitter == null) {
			return false;
		}

		emitter.stopEmission();
		return true;
	}

	public static boolean resume(FXRuntime runtime, String name) {
		if (!runtime.isValid() || runtime.isFinished()) {
			return false;
		}

		var emitter = find(runtime, name);
		if (emitter == null) {
			return false;
		}

		return emitter.resumeEmission();
	}

	public static boolean pause(FXRuntime runtime, String name, boolean paused) {
		var emitter = find(runtime, name);
		if (emitter == null) {
			return false;
		}

		emitter.setPaused(paused);
		return true;
	}

	public static boolean restart(FXRuntime runtime, String name, boolean clear) {
		var emitter = find(runtime, name);
		if (emitter == null) {
			return false;
		}

		if (!runtime.isValid() || runtime.isFinished() || !emitter.isAlive() || emitter.getEffectExecutor() == null) {
			return false;
		}

		emitter.restart(clear);
		return true;
	}

	public static boolean clear(FXRuntime runtime, String name) {
		var emitter = find(runtime, name);
		if (emitter == null) {
			return false;
		}

		emitter.lobecorp$clearParticles();
		return true;
	}

	public static boolean parameters(FXRuntime runtime, String name, @Nullable PhotonSpawnOverrides values) {
		var emitter = find(runtime, name);
		if (emitter == null) {
			return false;
		}

		emitter.setParameterOverride(values);
		return true;
	}

	public static List<UUID> instances(FXRuntime runtime, String name) {
		var emitter = find(runtime, name);
		return emitter == null ? List.of() : emitter.instanceHandles();
	}

	public static boolean stopInstance(FXRuntime runtime, String name, UUID handle, boolean force) {
		var emitter = find(runtime, name);
		return emitter != null && emitter.stopInstance(handle, force);
	}
}
