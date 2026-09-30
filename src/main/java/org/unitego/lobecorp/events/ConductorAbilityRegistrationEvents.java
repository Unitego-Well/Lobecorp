package org.unitego.lobecorp.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.event.RegisterConductorAbilitiesEvent;
import org.unitego.lobecorp.registry.entity.RegisterConductorAbilities;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class ConductorAbilityRegistrationEvents {
	@SubscribeEvent
	public static void setup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> ModLoader.postEvent(new RegisterConductorAbilitiesEvent()));
	}

	@SubscribeEvent
	public static void register(RegisterConductorAbilitiesEvent event) {
		RegisterConductorAbilities.register(event);
	}
}
