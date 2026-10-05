package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.runtime.PhotonParticleAgeAccess;

@Mixin(TileParticle.class)
public abstract class TileParticleMixin implements PhotonParticleAgeAccess {
	@Shadow
	protected float age;

	@Unique
	private float lobecorp$previousAge;

	@Inject(method = "syncOrigin", at = @At("HEAD"))
	private void lobecorp$snapshotAge(CallbackInfo ci) {
		this.lobecorp$previousAge = this.age;
	}

	@Override
	public float lobecorp$getRotationAge(float partialTicks) {
		return partialTicks == 0 ? this.age
				: Mth.lerp(Mth.clamp(partialTicks, 0, 1), this.lobecorp$previousAge, this.age);
	}
}
