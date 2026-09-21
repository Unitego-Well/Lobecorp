package org.unitego.lobecorp.registry.entity_skill;

import net.neoforged.neoforge.registries.DeferredHolder;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredBlinkSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredDispelSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredSweepSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredSpinSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredLaserSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredHealSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredDamageReductionSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredSpellSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredPurificationSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredSlownessSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredMarkSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredStarfallSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredPillarOfLightSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredTeleportSkill;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 憎恶皇后的实体技能注册。
public interface TheQueenOfHatredSkills {
	/// 瞬步：四 tick 冲刺、五 tick 后摇，并按实际位移计算一至五秒冷却。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredBlinkSkill> BLINK = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_blink", "Blink", "瞬步",
			TheQueenOfHatredBlinkSkill::new, properties -> properties
					.locksNavigation()
					.windupTicks(0)
					.durationTicks(4)
					.recoveryTicks(5)
					.cooldownTicks(TICKS_PER_SECOND));

	/// 传送：二十二 tick 前摇，完成后立即进入三百 tick 冷却。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredTeleportSkill> TELEPORT = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_teleport", "Teleport", "传送",
			TheQueenOfHatredTeleportSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(22)
					.durationTicks(0)
					.recoveryTicks(0)
					.cooldownTicks(15 * TICKS_PER_SECOND));

	/// 横扫：第十八至二十 tick 依次发射三颗魔法星星，动画总长二十五 tick。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredSweepSkill> SWEEP = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_sweep", "Sweep", "横扫",
			TheQueenOfHatredSweepSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(17)
					.durationTicks(3)
					.recoveryTicks(5)
					.cooldownTicks(TheQueenOfHatredSweepSkill.COOLDOWN_TICKS));

	/// 退散：十八至三十六 tick 持续攻击前方，第四十四 tick 产生范围爆发。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredDispelSkill> DISPEL = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_dispel", "Dispel", "退散",
			TheQueenOfHatredDispelSkill::new, properties -> properties
					.windupTicks(17)
					.durationTicks(27)
					.recoveryTicks(10)
					.cooldownTicks(TheQueenOfHatredDispelSkill.COOLDOWN_TICKS));

	/// 转圈：十四 tick 前摇、十五 tick 主动阶段和十八 tick 后摇。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredSpinSkill> SPIN = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_spin", "Spin", "转圈",
			TheQueenOfHatredSpinSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(14)
					.durationTicks(15)
					.recoveryTicks(18)
					.cooldownTicks(TheQueenOfHatredSpinSkill.COOLDOWN_TICKS));

	/// 激光：二十四 tick 前摇、持续发射和二十四 tick 后摇。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredLaserSkill> LASER = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_laser", "Laser", "激光",
			TheQueenOfHatredLaserSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(24)
					.durationTicks(-1)
					.recoveryTicks(24)
					.cooldownTicks(5 * TICKS_PER_SECOND));

	/// 柔光：二十 tick 前摇、五秒持续治疗和二十 tick 后摇。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredHealSkill> HEAL = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_heal", "Soft Light", "柔光",
			TheQueenOfHatredHealSkill::new, properties -> properties
					.windupTicks(TICKS_PER_SECOND)
					.durationTicks(5 * TICKS_PER_SECOND)
					.recoveryTicks(TICKS_PER_SECOND)
					.cooldownTicks(30 * TICKS_PER_SECOND));

	/// 减伤：十二 tick 前摇、二 tick 施法和十二 tick 后摇，完成后获得五秒减伤。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredDamageReductionSkill> DAMAGE_REDUCTION = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_damage_reduction", "Damage Reduction", "减伤",
			TheQueenOfHatredDamageReductionSkill::new, properties -> properties
					.windupTicks(TheQueenOfHatredSpellSkill.WINDUP_TICKS)
					.durationTicks(TheQueenOfHatredDamageReductionSkill.CAST_TICKS)
					.recoveryTicks(TheQueenOfHatredSpellSkill.RECOVERY_TICKS)
					.cooldownTicks(TheQueenOfHatredDamageReductionSkill.COOLDOWN_TICKS));

	/// 净化：十二 tick 前摇、二 tick 施法和十二 tick 后摇，完成后清除负面状态。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredPurificationSkill> PURIFICATION = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_purification", "Purification", "净化",
			TheQueenOfHatredPurificationSkill::new, properties -> properties
					.windupTicks(TheQueenOfHatredSpellSkill.WINDUP_TICKS)
					.durationTicks(TheQueenOfHatredPurificationSkill.CAST_TICKS)
					.recoveryTicks(TheQueenOfHatredSpellSkill.RECOVERY_TICKS)
					.cooldownTicks(TheQueenOfHatredPurificationSkill.COOLDOWN_TICKS));

	/// 迟缓：十二 tick 前摇后立即释放范围缓慢，并进入十二 tick 后摇。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredSlownessSkill> SLOWNESS = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_slowness", "Slowness", "迟缓",
			TheQueenOfHatredSlownessSkill::new, properties -> properties
					.windupTicks(TheQueenOfHatredSpellSkill.WINDUP_TICKS)
					.durationTicks(0)
					.recoveryTicks(TheQueenOfHatredSpellSkill.RECOVERY_TICKS)
					.cooldownTicks(TheQueenOfHatredSlownessSkill.COOLDOWN_TICKS));

	/// 标记：十二 tick 前摇后为当前攻击目标施加一分钟憎恶标记。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredMarkSkill> MARK = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_mark", "Mark", "标记",
			TheQueenOfHatredMarkSkill::new, properties -> properties
					.windupTicks(TheQueenOfHatredSpellSkill.WINDUP_TICKS)
					.durationTicks(0)
					.recoveryTicks(TheQueenOfHatredSpellSkill.RECOVERY_TICKS)
					.cooldownTicks(TheQueenOfHatredMarkSkill.COOLDOWN_TICKS));

	/// 星陨：十二 tick 前摇后持续十秒生成星星，再进入十二 tick 后摇。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredStarfallSkill> STARFALL = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_starfall", "Starfall", "星陨",
			TheQueenOfHatredStarfallSkill::new, properties -> properties
					.windupTicks(TheQueenOfHatredSpellSkill.WINDUP_TICKS)
					.durationTicks(TheQueenOfHatredStarfallSkill.CAST_TICKS)
					.recoveryTicks(TheQueenOfHatredSpellSkill.RECOVERY_TICKS)
					.cooldownTicks(TheQueenOfHatredStarfallSkill.COOLDOWN_TICKS));

	/// 光柱：十二 tick 前摇后在锁定位置造成一次伤害并显示两秒光柱。
	DeferredHolder<IEntitySkill<?>, TheQueenOfHatredPillarOfLightSkill> PILLAR_OF_LIGHT = LcEntitySkills.register(
			LcEntitySkills.REGISTER, "the_queen_of_hatred_pillar_of_light", "Pillar of Light", "光柱",
			TheQueenOfHatredPillarOfLightSkill::new, properties -> properties
					.windupTicks(TheQueenOfHatredSpellSkill.WINDUP_TICKS)
					.durationTicks(0)
					.recoveryTicks(TheQueenOfHatredSpellSkill.RECOVERY_TICKS)
					.cooldownTicks(TheQueenOfHatredPillarOfLightSkill.COOLDOWN_TICKS));

	/// 强制初始化本接口，使技能声明在延迟注册器绑定前完成。
	static void init() {
	}
}
