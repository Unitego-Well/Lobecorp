package org.unitego.lobecorp.entity_skill.skill.sweeper;

import net.minecraft.world.entity.LivingEntity;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity_skill.EntitySkill;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;

public abstract class SweeperSkill extends EntitySkill<Sweeper> {
	/// 重组每 tick 处理的尸体生命值
	protected static final float PROCESS_HEALTH = 2.0F;

	public SweeperSkill(Properties properties) {
		super(properties);
	}

	protected LivingEntity getTarget(EntitySkillRuntime<Sweeper> runtime) {
		return runtime.target(LivingEntity.class);
	}
}
