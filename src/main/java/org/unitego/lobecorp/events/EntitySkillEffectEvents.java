package org.unitego.lobecorp.events;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.unitego.lobecorp.entity_skill.effect.EntitySkillEffectManager;

public class EntitySkillEffectEvents {
	public static void onLevelTick(LevelTickEvent.Post event) {
		if (event.getLevel() instanceof ServerLevel serverLevel) {
			EntitySkillEffectManager.tick(serverLevel);
		}
	}
}
