package org.unitego.lobecorp.events;

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
		HitboxEvents.onLevelTick(event);
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		ConductorChunkLoading.onServerTick(event);
	}
}
