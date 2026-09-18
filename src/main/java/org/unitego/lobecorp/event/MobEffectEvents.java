package org.unitego.lobecorp.event;

import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.effect.LcMobEffects;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class MobEffectEvents {
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onMobEffectRemoved(MobEffectEvent.Remove event) {
		if (!event.isCanceled() && event.getEffect().equals(LcMobEffects.STUN)) {
			LcMobEffects.STUN.get().restoreAi(event.getEntity());
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onMobEffectExpired(MobEffectEvent.Expired event) {
		MobEffectInstance effect = event.getEffectInstance();
		if (!event.isCanceled() && effect != null && effect.getEffect().equals(LcMobEffects.STUN)) {
			LcMobEffects.STUN.get().restoreAi(event.getEntity());
		}
	}
}
