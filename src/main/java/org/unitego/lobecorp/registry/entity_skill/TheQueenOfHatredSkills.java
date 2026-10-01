package org.unitego.lobecorp.registry.entity_skill;

import net.neoforged.neoforge.registries.DeferredHolder;
import org.unitego.lobecorp.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity_skill.EntitySkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.AttackSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.RepelSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.SweepSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.SpinSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.LaserSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.StarBeamSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.DamageReductionSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.PurificationSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.SlownessSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.MarkSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.StarfallSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.PillarOfLightSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.BlinkSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.TeleportSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.DashSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.RefractionSkill;
import org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred.ConvergentSkill;

public interface TheQueenOfHatredSkills {
	/// 每段普通攻击的前摇时长，单位为 tick。
	int ATTACK_WINDUP_TICKS = 9;
	/// attack 和 attack2 动画均为 0.625 秒，按 20 tick/秒向上取整为 13 tick。
	int ATTACK_ANIMATION_TICKS = 13;
	/// 两段普通攻击共用一个技能，成功命中并完成后摇后切换段数。
	DeferredHolder<IEntitySkill<?>, AttackSkill> ATTACK = LcEntitySkills.register(
			"the_queen_of_hatred_attack", "Normal Attack", "普通攻击",
			AttackSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(ATTACK_WINDUP_TICKS)
					.durationTicks(0)
					.recoveryTicks(ATTACK_ANIMATION_TICKS - ATTACK_WINDUP_TICKS)
					.cooldownTicks(0));
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
					.windupTicks(20)
					.durationTicks(0)
					.recoveryTicks(15)
					.cooldownTicks(40));

	/// 回旋前摇时长，单位为游戏刻；结束时结算近战并发射星星。
	int SPIN_WINDUP_TICKS = 15;
	/// spin 动画总长两秒，单位为游戏刻。
	int SPIN_ANIMATION_TICKS = 40;
	/// 回旋从成功开始施放时计时的冷却，单位为游戏刻。
	int SPIN_COOLDOWN_TICKS = 40;
	DeferredHolder<IEntitySkill<?>, SpinSkill> SPIN = LcEntitySkills.register(
			"the_queen_of_hatred_spin", "Spin", "回旋",
			SpinSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(SPIN_WINDUP_TICKS)
					.durationTicks(0)
					.recoveryTicks(SPIN_ANIMATION_TICKS - SPIN_WINDUP_TICKS)
					.cooldownTicks(SPIN_COOLDOWN_TICKS));

	/// 星束瞄准前摇，单位为游戏刻。
	int STAR_BEAM_WINDUP_TICKS = 20;
	/// 星束发射后的收招时间，单位为游戏刻。
	int STAR_BEAM_RECOVERY_TICKS = 10;
	/// 星束从成功开始施放时计时的冷却，单位为游戏刻。
	int STAR_BEAM_COOLDOWN_TICKS = 60;
	DeferredHolder<IEntitySkill<?>, StarBeamSkill> STAR_BEAM = LcEntitySkills.register(
			"the_queen_of_hatred_star_beam", "Star Beam", "星束",
			StarBeamSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(STAR_BEAM_WINDUP_TICKS)
					.durationTicks(0)
					.recoveryTicks(STAR_BEAM_RECOVERY_TICKS)
					.cooldownTicks(STAR_BEAM_COOLDOWN_TICKS));

	/// 激光发射持续 200 tick，正常完成显式重新计时 200 tick，取消或首次命中超时为 60 tick。
	DeferredHolder<IEntitySkill<?>, LaserSkill> LASER = LcEntitySkills.register(
			"the_queen_of_hatred_laser", "Laser", "激光", LaserSkill::new,
			properties -> properties.locksNavigation().locksMovement()
					.windupTicks(20).durationTicks(200).recoveryTicks(10).cooldownTicks(200));
	/// 以下法术共用 20 tick 前摇和 10 tick 后摇，效果在前摇结束时生效。
	DeferredHolder<IEntitySkill<?>, DamageReductionSkill> DAMAGE_REDUCTION = LcEntitySkills.register(
			"the_queen_of_hatred_damage_reduction", "Damage Reduction", "减伤", DamageReductionSkill::new,
			properties -> spellProperties(properties).cooldownTicks(400));
	DeferredHolder<IEntitySkill<?>, PurificationSkill> PURIFICATION = LcEntitySkills.register(
			"the_queen_of_hatred_purification", "Purification", "净化", PurificationSkill::new,
			properties -> spellProperties(properties).cooldownTicks(400));
	DeferredHolder<IEntitySkill<?>, SlownessSkill> SLOWNESS = LcEntitySkills.register(
			"the_queen_of_hatred_slowness", "Slowness", "迟缓", SlownessSkill::new,
			properties -> spellProperties(properties).cooldownTicks(400));
	DeferredHolder<IEntitySkill<?>, MarkSkill> MARK = LcEntitySkills.register(
			"the_queen_of_hatred_mark", "Mark", "标记", MarkSkill::new,
			properties -> spellProperties(properties).cooldownTicks(400));
	/// 星陨效果独立持续 200 tick，不占用女皇后续行动。
	DeferredHolder<IEntitySkill<?>, StarfallSkill> STARFALL = LcEntitySkills.register(
			"the_queen_of_hatred_starfall", "Starfall", "星陨", StarfallSkill::new,
			properties -> spellProperties(properties).cooldownTicks(900));
	/// 光柱命中后显式开始 160 tick 冷却，落空或取消时为 60 tick。
	DeferredHolder<IEntitySkill<?>, PillarOfLightSkill> PILLAR_OF_LIGHT = LcEntitySkills.register(
			"the_queen_of_hatred_pillar_of_light", "Pillar of Light", "光柱", PillarOfLightSkill::new,
			properties -> spellProperties(properties).cooldownTicks(160));

	/// 瞬步无前摇，位移持续 4 tick、后摇保持 6 tick。
	DeferredHolder<IEntitySkill<?>, BlinkSkill> BLINK = LcEntitySkills.register(
			"the_queen_of_hatred_blink", "Blink", "瞬步",
			BlinkSkill::new, TheQueenOfHatredSkills::movementProperties);

	/// 传送前后摇各 10 tick，冷却从成功开始施放时计时。
	DeferredHolder<IEntitySkill<?>, TeleportSkill> TELEPORT = LcEntitySkills.register(
			"the_queen_of_hatred_teleport", "Teleport", "传送",
			TeleportSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(10)
					.durationTicks(0)
					.recoveryTicks(10)
					.cooldownTicks(80));
	/// 二阶段冲刺与瞬步共用时序和冷却，每个实际移动 tick 留下一枚中型星星。
	DeferredHolder<IEntitySkill<?>, DashSkill> DASH = LcEntitySkills.register(
			"the_queen_of_hatred_dash", "Dash", "冲刺",
			DashSkill::new, TheQueenOfHatredSkills::movementProperties);
	/// 折射使用小型星星，命中后的每条分支最多分裂四代。
	DeferredHolder<IEntitySkill<?>, RefractionSkill> REFRACTION = LcEntitySkills.register(
			"the_queen_of_hatred_efraction", "Refraction", "折射",
			RefractionSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(15)
					.durationTicks(0)
					.recoveryTicks(10)
					.cooldownTicks(60));
	/// 聚爆持续 60 tick，均匀分布六次强吸引；冷却包含前摇、持续和后摇。
	DeferredHolder<IEntitySkill<?>, ConvergentSkill> CONVERGENT = LcEntitySkills.register(
			"the_queen_of_hatred_convergent", "Convergent Burst", "聚爆",
			ConvergentSkill::new, properties -> properties
					.locksNavigation()
					.locksMovement()
					.windupTicks(20)
					.durationTicks(60)
					.recoveryTicks(20)
					.cooldownTicks(160));

	/// 瞬步与冲刺均无前摇，位移 4 tick、后摇 6 tick、冷却 40 tick。
	private static EntitySkill.Properties movementProperties(EntitySkill.Properties properties) {
		return properties.locksNavigation().locksMovement()
				.windupTicks(0).durationTicks(4).recoveryTicks(6).cooldownTicks(40);
	}

	/// 瞬时法术共用的施法时序；冷却由每个注册项配置。
	private static EntitySkill.Properties spellProperties(EntitySkill.Properties properties) {
		return properties.locksNavigation().locksMovement().windupTicks(20).durationTicks(0).recoveryTicks(10);
	}

	/// 强制初始化憎恶女皇技能注册声明。
	static void init() {
	}

}
