package org.unitego.lobecorp.registry.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.init.LcParticleTypes;
import org.unitego.lobecorp.particle.client.BloodParticle;
import org.unitego.lobecorp.particle.client.SweeperStrikeParticle;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class RegisterParticleProviders {
    @SubscribeEvent
    public static void registry(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(LcParticleTypes.SIMPLE_DOUBLE_SLASH.get(), SweeperStrikeParticle.Provider::new);
        event.registerSpriteSet(LcParticleTypes.SIMPLE_LONG_SLASH.get(), SweeperStrikeParticle.Provider::new);
        event.registerSpriteSet(LcParticleTypes.SIMPLE_SHORT_SLASH.get(), SweeperStrikeParticle.Provider::new);
        event.registerSpriteSet(LcParticleTypes.BLOOD.get(), BloodParticle.Provider::new);
    }
}
