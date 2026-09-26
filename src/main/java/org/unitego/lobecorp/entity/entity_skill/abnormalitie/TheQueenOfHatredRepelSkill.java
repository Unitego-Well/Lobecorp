package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.server.level.ServerLevel;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.entity_skill.EntitySkill;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.effect.EntitySkillEffectManager;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 憎恶皇后的环形退散冲击波。
public final class TheQueenOfHatredRepelSkill extends EntitySkill<TheQueenOfHatred> {
	/// 技能运行结束后再次施放前的等待时间，单位为游戏刻。
	private static final int REUSE_DELAY_TICKS = TICKS_PER_SECOND;
	public TheQueenOfHatredRepelSkill(Properties properties) {
		super(properties);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.playActionAnimation(TheQueenOfHatredAnim.DISPEL);
		queen.lockSkillFacing();
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (queen.level() instanceof ServerLevel level) {
			EntitySkillEffectManager.add(new TheQueenOfHatredRepelWaveEffect(queen, level, runtime));
		}
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
		queen.delayNextSkillCast(REUSE_DELAY_TICKS);
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}
}
