package org.unitego.lobecorp.registry.entity_skill;

import net.neoforged.neoforge.registries.DeferredHolder;
import org.unitego.lobecorp.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.AttackSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.LeapSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.ReassembleSkill;
import org.unitego.lobecorp.entity_skill.skill.sweeper.SweepSkill;

/// 清道夫
public interface SweeperSkills {
	/// 清理结束动画及淡出期间保持技能占用的后摇 tick。
	int CLEANUP_RECOVERY_TICKS = 15;

	/// 普通攻击
	DeferredHolder<IEntitySkill<?>, AttackSkill> ATTACK = LcEntitySkills.register(
			"sweeper_attack", "Normal Attack", "普通攻击",
			AttackSkill::new, p -> p
					.locksNavigation()
					.locksMovement()
					.windupTicks(6)
					.durationTicks(0)
					.recoveryTicks(14)
					.cooldownTicks(0));
	/// 飞扑
	DeferredHolder<IEntitySkill<?>, LeapSkill> LEAP = LcEntitySkills.register(
			"sweeper_leap", "Leap", "飞扑",
			LeapSkill::new, p -> p
					.locksNavigation()
					.windupTicks(5)
					.durationTicks(-1)
					.recoveryTicks(10)
					.cooldownTicks(5 * 20));
	/// 清扫
	DeferredHolder<IEntitySkill<?>, SweepSkill> SWEEP = LcEntitySkills.register(
			"sweeper_sweep", "Sweep", "清扫",
			SweepSkill::new, p -> p
					.locksNavigation()
					.locksMovement()
					.windupTicks(17)
					.durationTicks(-1)
					.recoveryTicks(CLEANUP_RECOVERY_TICKS)
					.cooldownTicks(10));
	/// 重组
	DeferredHolder<IEntitySkill<?>, ReassembleSkill> REASSEMBLE = LcEntitySkills.register(
			"sweeper_reassemble", "Reassemble", "重组",
			ReassembleSkill::new, p -> p
					.locksNavigation()
					.locksMovement()
					.windupTicks(17)
					.durationTicks(-1)
					.recoveryTicks(CLEANUP_RECOVERY_TICKS)
					.cooldownTicks(10));

	/// 强制初始化清道夫技能注册声明。
	static void init() {
	}

}
