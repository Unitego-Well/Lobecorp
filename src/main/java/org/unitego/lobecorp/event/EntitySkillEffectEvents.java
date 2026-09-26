package org.unitego.lobecorp.event;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.entity_skill.effect.EntitySkillEffectManager;

@EventBusSubscriber(modid = Lobecorp.NAMESPACE)
public class EntitySkillEffectEvents {
	@SubscribeEvent(priority = EventPriority.HIGH)
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (event.getLevel() instanceof ServerLevel serverLevel) {
			EntitySkillEffectManager.tick(serverLevel);
		}
	}
}
