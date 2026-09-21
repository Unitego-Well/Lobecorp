package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 减伤：完成施法后暂时降低憎恶皇后受到的伤害。
public class TheQueenOfHatredDamageReductionSkill extends TheQueenOfHatredSpellSkill {
	/// 战斗决策选择减伤法术的概率。
	public static final float AI_CAST_CHANCE = 0.25F;
	/// 减伤法术的主动施法时间。
	public static final int CAST_TICKS = 2;
	/// 减伤法术的独立冷却时间。
	public static final int COOLDOWN_TICKS = 20 * TICKS_PER_SECOND;
	/// 减伤效果的持续时间。
	private static final int EFFECT_DURATION_TICKS = 5 * TICKS_PER_SECOND;

	/// @param properties 减伤法术的基础技能配置
	public TheQueenOfHatredDamageReductionSkill(Properties properties) {
		super(properties);
	}

	@Override
	public float planningChance() {
		return AI_CAST_CHANCE;
	}

	@Override
	public TheQueenOfHatredAttackMode attackMode() {
		return TheQueenOfHatredAttackMode.DEFENSIVE_RETREAT;
	}

	@Override
	public double minimumPlanningRange() {
		return 0.0;
	}

	@Override
	public double maximumPlanningRange() {
		return Double.POSITIVE_INFINITY;
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onActivate(entity, runtime);
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onEnd(entity, runtime);
		entity.startDamageReduction(EFFECT_DURATION_TICKS);
	}
}
