package org.unitego.lobecorp.mixin.photon.performance.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.fx.timeline.AnimationTrack;
import com.lowdragmc.photon.client.gameobject.FXObject;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

@Mixin(AnimationTrack.class)
public class AnimationTrackMixin {
	@WrapMethod(method = "sampleInto")
	private void lobecorp$captureTrack(FXObject target, double time, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.TRACK)) {
			original.call(target, time);
			return;
		}
		int parent = PhotonPerformanceOwnerUtil.object(target);
		long scope = PhotonPerformanceTraceUtil.begin(Stage.TRACK,
				PhotonPerformanceOwnerUtil.track((AnimationTrack) (Object) this, parent), time);
		boolean completed = false;
		try {
			original.call(target, time);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}
}
