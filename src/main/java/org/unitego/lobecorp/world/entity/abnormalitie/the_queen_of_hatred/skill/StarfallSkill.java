package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill;

import net.minecraft.server.level.ServerLevel;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.skill.EntitySkill;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.entity.skill.QueenSkillUtil;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.world.entity.skill.effect.EntitySkillEffectManager;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.effect.QueenStarfallEffect;
import org.unitego.lobecorp.particle.QueenMagicCircleParticleOptions;

/// 前摇结束锁定星陨区域；效果独立于施放实例，后摇完成即可继续行动。
public class StarfallSkill extends EntitySkill<TheQueenOfHatred> {
	/// 实体或位置目标的最大施法距离，单位为格。
	public static final double RANGE = 20.0;
	/// 星星随机落点区域半径，单位为格。
	public static final double RADIUS = 2.5;
	/// 星陨生成持续十秒，单位为 tick。
	public static final int EFFECT_TICKS = 200;
	/// 一阶段星星生成间隔，二阶段减半，单位为 tick。
	public static final int SPAWN_INTERVAL_TICKS = 10;

	public StarfallSkill(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return QueenSkillUtil.canTarget(queen, runtime, RANGE);
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level))
			return;
		Vec3 position = QueenSkillUtil.primaryPosition(queen, runtime, RANGE);
		if (position == null)
			return;
		EntitySkillEffectManager.add(new QueenStarfallEffect(level, position, queen, queen.isPhaseTwo()));
		QueenMagicCircleParticleOptions.send(level, position, RADIUS, EFFECT_TICKS, false);
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
