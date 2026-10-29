package org.unitego.lobecorp.events.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.animation.LcAnimationDisplays;
import org.unitego.lobecorp.client.conductor.render.ConductorRendering;
import org.unitego.lobecorp.client.horde.HordeRendering;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.horde.HordeDebugState;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import net.minecraft.client.Minecraft;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class ClientRenderingEvents {
	@SubscribeEvent
	public static void onNameTag(RenderNameTagEvent.CanRender event) {
		ConductorRendering.onNameTag(event);
	}

	@SubscribeEvent
	public static void onExtract(ExtractLevelRenderStateEvent event) {
		ConductorRendering.onExtract(event);
		LcAnimationDisplays.onExtract(event);
		HordeRendering.extract(event);
	}

	@SubscribeEvent
	public static void onCustomGeometry(SubmitCustomGeometryEvent event) {
		ConductorRendering.onCustomGeometry(event);
		LcAnimationDisplays.onSubmit(event);
		HordeRendering.submit(event);
	}

	@SubscribeEvent
	public static void onHordeWorld(RenderLevelStageEvent.AfterOpaqueFeatures event) {
		HordeRendering.render(event);
	}

	@SubscribeEvent
	public static void onHand(RenderHandEvent event) {
		ConductorRendering.onHand(event);
	}

	@SubscribeEvent
	public static void onGui(RenderGuiEvent.Post event) {
		ConductorRendering.onGui(event);
		if (HordeVisualConfig.STATISTICS.get() && Minecraft.getInstance().screen == null) {
			var font = Minecraft.getInstance().font;
			var lines = HordeDebugState.lines();
			for (int index = 0; index < lines.size(); index++) {
				event.getGuiGraphics().text(font, lines.get(index), 8, 8 + index * 11, -1);
			}
		}
	}
}
