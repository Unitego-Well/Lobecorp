package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.util.EntityPurificationUtil;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 净化：完成施法后移除自身全部负面状态和有害属性修饰符。
public class TheQueenOfHatredPurificationSkill extends TheQueenOfHatredSpellSkill {
	/// 战斗决策选择净化法术的概率。
	public static final float AI_CAST_CHANCE = 0.25F;
	/// 净化法术的主动施法时间。
	public static final int CAST_TICKS = 2;
	/// 净化法术的独立冷却时间。
	public static final int COOLDOWN_TICKS = 20 * TICKS_PER_SECOND;

	/// @param properties 净化法术的基础技能配置
	public TheQueenOfHatredPurificationSkill(Properties properties) {
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
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return EntityPurificationUtil.needsPurification(entity);
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
		EntityPurificationUtil.purify(entity);
	}
}
