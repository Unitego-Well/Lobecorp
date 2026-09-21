package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.registry.tag.LcEntitySkillTags;

/// 使用通用施法动画和位移打断规则的憎恶皇后法术基类。
public abstract class TheQueenOfHatredSpellSkill extends TheQueenOfHatredSkill {
	/// 所有法术共用的前摇时间。
	public static final int WINDUP_TICKS = 12;
	/// 所有法术共用的后摇时间。
	public static final int RECOVERY_TICKS = 12;

	/// @param properties 法术的基础技能配置
	protected TheQueenOfHatredSpellSkill(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canBeOverridden(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime,
			IEntitySkill<?> replacement) {
		if (runtime.state() == EntitySkillRuntime.SkillState.ACTIVE) {
			return false;
		}
		return replacement.is(LcEntitySkillTags.MOVEMENT);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.playActionAnimation(TheQueenOfHatredAnim.SPELL);
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.playActionAnimation(TheQueenOfHatredAnim.SPELL_ACTIVE);
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.playActionAnimation(TheQueenOfHatredAnim.SPELL_RECOVERY);
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		stopAnimation(entity);
	}

	@Override
	public void onCancel(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		stopAnimation(entity);
	}

	private void stopAnimation(TheQueenOfHatred entity) {
		entity.stopActionAnimation();
	}
}
