package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.client.scene.ParticleManager;
import com.lowdragmc.photon.client.PhotonParticleManager;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.unitego.lobecorp.client.photon.runtime.PhotonPendingParticlePrimeAccess;

@Mixin(ParticleManager.class)
public abstract class SceneParticleManagerPrimeMixin implements PhotonPendingParticlePrimeAccess {
	@Shadow
	private ParticleEngine engine;

	@Shadow
	private void ensureEngine() {
		throw new AssertionError();
	}

	@Override
	public void lobecorp$flushPendingParticles() {
		if (!(((ParticleManager) (Object) this) instanceof PhotonParticleManager)) {
			return;
		}

		ensureEngine();

		if (engine instanceof PhotonPendingParticlePrimeAccess access) {
			access.lobecorp$flushPendingParticles();
		}
	}
}
