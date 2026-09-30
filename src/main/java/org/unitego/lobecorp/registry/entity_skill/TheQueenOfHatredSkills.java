package org.unitego.lobecorp.registry.entity_skill;

import net.neoforged.neoforge.registries.DeferredHolder;
import org.unitego.lobecorp.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.RepelSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.SweepSkill;

public interface TheQueenOfHatredSkills {
	DeferredHolder<IEntitySkill<?>, RepelSkill> REPEL = LcEntitySkills.register(
			"the_queen_of_hatred_repel", "Repel", "退散",
			RepelSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(37)
					.durationTicks(0)
					.recoveryTicks(10)
					.cooldownTicks(0));
	DeferredHolder<IEntitySkill<?>, SweepSkill> SWEEP = LcEntitySkills.register(
			"the_queen_of_hatred_sweep", "Sweep", "横扫",
			SweepSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(10)
					.durationTicks(0)
					.recoveryTicks(15)
					.cooldownTicks(40));

	/// 强制初始化憎恶女皇技能注册声明。
	static void init() {
	}

}
