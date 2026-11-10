package org.unitego.lobecorp.events.client;

import com.geckolib.GeckoLibClientServices;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.unitego.lobecorp.registry.client.config.VisualQualityConfigScreens;
import org.unitego.lobecorp.registry.client.hud.PlayerHudRegistration;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.client.encounter.resource.MidnightCleanupResources;
import org.unitego.lobecorp.registry.client.photon.LcPhotonMcpCommands;
import org.unitego.lobecorp.registry.client.photon.LcPhotonEditorCommand;
import org.unitego.lobecorp.registry.client.photon.PhotonModelResourceRegistration;
import org.unitego.lobecorp.registry.client.animation.LcClientAnimationResourceRegistration;
import org.unitego.lobecorp.registry.command.LcDebugCommand;
import org.unitego.lobecorp.util.debug.LcDebugUtil;
import org.unitego.lobecorp.util.debug.LcDebugUtil.Category;
import org.unitego.lobecorp.registry.client.animation.LcPlayerAnimationRegistration;
import org.unitego.lobecorp.event.EntityCorpseReverseEvent;
import org.unitego.lobecorp.registry.client.entity.EntityCorpseReverse;
import org.unitego.lobecorp.registry.client.entity.EntityRenderers;
import org.unitego.lobecorp.registry.client.entity.LcDebugEntries;
import org.unitego.lobecorp.registry.client.entity.RegisterRenderStateModifiers;
import org.unitego.lobecorp.registry.client.particle.RegisterParticleProviders;
import org.unitego.lobecorp.registry.client.particle.TextParticleRegistration;
import org.unitego.lobecorp.registry.client.particle.ScreenParticleRegistration;
import org.unitego.lobecorp.registry.client.conductor.ConductorHudRegistration;
import org.unitego.lobecorp.registry.client.conductor.ConductorKeyMappings;
import org.unitego.lobecorp.registry.client.conductor.ConductorPortraitRegistration;
import org.unitego.lobecorp.registry.client.conductor.ConductorRenderStateRegistration;

import java.util.Objects;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class LobecorpClientEvents {
	@SubscribeEvent
	public static void onRegisterConfigScreens(FMLClientSetupEvent event) {
		VisualQualityConfigScreens.register();

		event.enqueueWork(() -> {
			boolean diagnostic = LcDebugUtil.isEnabled(Category.ANIMATION);
			long startedNs = diagnostic ? System.nanoTime() : 0;
			Objects.requireNonNull(GeckoLibClientServices.EVENTS);
			Objects.requireNonNull(GeckoLibClientServices.ITEM_RENDERING);
			if (diagnostic) {
				Lobecorp.LOGGER.info("客户端动画服务初始化完成，耗时 {} ns", System.nanoTime() - startedNs);
			}
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
		MidnightCleanupResources.registerReload(event);
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
