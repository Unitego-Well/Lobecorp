package org.unitego.lobecorp.util.photon.runtime;

import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import com.lowdragmc.photon.client.PhotonParticleManager;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.unitego.lobecorp.client.photon.runtime.PhotonEmissionPrimeAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonPendingParticlePrimeAccess;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;

/// 仅由编辑器调用的零时刻初始化；不会启动普通世界中的运行时。
public class PhotonEmissionPrimeUtil {
	// 当前线程的粒子出生初始化深度，防止嵌套创建提前退出作用域。
	private static final ThreadLocal<Integer> PRIME_DEPTH = new ThreadLocal<>();

	public static boolean active() {
		return PRIME_DEPTH.get() != null;
	}

	public static void duringPrime(Runnable action) {
		var previous = PRIME_DEPTH.get();
		PRIME_DEPTH.set(previous == null ? 1 : previous + 1);

		try {
			action.run();
		} finally {
			if (previous == null) {
				PRIME_DEPTH.remove();
			} else {
				PRIME_DEPTH.set(previous);
			}
		}
	}

	public static void prime(FXRuntime runtime) {
		if (runtime == null || !runtime.isEmitted() || runtime.isDestroyed()) {
			return;
		}

		for (var object : runtime.getObjects().values()) {
			primeObject(object);
		}
	}

	public static void primeSubtree(IFXObject object) {
		if (isOutsideScene(object)) {
			return;
		}

		primeObject(object);

		for (var child : object.transform().children()) {
			if (child.sceneObject() instanceof IFXObject childObject) {
				primeSubtree(childObject);
			}
		}
	}

	private static void primeObject(IFXObject object) {
		if (isOutsideScene(object) || !(object instanceof PhotonEmissionPrimeAccess access)) {
			return;
		}

		access.lobecorp$primeInitialEmission();
	}

	public static boolean isOutsideScene(IFXObject object) {
		return object == null || !(object.getLevel() instanceof DummyWorld world)
				|| !(world.getParticleManager() instanceof PhotonParticleManager manager)
				|| !PhotonEditorTools.enabled(manager.sceneView, PhotonEditorTextUtil.ZERO_TIME_EMISSION_FEATURE);
	}

	public static void flushScene(PhotonParticleManager manager) {
		if (!PhotonEditorTools.enabled(manager.sceneView, PhotonEditorTextUtil.ZERO_TIME_EMISSION_FEATURE)) {
			return;
		}

		if (manager instanceof PhotonPendingParticlePrimeAccess access) {
			access.lobecorp$flushPendingParticles();
		}
	}
}
