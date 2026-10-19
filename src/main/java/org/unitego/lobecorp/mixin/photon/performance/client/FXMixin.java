package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

@Mixin(FX.class)
public class FXMixin {
	@WrapMethod(method = "createRuntime(Z)Lcom/lowdragmc/photon/client/fx/FXRuntime;")
	private FXRuntime lobecorp$captureCreation(boolean deepCopy, Operation<FXRuntime> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.FX_CREATE)) {
			return original.call(deepCopy);
		}

		var definition = (FX) (Object) this;
		long scope = PhotonPerformanceTraceUtil.begin(Stage.FX_CREATE, PhotonPerformanceOwnerUtil.definition(definition));
		boolean completed = false;
		try {
			var result = original.call(deepCopy);
			PhotonPerformanceOwnerUtil.bind(result, definition);
			completed = true;
			return result;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "createInternalRuntime")
	private FXRuntime lobecorp$captureInternalCreation(Operation<FXRuntime> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.FX_CREATE)) {
			return original.call();
		}

		var definition = (FX) (Object) this;
		long scope = PhotonPerformanceTraceUtil.begin(Stage.FX_CREATE, PhotonPerformanceOwnerUtil.definition(definition));
		boolean completed = false;
		try {
			var result = original.call();
			PhotonPerformanceOwnerUtil.bind(result, definition);
			completed = true;
			return result;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}
}
