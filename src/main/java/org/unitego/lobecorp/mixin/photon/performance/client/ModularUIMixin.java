package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

@Mixin(ModularUI.class)
public class ModularUIMixin {
	@WrapMethod(method = "calculateStyleAndLayout")
	private void lobecorp$captureLayout(Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.UI_LAYOUT)) {
			original.call();
			return;
		}
		long scope = PhotonPerformanceTraceUtil.begin(Stage.UI_LAYOUT, PhotonPerformanceTraceUtil.node(this));
		boolean completed = false;
		try {
			original.call();
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "refreshHoveredElement")
	private void lobecorp$captureHover(float x, float y, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.UI_INPUT)) {
			original.call(x, y);
			return;
		}
		long scope = PhotonPerformanceTraceUtil.begin(Stage.UI_INPUT, PhotonPerformanceTraceUtil.node(this));
		boolean completed = false;
		try {
			original.call(x, y);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}
}
