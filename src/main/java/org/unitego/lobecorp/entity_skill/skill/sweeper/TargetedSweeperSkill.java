package org.unitego.lobecorp.entity_skill.skill.sweeper;

import net.minecraft.world.entity.LivingEntity;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity_skill.TargetedEntitySkill;

public abstract class TargetedSweeperSkill extends TargetedEntitySkill<Sweeper> {
	protected TargetedSweeperSkill(Properties properties) {
		super(properties);
	}

	protected LivingEntity getTarget(EntitySkillRuntime<Sweeper> runtime) {
		return runtime.target(LivingEntity.class);
	}
}
