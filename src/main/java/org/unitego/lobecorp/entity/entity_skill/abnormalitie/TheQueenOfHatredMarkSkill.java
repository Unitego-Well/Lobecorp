package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.registry.effect.LcMobEffects;
import org.unitego.lobecorp.util.TypedDataKey;

import java.util.List;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 标记：使当前攻击目标在伤害处理开始前承受额外伤害。
public class TheQueenOfHatredMarkSkill extends TheQueenOfHatredSpellSkill {
	/// AI 选择标记法术的概率。
	public static final float AI_CAST_CHANCE = 0.25F;
	/// 标记能够锁定当前攻击目标的最大距离。
	private static final double USE_RANGE = 15.0;
	/// 使用距离的平方缓存。
	private static final double USE_RANGE_SQUARED = USE_RANGE * USE_RANGE;
	/// 一级状态效果使用的原版增幅值。
	private static final int EFFECT_AMPLIFIER = 0;
	/// 憎恶标记的持续时间。
	private static final int EFFECT_DURATION_TICKS = 60 * TICKS_PER_SECOND;
	/// 标记法术的独立冷却时间。
	public static final int COOLDOWN_TICKS = 20 * TICKS_PER_SECOND;
	private static final TypedDataKey<List<LivingEntity>> TARGETS = TypedDataKey.create();

	/// @param properties 标记法术的基础技能配置
	public TheQueenOfHatredMarkSkill(Properties properties) {
		super(properties);
	}

	@Override
	public float planningChance() {
		return AI_CAST_CHANCE;
	}

	@Override
	public TheQueenOfHatredAttackMode attackMode() {
		return TheQueenOfHatredAttackMode.LONG_RANGE_CAST;
	}

	@Override
	public double minimumPlanningRange() {
		return 7.0;
	}

	@Override
	public double maximumPlanningRange() {
		return USE_RANGE;
	}

	@Override
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = entity.getAttackTarget();
		if (target == null || !entity.isValidTarget(target)) {
			return false;
		}
		if (entity.distanceToSqr(target) > USE_RANGE_SQUARED) {
			return false;
		}
		if (!entity.hasLineOfSight(target)) {
			return false;
		}
		runtime.setTarget(target);
		runtime.setData(TARGETS, selectPhaseTargets(entity, target, USE_RANGE, true));
		return true;
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onActivate(entity, runtime);
		List<LivingEntity> locked = runtime.getData(TARGETS);
		for (LivingEntity target : refreshPhaseTargets(entity, locked == null ? List.of() : locked,
				USE_RANGE, true)) {
			target.addEffect(new MobEffectInstance(
					LcMobEffects.HATRED_MARK, EFFECT_DURATION_TICKS, EFFECT_AMPLIFIER), entity);
		}
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onEnd(entity, runtime);
	}
}
