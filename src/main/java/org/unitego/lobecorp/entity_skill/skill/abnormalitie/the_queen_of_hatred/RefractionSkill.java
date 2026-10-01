package org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.projectile.MagicStarProjectile;
import org.unitego.lobecorp.entity_skill.EntitySkill;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.util.EntityFacingUtil;

/// 发射小型星星，碰撞后的分裂与追踪由现有魔法星弹射物处理。
public class RefractionSkill extends EntitySkill<TheQueenOfHatred> {
	/// 每条分支最多分裂四代，最后一代只伤害、不继续分裂。
	private static final int MAXIMUM_SPLIT_GENERATIONS = 4;

	public RefractionSkill(Properties properties) {
		super(properties);
	}

	/// 射程沿用现有星星的追踪范围，单位为格。
	public static double range() {
		return MagicStarProjectile.homingRange();
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target == null && runtime.targetPosition() == null) {
			target = queen.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
			if (target != null) runtime.setTarget(target);
		}
		return target != null ? target.isAlive() && queen.isHatedTarget(target) && queen.hasLineOfSight(target)
				&& queen.distanceToSqr(target) <= range() * range() : runtime.targetPosition() != null;
	}

	@Override
	public boolean prepareAim(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return EntityFacingUtil.aim(queen, EntityFacingUtil.targetPosition(queen, runtime, true), true);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.playActionAnimation(TheQueenOfHatredAnim.KISS);
		queen.lockSkillFacing();
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level)) return;
		Vec3 direction = queen.getLookAngle();
		Vec3 origin = queen.getEyePosition().add(direction.scale(queen.getBbWidth()));
		MagicStarProjectile star = new MagicStarProjectile(AbnormalitieEntityTypes.MAGIC_NORMAL_STAR.get(), level);
		star.configure(queen, MagicStarProjectile.StarSize.SMALL, MagicStarProjectile.StarVariant.A, origin, direction);
		star.setRefraction(MAXIMUM_SPLIT_GENERATIONS, null);
		level.addFreshEntity(star);
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
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}
}
