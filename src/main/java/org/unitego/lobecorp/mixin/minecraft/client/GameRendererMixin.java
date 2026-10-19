package org.unitego.lobecorp.mixin.minecraft.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.client.photon.performance.IPhotonPerformanceInstrumentation;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

@Mixin(GameRenderer.class)
public class GameRendererMixin implements IPhotonPerformanceInstrumentation {
	@WrapMethod(method = "render")
	private void lobecorp$captureFrame(DeltaTracker deltaTracker, boolean advanceGameTime, Operation<Void> original) {
		boolean completed = false;
		PhotonFrameCapture.beginFrame();
		long scope = PhotonPerformanceTraceUtil.begin(Stage.RENDER, 0);

		try {
			original.call(deltaTracker, advanceGameTime);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
			PhotonFrameCapture.endFrame(completed);
		}
	}

	@WrapMethod(method = "extract")
	private void lobecorp$captureExtraction(DeltaTracker deltaTracker, boolean advanceGameTime, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.EXTRACT)) {
			original.call(deltaTracker, advanceGameTime);
			return;
		}
		long scope = PhotonPerformanceTraceUtil.begin(Stage.EXTRACT, 0);
		boolean completed = false;
		try {
			original.call(deltaTracker, advanceGameTime);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "extractGui")
	private void lobecorp$captureGui(DeltaTracker deltaTracker, boolean renderLevel, boolean resourcesLoaded, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.UI_RENDER)) {
			original.call(deltaTracker, renderLevel, resourcesLoaded);
			return;
		}
		long scope = PhotonPerformanceTraceUtil.begin(Stage.UI_RENDER, 0);
		boolean completed = false;
		try {
			original.call(deltaTracker, renderLevel, resourcesLoaded);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

}
