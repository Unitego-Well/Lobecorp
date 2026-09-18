package org.unitego.lobecorp.entity.entity_skill.sweeper;

import net.minecraft.world.entity.LivingEntity;
import org.unitego.lobecorp.entity.entity_skill.EntitySkill;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;

public abstract class SweeperSkill extends EntitySkill<Sweeper> {
	public SweeperSkill(Properties properties) {
		super(properties);
	}

	protected LivingEntity getTarget(EntitySkillRuntime<Sweeper> runtime) {
		return runtime.target(LivingEntity.class);
	}
}
