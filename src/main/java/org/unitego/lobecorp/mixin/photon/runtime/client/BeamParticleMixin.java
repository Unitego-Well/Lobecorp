package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lowdragmc.photon.client.gameobject.emitter.beam.BeamRuntime;
import com.lowdragmc.photon.client.gameobject.particle.BeamParticle;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.runtime.PhotonBeamLengthAccess;

@Mixin(BeamParticle.class)
public abstract class BeamParticleMixin {
	@Shadow
	protected BeamRuntime runtime;

	@ModifyExpressionValue(method = "getRealEnd", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/emitter/beam/BeamConfig;getEnd()Lorg/joml/Vector3f;"))
	private Vector3f lobecorp$overrideLength(Vector3f original) {
		return ((PhotonBeamLengthAccess) this.runtime).lobecorp$getBeamEnd(original);
	}
}
