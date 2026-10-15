package org.unitego.lobecorp.mixin.photon.editor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.unitego.lobecorp.client.photon.runtime.PhotonPendingParticlePrimeAccess;

import java.util.Map;
import java.util.Queue;

@Mixin(ParticleEngine.class)
public abstract class ParticleEnginePrimeMixin implements PhotonPendingParticlePrimeAccess {
	@Shadow
	@Final
	private Map<ParticleRenderType, ParticleGroup<?>> particles;
	@Shadow
	@Final
	private Queue<Particle> particlesToAdd;

	@Shadow
	private ParticleGroup<?> createParticleGroup(ParticleRenderType type) {
		throw new AssertionError();
	}

	@Override
	public void lobecorp$flushPendingParticles() {
		if ((Object) this == Minecraft.getInstance().particleEngine) {
			return;
		}

		// add 已执行原生限额检查与计数；这里只复用 tick 末尾的分组加入步骤。
		for (Particle particle; (particle = particlesToAdd.poll()) != null; ) {
			particles.computeIfAbsent(particle.getGroup(), this::createParticleGroup).add(particle);
		}
	}
}
