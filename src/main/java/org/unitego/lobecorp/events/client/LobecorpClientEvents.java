package org.unitego.lobecorp.events.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.event.EntityCorpseReverseEvent;
import org.unitego.lobecorp.registry.entity.client.EntityCorpseReverse;
import org.unitego.lobecorp.registry.entity.client.EntityRenderers;
import org.unitego.lobecorp.registry.entity.client.LcDebugEntries;
import org.unitego.lobecorp.registry.entity.client.RegisterRenderStateModifiers;
import org.unitego.lobecorp.registry.particle.client.RegisterParticleProviders;
import org.unitego.lobecorp.registry.conductor.client.ConductorHudRegistration;
import org.unitego.lobecorp.registry.conductor.client.ConductorKeyMappings;
import org.unitego.lobecorp.registry.conductor.client.ConductorPortraitRegistration;
import org.unitego.lobecorp.registry.conductor.client.ConductorRenderStateRegistration;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class LobecorpClientEvents {
	@SubscribeEvent
	public static void onClientSetup(EntityRenderersEvent.AddLayers event) {
		NeoForge.EVENT_BUS.post(new EntityCorpseReverseEvent());
	}

	@SubscribeEvent
	public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		EntityRenderers.onRegister(event);
	}

	@SubscribeEvent
	public static void onRegisterCorpseReverse(EntityCorpseReverseEvent event) {
		EntityCorpseReverse.onRegister(event);
	}

	@SubscribeEvent
	public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
		RegisterRenderStateModifiers.onRegister(event);
		ConductorRenderStateRegistration.register(event);
	}

	@SubscribeEvent
	public static void onRegisterDebugEntries(RegisterDebugEntriesEvent event) {
		LcDebugEntries.onRegisterDebugEntries(event);
	}

	@SubscribeEvent
	public static void onRegisterDebugRenderers(RegisterDebugRenderersEvent event) {
		LcDebugEntries.onRegisterDebugRenderers(event);
	}

	@SubscribeEvent
	public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
		RegisterParticleProviders.registry(event);
	}

	@SubscribeEvent
	public static void registerKeys(RegisterKeyMappingsEvent event) {
		ConductorKeyMappings.register(event);
	}

	@SubscribeEvent
	public static void registerConductorPortraits(RegisterPictureInPictureRenderersEvent event) {
		ConductorPortraitRegistration.register(event);
	}

	@SubscribeEvent
	public static void registerHud(RegisterGuiLayersEvent event) {
		ConductorHudRegistration.register(event);
	}
}
