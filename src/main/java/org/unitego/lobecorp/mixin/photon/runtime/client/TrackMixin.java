package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorTrackAccess;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivatorOptions;

@Mixin(Track.class)
public abstract class TrackMixin implements PhotonActivatorTrackAccess {
	@Unique
	private PhotonActivatorOptions lobecorp$activationOptions = PhotonActivatorOptions.DEFAULT;

	@Override
	public PhotonActivatorOptions lobecorp$getActivationOptions() {
		return lobecorp$activationOptions;
	}

	@Override
	public void lobecorp$setActivationOptions(PhotonActivatorOptions options) {
		lobecorp$activationOptions = options;
	}

	@Inject(method = "copyExtra", at = @At("RETURN"))
	private void lobecorp$copyActivation(Track target, CallbackInfo ci) {
		if ((Object) this instanceof ActivatorTrack) {
			((PhotonActivatorTrackAccess) target).lobecorp$setActivationOptions(lobecorp$activationOptions);
		}
	}
}
