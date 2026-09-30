package org.unitego.lobecorp.events.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.conductor.ConductorRendering;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class ClientRenderingEvents {
	@SubscribeEvent
	public static void onNameTag(RenderNameTagEvent.CanRender event) {
		ConductorRendering.onNameTag(event);
	}

	@SubscribeEvent
	public static void onExtract(ExtractLevelRenderStateEvent event) {
		ConductorRendering.onExtract(event);
	}

	@SubscribeEvent
	public static void onCustomGeometry(SubmitCustomGeometryEvent event) {
		ConductorRendering.onCustomGeometry(event);
	}

	@SubscribeEvent
	public static void onHand(RenderHandEvent event) {
		ConductorRendering.onHand(event);
	}

	@SubscribeEvent
	public static void onGui(RenderGuiEvent.Post event) {
		ConductorRendering.onGui(event);
	}
}
