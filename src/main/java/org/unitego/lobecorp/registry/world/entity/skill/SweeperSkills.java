package org.unitego.lobecorp.registry.world.entity.skill;

import net.neoforged.neoforge.registries.DeferredHolder;
import org.unitego.lobecorp.world.entity.ordeal.indigo.skill.AttackSkill;
import org.unitego.lobecorp.world.entity.ordeal.indigo.skill.LeapSkill;
import org.unitego.lobecorp.world.entity.ordeal.indigo.skill.ReassembleSkill;
import org.unitego.lobecorp.world.entity.ordeal.indigo.skill.SweepSkill;
import org.unitego.lobecorp.world.entity.skill.IEntitySkill;

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

	/// 清扫进行声为原每12刻的2.2倍：每60刻11次，用整数累计保持5/6刻节奏。
	static boolean cleanupSoundTick(int activeTick) {
		return activeTick > 0 && (long) activeTick * 11 / 60 != (long) (activeTick - 1) * 11 / 60;
	}

	/// 强制初始化清道夫技能注册声明。
	static void init() {
	}

}
