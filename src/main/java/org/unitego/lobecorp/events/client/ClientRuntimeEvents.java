package org.unitego.lobecorp.events.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.registry.LcSoundEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.hud.event.MidnightCleanupCinematic;
import org.unitego.lobecorp.client.horde.HordeEventAtmosphere;
import net.neoforged.bus.api.EventPriority;
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
import org.unitego.lobecorp.client.horde.HordeGroundCache;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.unitego.lobecorp.world.effect.StunMobEffect;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.client.entity.renderer.EntityCorpseRenderer;
import org.unitego.lobecorp.world.item.animation.LcAttachedItems;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE, value = Dist.CLIENT)
public class ClientRuntimeEvents {
	private static double landingAt = Double.NEGATIVE_INFINITY;
	private static float landingPower;
	private static ResourceKey<Level> landingDimension;

	@SubscribeEvent
	public static void onGroundChunkLoad(ChunkEvent.Load event) {
		if (event.getLevel() == Minecraft.getInstance().level) {
			var position = event.getChunk().getPos();
			HordeGroundCache.invalidateChunk(position.x(), position.z());
		}
	}

	@SubscribeEvent
	public static void onGroundChunkUnload(ChunkEvent.Unload event) {
		if (event.getLevel() == Minecraft.getInstance().level) {
			var position = event.getChunk().getPos();
			HordeGroundCache.invalidateChunk(position.x(), position.z());
		}
	}

	@SubscribeEvent
	public static void onSweeperLandingSound(PlaySoundEvent event) {
		var sound = event.getSound();
		var minecraft = Minecraft.getInstance();
		if (sound == null || HordeAmbient.ambientSound(sound) || minecraft.level == null
				|| !HordeVisualConfig.LANDING_SHAKE.get()
				|| !sound.getIdentifier().equals(LcSoundEvents.SWEEPER_LAND.get().location())) {
			return;
		}
		var camera = minecraft.gameRenderer.getMainCamera();
		if (!camera.isInitialized()) {
			return;
		}
		double distance = camera.position().distanceTo(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
		float power = (float) Math.clamp(1 - distance / HordeVisualConfig.LANDING_SHAKE_RANGE.get(), 0, 1);
		if (power <= 0) {
			return;
		}
		double now = minecraft.level.getGameTime();
		float remaining = landingDimension == minecraft.level.dimension()
				? landingPower * (float) Math.clamp(1 - (now - landingAt) / 6, 0, 1) : 0;
		landingPower = Math.max(remaining, power);
		landingAt = now;
		landingDimension = minecraft.level.dimension();
	}

	@SubscribeEvent
	public static void onSweeperCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		MidnightCleanupCinematic.camera(event);

		var minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		float partial = (float) event.getPartialTick();
		double now = minecraft.level.getGameTime() + partial;
		float scale = HordeVisualConfig.SHAKE_STRENGTH.get().floatValue();
		float wave = HordeVisualConfig.EVENT_SHAKE.get() ? HordeAmbient.shakeStrength(partial) : 0;
		double age = now - landingAt;
		float impact = HordeVisualConfig.LANDING_SHAKE.get() && landingDimension == minecraft.level.dimension()
				&& age >= 0 ? landingPower * (float) Math.clamp(1 - age / 6, 0, 1) * scale : 0;
		if (wave <= 0 && impact <= 0) {
			return;
		}
		double hitAge = impact > 0 ? age : 0;
		float cap = 0.8F * scale;
		event.setPitch(event.getPitch() + Math.clamp((float) Math.sin(now * 2.2) * 0.2F * wave
				+ (float) Math.sin(hitAge * 2.8) * 0.8F * impact, -cap, cap));
		event.setYaw(event.getYaw() + Math.clamp((float) Math.sin(now * 1.5) * 0.1F * wave
				+ (float) Math.sin(hitAge * 2.1) * 0.2F * impact, -cap, cap));
		event.setRoll(event.getRoll() + Math.clamp((float) Math.sin(now * 1.8) * 0.35F * wave
				+ (float) Math.sin(hitAge * 2.5) * 0.4F * impact, -cap, cap));
	}


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
		landingAt = Double.NEGATIVE_INFINITY;
		landingPower = 0;
		landingDimension = null;
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

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onVisualFrameEnd(RenderFrameEvent.Post event) {
		HordeEventAtmosphere.frameEnd();
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
		MidnightCleanupCinematic.holdView();
		HordeClient.tick();
		HordeAmbient.tick();
	}

	@SubscribeEvent
	public static void onCinematicFov(ViewportEvent.ComputeFov event) {
		MidnightCleanupCinematic.zoomFov(event);
	}

	@SubscribeEvent
	public static void onFovModifier(ComputeFovModifierEvent event) {
		MidnightCleanupCinematic.correctFov(event);
	}

	@SubscribeEvent
	public static void onPlayerTurn(CalculatePlayerTurnEvent event) {
		if (MidnightCleanupCinematic.controlsView()) {
			// 原版按(sensitivity*0.6F+0.2F)的三次方缩放输入，抵消常数后实际转向为零。
			event.setMouseSensitivity(-0.2F / (double) 0.6F);
			event.setCinematicCameraEnabled(false);
			MidnightCleanupCinematic.holdView();
		}
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
		landingAt = Double.NEGATIVE_INFINITY;
		landingPower = 0;
		landingDimension = null;
		HudLayerManager.INSTANCE.clear();
		PlayerStatusHud.INSTANCE.clear();
		TopLeftHud.INSTANCE.clear();
		DamageFilter.INSTANCE.resetSession();
		RationalityFilter.INSTANCE.resetSession();
	}
}
