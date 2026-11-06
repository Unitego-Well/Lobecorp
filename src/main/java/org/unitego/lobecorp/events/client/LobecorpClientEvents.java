package org.unitego.lobecorp.events.client;

import com.geckolib.GeckoLibClientServices;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.unitego.lobecorp.registry.client.VisualQualityConfigScreens;
import org.unitego.lobecorp.registry.client.PlayerHudRegistration;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.photon.client.LcPhotonMcpCommands;
import org.unitego.lobecorp.registry.photon.client.LcPhotonEditorCommand;
import org.unitego.lobecorp.registry.photon.client.PhotonModelResourceRegistration;
import org.unitego.lobecorp.registry.animation.client.LcClientAnimationResourceRegistration;
import org.unitego.lobecorp.registry.command.LcDebugCommand;
import org.unitego.lobecorp.registry.animation.client.LcPlayerAnimationRegistration;
import org.unitego.lobecorp.event.EntityCorpseReverseEvent;
import org.unitego.lobecorp.registry.entity.client.EntityCorpseReverse;
import org.unitego.lobecorp.registry.entity.client.EntityRenderers;
import org.unitego.lobecorp.registry.entity.client.LcDebugEntries;
import org.unitego.lobecorp.registry.entity.client.RegisterRenderStateModifiers;
import org.unitego.lobecorp.registry.particle.client.RegisterParticleProviders;
import org.unitego.lobecorp.registry.particle.client.TextParticleRegistration;
import org.unitego.lobecorp.registry.particle.client.ScreenParticleRegistration;
import org.unitego.lobecorp.registry.conductor.client.ConductorHudRegistration;
import org.unitego.lobecorp.registry.conductor.client.ConductorKeyMappings;
import org.unitego.lobecorp.registry.conductor.client.ConductorPortraitRegistration;
import org.unitego.lobecorp.registry.conductor.client.ConductorRenderStateRegistration;

import java.util.Objects;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class LobecorpClientEvents {
	@SubscribeEvent
	public static void onRegisterConfigScreens(FMLClientSetupEvent event) {
		VisualQualityConfigScreens.register();

		event.enqueueWork(() -> {
			var startedNs = System.nanoTime();
			Objects.requireNonNull(GeckoLibClientServices.EVENTS);
			Objects.requireNonNull(GeckoLibClientServices.ITEM_RENDERING);
			Lobecorp.LOGGER.info("客户端动画服务初始化完成，耗时 {} ns", System.nanoTime() - startedNs);
		});
	}

	@SubscribeEvent
	public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
		LcPhotonEditorCommand.register(event.getDispatcher());
		if (!FMLEnvironment.isProduction()) {
			LcDebugCommand.registerClient(event.getDispatcher());
		}
	}

	@SubscribeEvent
	public static void onClientSetup(EntityRenderersEvent.AddLayers event) {
		NeoForge.EVENT_BUS.post(new EntityCorpseReverseEvent());
		LcPlayerAnimationRegistration.registerFirstPerson(event);

		if (ModList.get().isLoaded(LcPhotonMcpCommands.MOD_ID)) {
			LcPhotonMcpCommands.register();
		}
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
		LcPlayerAnimationRegistration.register(event);
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
	public static void onRegisterParticleGroups(RegisterParticleGroupsEvent event) {
		TextParticleRegistration.register(event);
	}

	@SubscribeEvent
	public static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
		TextParticleRegistration.registerReload(event);
		ScreenParticleRegistration.registerReload(event);
		PhotonModelResourceRegistration.registerReload(event);
		LcClientAnimationResourceRegistration.registerReload(event);
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
		ScreenParticleRegistration.register(event);
		PlayerHudRegistration.register(event);
	}
}
