package org.unitego.lobecorp.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.network.tc.animation.LcAnimationSync;
import org.unitego.lobecorp.util.conductor.ConductorAttachmentUtil;
import org.unitego.lobecorp.util.entity.skill.EntitySkillDataSyncUtil;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class EntityDataSyncEvents {
	@SubscribeEvent
	public static void onStartTracking(PlayerEvent.StartTracking event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			LcAnimationSync.track(player, event.getTarget());
		}

		if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity entity) {
			sendInitial(player, entity);
		}
	}

	@SubscribeEvent
	public static void onStopTracking(PlayerEvent.StopTracking event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			LcAnimationSync.untrack(player, event.getTarget());
		}
	}

	public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		sendSelf(event);
	}

	@SubscribeEvent
	public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
		sendSelf(event);
	}

	@SubscribeEvent
	public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
		sendSelf(event);
	}

	private static void sendSelf(PlayerEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			sendInitial(player, player);
		}
	}

	private static void sendInitial(ServerPlayer player, LivingEntity entity) {
		EntitySkillDataSyncUtil.sendInitial(player, entity);
		ConductorAttachmentUtil.sendInitial(player, entity);
	}
}
