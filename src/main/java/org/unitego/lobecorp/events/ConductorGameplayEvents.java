package org.unitego.lobecorp.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.conductor.lifecycle.ConductorEvents;
import org.unitego.lobecorp.event.EntitySkillEvent;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class ConductorGameplayEvents {
	@SubscribeEvent
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Mob mob) {
			ConductorData.get(level.getServer()).attach(mob);
		}
	}

	@SubscribeEvent
	public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		ConductorEvents.onPlayerLogin(event);
	}

	@SubscribeEvent
	public static void onTargetChange(LivingChangeTargetEvent event) {
		ConductorEvents.onTargetChange(event);
	}

	@SubscribeEvent
	public static void onSkillCast(EntitySkillEvent.Cast event) {
		ConductorEvents.onSkillCast(event);
	}
}
