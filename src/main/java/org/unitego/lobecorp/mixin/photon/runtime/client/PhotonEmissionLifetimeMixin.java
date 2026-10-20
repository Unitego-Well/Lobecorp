package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.playback.PhotonEmissionAccess;

@Mixin(Emitter.class)
public abstract class PhotonEmissionLifetimeMixin {
	@ModifyReturnValue(method = "isEmitting", at = @At("RETURN"))
	private boolean lobecorp$stoppedEmission(boolean original) {
		return original && (!(this instanceof PhotonEmissionAccess emission && emission.lobecorp$emissionStopped())
				|| ((Emitter) (Object) this).getParticleAmount() != 0);
	}
}
