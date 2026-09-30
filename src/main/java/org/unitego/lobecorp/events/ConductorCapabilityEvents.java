package org.unitego.lobecorp.events;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.entity.ConductorCapabilityRegistration;
import org.unitego.lobecorp.registry.entity_skill.EntitySkillCapabilities;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class ConductorCapabilityEvents {
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void register(RegisterCapabilitiesEvent event) {
		ConductorCapabilityRegistration.register(event);
		EntitySkillCapabilities.register(event);
	}
}
