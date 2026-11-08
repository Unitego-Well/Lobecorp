package org.unitego.lobecorp.events.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.event.MidnightCleanupCinematic;
import org.unitego.lobecorp.client.animation.LcAnimationDisplays;
import org.unitego.lobecorp.client.conductor.render.ConductorRendering;
import org.unitego.lobecorp.client.horde.HordeRendering;
import org.unitego.lobecorp.client.horde.HordeRedSky;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.hud.RuntimeOverlayHud;

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
		HordeRedSky.extract(event);
	}

	@SubscribeEvent
	public static void onCustomGeometry(SubmitCustomGeometryEvent event) {
		ConductorRendering.onCustomGeometry(event);
		LcAnimationDisplays.onSubmit(event);
		HordeRendering.submit(event);
	}

	@SubscribeEvent
	public static void onHordeSky(RenderLevelStageEvent.AfterSky event) {
		HordeRedSky.draw(event, false);
	}

	@SubscribeEvent
	public static void onHordeShaderSky(RenderLevelStageEvent.AfterLevel event) {
		HordeRedSky.draw(event, true);
	}

	@SubscribeEvent
	public static void onHordeWorld(RenderLevelStageEvent.AfterOpaqueFeatures event) {
		HordeRendering.render(event);
	}

	@SubscribeEvent
	public static void onHand(RenderHandEvent event) {
		ConductorRendering.onHand(event);
	}

	/// 完整开幕在总GUI入口接管，取消后NeoForge不会再分发普通图层或Post。
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onGuiPre(RenderGuiEvent.Pre event) {
		if (MidnightCleanupCinematic.hidesHud()) {
			MidnightCleanupCinematic.render(event.getGuiGraphics());
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onGui(RenderGuiEvent.Post event) {
		if (!MidnightCleanupCinematic.hidesHud()) {
			if (Minecraft.getInstance().level != null) {
				ConductorRendering.onGui(event.getGuiGraphics());
			}
			RuntimeOverlayHud.INSTANCE.render(event);
		}
		MidnightCleanupCinematic.render(event.getGuiGraphics());
	}
}
