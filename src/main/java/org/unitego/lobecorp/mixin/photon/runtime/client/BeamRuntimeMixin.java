package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamRuntime;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonBeamLengthAccess;

@Mixin(BeamRuntime.class)
public abstract class BeamRuntimeMixin implements PhotonBeamLengthAccess {
	@Unique
	private Float lobecorp$beamLength;

	@Override
	public Vector3f lobecorp$getBeamEnd(Vector3f authored) {
		if (this.lobecorp$beamLength == null || authored.lengthSquared() == 0) {
			return authored;
		}
		return new Vector3f(authored).normalize(this.lobecorp$beamLength);
	}

	@Override
	public void lobecorp$setBeamLength(Float length) {
		this.lobecorp$beamLength = length;
	}

	@Inject(method = "clear", at = @At("TAIL"))
	private void lobecorp$clearLength(CallbackInfo ci) {
		this.lobecorp$beamLength = null;
	}
}
