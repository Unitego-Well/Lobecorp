package org.unitego.lobecorp.events.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.conductor.ConductorClient;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.client.particle.photon.PhotonParticleRuntimeTrial;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class ClientRuntimeEvents {
	@SubscribeEvent
	public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		PhotonParticleRuntimeTrial.onClientLoggingIn(event);
	}

	@SubscribeEvent
	public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		ConductorControls.clearSession();
	}

	@SubscribeEvent
	public static void onClientLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide()) ConductorControls.clearSession();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		ConductorClient.tick();
		ConductorControls.onTick(event);
	}

	@SubscribeEvent
	public static void onMovement(MovementInputUpdateEvent event) {
		ConductorControls.onMovement(event);
	}

	@SubscribeEvent
	public static void onGuiLayer(RenderGuiLayerEvent.Pre event) {
		ConductorControls.onGuiLayer(event);
	}

	@SubscribeEvent
	public static void onScroll(InputEvent.MouseScrollingEvent event) {
		ConductorControls.onScroll(event);
	}

	@SubscribeEvent
	public static void onMouse(InputEvent.MouseButton.Pre event) {
		ConductorControls.onMouse(event);
	}
}
