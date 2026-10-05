package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill;

import net.minecraft.server.level.ServerLevel;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.skill.EntitySkill;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.entity.skill.QueenSkillUtil;
import net.minecraft.world.effect.MobEffectInstance;
import org.unitego.lobecorp.registry.effect.LcMobEffects;

/// 施法结束获得限时减伤，复用既有受伤倍率属性与效果同步。
public class DamageReductionSkill extends EntitySkill<TheQueenOfHatred> {
	/// 减伤持续五秒，单位为游戏 tick。
	public static final int EFFECT_TICKS = 100;

	public DamageReductionSkill(Properties properties) {
		super(properties);
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (queen.level() instanceof ServerLevel
				&& queen.addEffect(new MobEffectInstance(LcMobEffects.QUEEN_DAMAGE_REDUCTION, EFFECT_TICKS)))
			runtime.markSuccessful();
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		QueenSkillUtil.startSpell(queen);
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
	}

	@Override
	public void onEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}
}

