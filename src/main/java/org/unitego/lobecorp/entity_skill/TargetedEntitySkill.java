package org.unitego.lobecorp.entity_skill;

import net.minecraft.world.entity.LivingEntity;

public abstract class TargetedEntitySkill<T extends LivingEntity> extends EntitySkill<T> {
	protected TargetedEntitySkill(Properties properties) {
		super(properties);
	}

}
