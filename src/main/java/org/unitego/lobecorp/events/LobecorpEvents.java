package org.unitego.lobecorp.events;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.generator.ModGenerator;
import org.unitego.lobecorp.registry.conductor.ConductorTicketControllers;
import org.unitego.lobecorp.registry.LcPayloads;
import org.unitego.lobecorp.registry.LcRegistrys;
import org.unitego.lobecorp.registry.entity.EntityAttributeCreation;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class LobecorpEvents {
	@SubscribeEvent
	public static void registerRegistry(NewRegistryEvent event) {
		LcRegistrys.registerRegistry(event);
	}

	@SubscribeEvent
	public static void registerTicketControllers(RegisterTicketControllersEvent event) {
		ConductorTicketControllers.register(event);
	}

	@SubscribeEvent
	public static void registerPayloads(RegisterPayloadHandlersEvent event) {
		LcPayloads.register(event);
	}

	@SubscribeEvent
	public static void registerEntityAttributes(EntityAttributeCreationEvent event) {
		EntityAttributeCreation.registry(event);
	}

	@SubscribeEvent
	public static void addCommonAttributes(EntityAttributeModificationEvent event) {
		EntityAttributeCreation.addCommonAttributes(event);
	}

	@SubscribeEvent
	public static void gatherData(GatherDataEvent.Client event) {
		ModGenerator.gatherData(event);
	}
}
