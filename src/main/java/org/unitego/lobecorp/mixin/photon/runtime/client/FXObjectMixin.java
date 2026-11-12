package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.unitego.lobecorp.client.photon.playback.PhotonActivationOverrideAccess;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

import javax.annotation.Nullable;

@Mixin(FXObject.class)
public abstract class FXObjectMixin implements PhotonActivationOverrideAccess {
	@Unique
	private @Nullable Object lobecorp$activationOwner;

	@Override
	public boolean lobecorp$ownActivation() {
		return lobecorp$activationOwner != null;
	}

	@Override
	public void lobecorp$ownActivation(boolean own) {
		lobecorp$activationOwner = own ? this : null;
	}

	@Override
	public @Nullable Object lobecorp$activationOwner() {
		return lobecorp$activationOwner;
	}

	@Override
	public void lobecorp$activationOwner(@Nullable Object owner) {
		lobecorp$activationOwner = owner;
	}


	@WrapMethod(method = "tick")
	private void lobecorp$captureTick(Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.OBJECT_TICK)) {
			original.call();
			return;
		}
		long scope = PhotonPerformanceTraceUtil.begin(Stage.OBJECT_TICK, PhotonPerformanceOwnerUtil.object((FXObject) (Object) this));
		boolean completed = false;
		try {
			original.call();
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "extractFrame")
	private void lobecorp$captureFrame(float partialTicks, Operation<Void> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.OBJECT_FRAME)) {
			original.call(partialTicks);
			return;
		}
		long scope = PhotonPerformanceTraceUtil.begin(Stage.OBJECT_FRAME, PhotonPerformanceOwnerUtil.object((FXObject) (Object) this));
		boolean completed = false;
		try {
			original.call(partialTicks);
			completed = true;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	@WrapMethod(method = "copy")
	private IFXObject lobecorp$captureCopy(boolean deep, Operation<IFXObject> original) {
		if (!PhotonPerformanceTraceUtil.enabled(Stage.FX_COPY)) {
			return original.call(deep);
		}
		long scope = PhotonPerformanceTraceUtil.begin(Stage.FX_COPY, PhotonPerformanceOwnerUtil.object((FXObject) (Object) this));
		boolean completed = false;
		try {
			var result = original.call(deep);
			completed = true;
			return result;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}
}
