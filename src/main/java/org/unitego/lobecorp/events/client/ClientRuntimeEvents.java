package org.unitego.lobecorp.events.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.attachment.LcAttachments;
import org.unitego.lobecorp.client.attachment.LcClientAttachments;
import org.unitego.lobecorp.client.animation.LcClientAnimations;
import org.unitego.lobecorp.client.animation.player.LcPlayerAnimationRuntime;
import org.unitego.lobecorp.client.conductor.ConductorClient;
import org.unitego.lobecorp.client.conductor.ConductorControls;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.client.photon.performance.PhotonRepeatedEffectTrial;
import org.unitego.lobecorp.client.photon.performance.QueenBeamPerformanceTrial;
import org.unitego.lobecorp.client.photon.runtime.QueenBeamEffects;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import org.unitego.lobecorp.client.particle.text.TextParticleClient;
import org.unitego.lobecorp.client.particle.screen.ScreenParticles;
import org.unitego.lobecorp.client.hud.HudLayerManager;
import org.unitego.lobecorp.client.hud.RuntimeOverlayHud;
import org.unitego.lobecorp.client.hud.PlayerStatusHud;
import org.unitego.lobecorp.client.hud.TopLeftHud;
import org.unitego.lobecorp.client.hud.filter.DamageFilter;
import org.unitego.lobecorp.client.hud.filter.RationalityFilter;
import org.unitego.lobecorp.util.animation.LcModelLodUtil;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.horde.HordeAmbient;
import org.unitego.lobecorp.world.effect.StunMobEffect;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.client.entity.renderer.EntityCorpseRenderer;
import org.unitego.lobecorp.world.item.animation.LcAttachedItems;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class ClientRuntimeEvents {
	@SubscribeEvent
	public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		HordeClient.clear();
		clearHud();
		ScreenParticles.clear();
		RuntimeOverlayHud.INSTANCE.clearChildren();
		TextParticleClient.clear();
		LcAttachments.clearClientWorlds();
		LcAttachedItems.clearClientWorlds();
		LcClientAttachments.clear();
		LcPlayerAnimationRuntime.clear();
		LcClientAnimations.clear();
		QueenBeamEffects.clear();
		PhotonRuntimeFactory.invalidateWorld();
	}

	@SubscribeEvent
	public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		HordeClient.clear();
		clearHud();
		ScreenParticles.clear();
		RuntimeOverlayHud.INSTANCE.clearChildren();
		TextParticleClient.clear();
		LcAttachments.clearClientWorlds();
		LcAttachedItems.clearClientWorlds();
		LcClientAttachments.clear();
		LcPlayerAnimationRuntime.clear();
		LcClientAnimations.clear();
		ConductorControls.clearSession();
		QueenBeamEffects.clear();
		PhotonRuntimeFactory.invalidateWorld();
		PhotonFrameCapture.cancel();
		LcModelLodUtil.clear();
	}

	@SubscribeEvent
	public static void onClientLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel() instanceof ClientLevel) {
			HordeClient.clear();
		}
		if (event.getLevel().isClientSide()) {
			ConductorControls.clearSession();
		}

		if (event.getLevel() instanceof ClientLevel level) {
			clearHud();
			ScreenParticles.clear();
			RuntimeOverlayHud.INSTANCE.clearChildren();
			TextParticleClient.clear();
			LcModelLodUtil.clear();
			LcClientAnimations.clearWorld();
			QueenBeamEffects.clear();
			PhotonRuntimeFactory.invalidateWorld();
			LcAttachments.clear(level);
			LcAttachedItems.clear(level);
			LcClientAttachments.clear();
			LcPlayerAnimationRuntime.clear();
		}
	}

	@SubscribeEvent
	public static void onCorpseLeaveLevel(EntityLeaveLevelEvent event) {
		if (event.getLevel().isClientSide() && event.getEntity() instanceof EntityCorpse<?> corpse) {
			EntityCorpseRenderer.forget(corpse);
		}
	}

	@SubscribeEvent
	public static void onGameShuttingDown(GameShuttingDownEvent event) {
		PhotonOwnedEffectResources.closePreparation();
	}

	@SubscribeEvent
	public static void onVisualFrameEnd(RenderFrameEvent.Post event) {
		LcModelLodUtil.endFrame();
		PhotonRuntimeFactory.onFrameEnd();
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		PhotonRuntimeFactory.onTick();
		PhotonRepeatedEffectTrial.tick();
		QueenBeamPerformanceTrial.tick();
		var level = Minecraft.getInstance().level;
		if (level != null) {
			LcAttachments.tick(level);
		}

		LcPlayerAnimationRuntime.tick();
		ScreenParticles.tick();
		DamageFilter.INSTANCE.tick();
		RationalityFilter.INSTANCE.tick();
		ConductorClient.tick();
		ConductorControls.onTick(event);
		QueenBeamEffects.tick();
		HordeClient.tick();
		HordeAmbient.tick();
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

	@SubscribeEvent
	public static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.player == null || !StunMobEffect.preventsActions(minecraft.player)
				|| !event.isAttack() && !event.isUseItem()) {
			return;
		}

		event.setCanceled(true);
		event.setSwingHand(false);

		if (event.isAttack() && minecraft.gameMode != null) {
			minecraft.gameMode.stopDestroyBlock();
		}
	}

	/// 清理所有 HUD 会话状态；管理器只保留指挥家等 LDLib2 消费者。
	private static void clearHud() {
		HudLayerManager.INSTANCE.clear();
		PlayerStatusHud.INSTANCE.clear();
		TopLeftHud.INSTANCE.clear();
		DamageFilter.INSTANCE.resetSession();
		RationalityFilter.INSTANCE.resetSession();
	}
}
