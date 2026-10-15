package org.unitego.lobecorp.util.photon.animation;

import com.lowdragmc.photon.client.fx.ParticleTickHost;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

/// 只保留弱引用用于引擎丢弃与资源重载通知，不延长实例生命周期。
public class PhotonAnimationLifecycleUtil {
	/// 动画运行实例的弱引用集合；不延长实例生命周期，供统一清理使用。
	private static final CopyOnWriteArrayList<WeakReference<PhotonAnimationRuntime>> INSTANCES = new CopyOnWriteArrayList<>();

	public static void track(PhotonAnimationRuntime runtime) {
		INSTANCES.removeIf(reference -> reference.get() == null);
		INSTANCES.add(new WeakReference<>(runtime));
	}

	public static void untrack(PhotonAnimationRuntime runtime) {
		INSTANCES.removeIf(reference -> reference.get() == null || reference.get() == runtime);
	}

	public static void clear(@Nullable ParticleTickHost host) {
		for (var reference : INSTANCES) {
			var runtime = reference.get();

			if (runtime == null || host != null && runtime.host() != host) {
				continue;
			}

			if (host == null) {
				runtime.finish(true);
			} else {
				runtime.close();
			}
		}

		INSTANCES.removeIf(reference -> reference.get() == null);
	}
}
