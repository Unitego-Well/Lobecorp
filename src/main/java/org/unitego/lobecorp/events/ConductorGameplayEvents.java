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
import org.unitego.lobecorp.world.entity.combat.CombatEvents;
import org.unitego.lobecorp.world.horde.HordeParticipant;
import org.unitego.lobecorp.world.horde.HordeRuntime;
import org.unitego.lobecorp.registry.entity.skill.SweeperSkills;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class ConductorGameplayEvents {
	@SubscribeEvent
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Mob mob) {
			if (HordeParticipant.isEvent(mob)) {
				if (!HordeRuntime.get(level.getServer()).join(mob)) {
					event.setCanceled(true);
				}
				return;
			}
			ConductorData.get(level.getServer()).attach(mob);
		}
	}

	@SubscribeEvent
	public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
		ConductorEvents.onPlayerLogin(event);
		EntityDataSyncEvents.onPlayerLogin(event);
	}

	@SubscribeEvent
	public static void onTargetChange(LivingChangeTargetEvent event) {
		if (event.getEntity() instanceof Mob mob && HordeParticipant.isEvent(mob)
				&& event.getNewAboutToBeSetTarget() != HordeRuntime.assignedTarget(mob)) {
			event.setCanceled(true);
			return;
		}
		CombatEvents.onTargetChange(event);
		ConductorEvents.onTargetChange(event);
	}

	@SubscribeEvent
	public static void onSkillCast(EntitySkillEvent.Cast event) {
		if (HordeParticipant.isEvent(event.getEntity()) && (HordeParticipant.get(event.getEntity()).fading()
				|| event.getSkill() == SweeperSkills.SWEEP.get() || event.getSkill() == SweeperSkills.REASSEMBLE.get())) {
			event.setCanceled(true);
			return;
		}
		ConductorEvents.onSkillCast(event);
	}
}
