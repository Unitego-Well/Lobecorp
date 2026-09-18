package org.unitego.lobecorp.registry.entity_skill;

import net.neoforged.neoforge.registries.DeferredHolder;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity.entity_skill.sweeper.SweeperAttackSkill;
import org.unitego.lobecorp.entity.entity_skill.sweeper.SweeperDisposeCorpseSkill;
import org.unitego.lobecorp.entity.entity_skill.sweeper.SweeperLeapSkill;

/// 清道夫
public interface SweeperSkills {
	/// 普通攻击
	DeferredHolder<IEntitySkill<?>, SweeperAttackSkill> ATTACK = LcEntitySkills.register("sweeper_attack",
			SweeperAttackSkill::new, p -> p
					.windupTicks(6)
					.durationTicks(0)
					.recoveryTicks(14)
					.cooldownTicks(0));
	/// 飞扑
	DeferredHolder<IEntitySkill<?>, SweeperLeapSkill> LEAP = LcEntitySkills.register("sweeper_leap",
			SweeperLeapSkill::new, p -> p
					.locksNavigation()
					.windupTicks(5)
					.durationTicks(-1)
					.recoveryTicks(10)
					.cooldownTicks(100));
	/// 清理尸体
	DeferredHolder<IEntitySkill<?>, SweeperDisposeCorpseSkill> DISPOSE_CORPSE = LcEntitySkills.register("sweeper_dispose_corpse",
			SweeperDisposeCorpseSkill::new, p -> p
					.locksNavigation()
					.locksMovement()
					.windupTicks(17)
					.durationTicks(-1)
					.recoveryTicks(10)
					.cooldownTicks(0));

	static void init() {
	}
}
