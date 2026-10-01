package org.unitego.lobecorp.registry.particle.client;

import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.unitego.lobecorp.client.particle.BloodParticle;
import org.unitego.lobecorp.client.particle.ShortSmokeParticle;
import org.unitego.lobecorp.client.particle.SweeperStrikeParticle;
import org.unitego.lobecorp.client.particle.QueenRepelWaveParticle;
import org.unitego.lobecorp.client.particle.QueenLaserParticle;
import org.unitego.lobecorp.client.particle.QueenChannelLaserParticle;
import org.unitego.lobecorp.client.particle.QueenMagicCircleParticle;
import org.unitego.lobecorp.client.particle.QueenConvergentParticle;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;

public class RegisterParticleProviders {
	public static void registry(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(LcParticleTypes.SIMPLE_DOUBLE_SLASH.get(), SweeperStrikeParticle.Provider::new);
		event.registerSpriteSet(LcParticleTypes.SIMPLE_LONG_SLASH.get(), SweeperStrikeParticle.Provider::new);
		event.registerSpriteSet(LcParticleTypes.SIMPLE_SHORT_SLASH.get(), SweeperStrikeParticle.Provider::new);
		event.registerSpriteSet(LcParticleTypes.BLOOD.get(), BloodParticle.Provider::new);
		event.registerSpriteSet(LcParticleTypes.SHORT_SMOKE.get(), ShortSmokeParticle.Provider::new);
		event.registerSpriteSet(LcParticleTypes.QUEEN_REPEL_WAVE.get(), QueenRepelWaveParticle.Provider::new);
		event.registerSpriteSet(LcParticleTypes.QUEEN_LASER.get(), QueenLaserParticle.Provider::new);
		event.registerSpriteSet(LcParticleTypes.QUEEN_CHANNEL_LASER.get(), QueenChannelLaserParticle.Provider::new);
		event.registerSpriteSet(LcParticleTypes.QUEEN_MAGIC_CIRCLE.get(), QueenMagicCircleParticle.Provider::new);
		event.registerSpriteSet(LcParticleTypes.QUEEN_CONVERGENT.get(), QueenConvergentParticle.Provider::new);
	}
}
