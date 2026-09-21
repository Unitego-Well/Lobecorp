package org.unitego.lobecorp.registry.entity_skill;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity.entity_skill.sweeper.SweeperAttackSkill;
import org.unitego.lobecorp.entity.entity_skill.sweeper.SweeperLeapSkill;
import org.unitego.lobecorp.entity.entity_skill.sweeper.SweeperReassembleSkill;
import org.unitego.lobecorp.entity.entity_skill.sweeper.SweeperSweepSkill;
import org.unitego.lobecorp.registry.LcRegistrys;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 清道夫
public interface SweeperSkills {
	/// 清理类技能结束后的冷却 tick。
	int CLEANUP_COOLDOWN_TICKS = 10;
	DeferredRegister<IEntitySkill<?>> REGISTER = Lobecorp.register(LcRegistrys.ENTITY_SKILL_KEY);

	/// 普通攻击
	DeferredHolder<IEntitySkill<?>, SweeperAttackSkill> ATTACK = LcEntitySkills.register(REGISTER,
			"sweeper_attack", "Normal Attack", "普通攻击",
			SweeperAttackSkill::new, p -> p
					.locksNavigation()
					.locksMovement()
					.windupTicks(6)
					.durationTicks(0)
					.recoveryTicks(14)
					.cooldownTicks(0));
	/// 飞扑
	DeferredHolder<IEntitySkill<?>, SweeperLeapSkill> LEAP = LcEntitySkills.register(REGISTER,
			"sweeper_leap", "Leap", "飞扑",
			SweeperLeapSkill::new, p -> p
					.locksNavigation()
					.windupTicks(5)
					.durationTicks(-1)
					.recoveryTicks(10)
					.cooldownTicks(5 * TICKS_PER_SECOND));
	/// 清扫
	DeferredHolder<IEntitySkill<?>, SweeperSweepSkill> SWEEP = LcEntitySkills.register(REGISTER,
			"sweeper_sweep", "Sweep", "清扫",
			SweeperSweepSkill::new, p -> p
					.locksNavigation()
					.locksMovement()
					.windupTicks(17)
					.durationTicks(-1)
					.recoveryTicks(10)
					.cooldownTicks(CLEANUP_COOLDOWN_TICKS));
	/// 重组
	DeferredHolder<IEntitySkill<?>, SweeperReassembleSkill> REASSEMBLE = LcEntitySkills.register(REGISTER,
			"sweeper_reassemble", "Reassemble", "重组",
			SweeperReassembleSkill::new, p -> p
					.locksNavigation()
					.locksMovement()
					.windupTicks(17)
					.durationTicks(-1)
					.recoveryTicks(10)
					.cooldownTicks(CLEANUP_COOLDOWN_TICKS));

	static void init(IEventBus iEventBus) {
		REGISTER.register(iEventBus);
	}
}
