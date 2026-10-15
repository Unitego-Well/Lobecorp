package org.unitego.lobecorp.registry.entity.skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.unitego.lobecorp.registry.LcCapabilities;
import org.unitego.lobecorp.world.entity.skill.EntitySkillAttachmentAccess;
import org.unitego.lobecorp.world.entity.skill.IEntitySkillHolder;

/// 实体技能能力的唯一注册入口。
public final class EntitySkillCapabilities {
	private EntitySkillCapabilities() {
	}

	public static void register(RegisterCapabilitiesEvent event) {
		for (var type : BuiltInRegistries.ENTITY_TYPE) {
			event.registerEntity(LcCapabilities.ENTITY_SKILL, type,
					(entity, _) -> {
						if (!(entity instanceof LivingEntity living)) {
							return null;
						}

						if (!(living instanceof IEntitySkillHolder)) {
							return null;
						}

						return new EntitySkillAttachmentAccess(living);
					});
		}
	}
}
