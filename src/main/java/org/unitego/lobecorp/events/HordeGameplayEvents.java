package org.unitego.lobecorp.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.event.EntitySkillEvent;
import org.unitego.lobecorp.world.horde.HordeEvents;
import org.unitego.lobecorp.world.horde.HordeSavedData;
import org.unitego.lobecorp.world.horde.HordeParticipant;
import org.unitego.lobecorp.world.horde.HordeRuntime;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class HordeGameplayEvents {
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDeath(LivingDeathEvent event) {
		if (!event.isCanceled() && event.getEntity().level() instanceof ServerLevel level) {
			HordeRuntime.get(level.getServer()).death(event.getEntity());
		}
	}

	@SubscribeEvent
	public static void onStarted(EntitySkillEvent.Started event) {
		if (event.getEntity() instanceof Mob mob && mob.level() instanceof ServerLevel level
				&& HordeParticipant.isEvent(mob)) {
			var ownerEvent = HordeSavedData.get(level.getServer()).events().get(HordeParticipant.get(mob).eventUuid());
			var profile = ownerEvent == null ? null : HordeEvents.profile(ownerEvent.profile());
			if (profile != null && profile.startsCombat(event.getSkill())) {
				HordeRuntime.get(level.getServer()).engage(mob, HordeRuntime.assignedTarget(mob));
			}
		}
	}

	@SubscribeEvent
	public static void onStopping(ServerStoppingEvent event) {
		HordeRuntime.stopping(event.getServer());
	}
}
