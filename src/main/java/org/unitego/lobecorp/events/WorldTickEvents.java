package org.unitego.lobecorp.events;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.unitego.lobecorp.animation.runtime.LcServerAnimations;
import org.unitego.lobecorp.attachment.LcAttachments;
import org.unitego.lobecorp.world.item.animation.LcAttachedItems;
import org.unitego.lobecorp.network.tc.animation.LcAnimationSync;
import org.unitego.lobecorp.particle.text.TextParticles;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.world.ConductorChunkLoading;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class WorldTickEvents {
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onLevelTick(LevelTickEvent.Post event) {
		EntitySkillEffectEvents.onLevelTick(event);

		if (event.getLevel() instanceof ServerLevel serverLevel) {
			LcServerAnimations.finishTick(serverLevel);
			LcAttachedItems.tick(serverLevel);
			LcAttachments.tick(serverLevel);
		}

		HitboxEvents.onLevelTick(event);

		if (event.getLevel() instanceof ServerLevel serverLevel) {
			LcAnimationSync.sync(serverLevel);
			TextParticles.tick(serverLevel);
		}
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		ConductorChunkLoading.onServerTick(event);
		LcServerAnimations.prune(event.getServer()).forEach(LcAnimationSync::clear);
		LcAttachments.prune(event.getServer()).forEach(level -> {
			LcAttachedItems.clear(level);
			LcAnimationSync.clear(level);
		});
		LcAttachedItems.prune(event.getServer());
	}

	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		try {
			LcServerAnimations.clear(event.getServer());
		} finally {
			LcAttachments.clear(event.getServer());
			LcAttachedItems.clear(event.getServer());
			LcAnimationSync.clear(event.getServer());
			TextParticles.clear(event.getServer());
		}
	}
}
