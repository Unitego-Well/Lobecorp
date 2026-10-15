package org.unitego.lobecorp.util.photon.animation;

import com.lowdragmc.photon.client.fx.FXRuntime;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationRuntimeAccess;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;

public class PhotonAnimationUtil {
	public static @Nullable PhotonAnimationRuntime of(FXRuntime runtime) {
		if (!(runtime instanceof PhotonAnimationRuntimeAccess access)) {
			return null;
		}

		var controller = access.lobecorp$animations();

		if (controller != null && (controller.closed() || !runtime.isValid())) {
			controller.close();
			return null;
		}

		return controller;
	}
}
