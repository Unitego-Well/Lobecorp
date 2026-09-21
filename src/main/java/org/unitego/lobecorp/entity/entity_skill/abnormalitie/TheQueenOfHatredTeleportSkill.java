package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 传送：经过可分段打断的前摇后，移动到接近目标或远离敌群的安全落点。
public class TheQueenOfHatredTeleportSkill extends TheQueenOfHatredSkill {
	/// 单次传送允许搜索的最大距离。
	public static final double MAXIMUM_DISTANCE = 40.0;
	/// 撤离传送必须产生的最小水平位移。
	private static final double MINIMUM_RETREAT_DISTANCE = 16.0;
	/// 传送前摇允许普通打断的 tick 数。
	private static final int INTERRUPTIBLE_WINDUP_TICKS = TICKS_PER_SECOND / 2;

	/// @param properties 传送的基础技能配置
	public TheQueenOfHatredTeleportSkill(Properties properties) {
		super(properties);
	}

	@Override
	public TheQueenOfHatredAttackMode attackMode() {
		return TheQueenOfHatredAttackMode.REPOSITION;
	}

	@Override
	public double minimumPlanningRange() {
		return 24.0;
	}

	@Override
	public double maximumPlanningRange() {
		return MAXIMUM_DISTANCE;
	}

	@Override
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!entity.hasTeleportRequest()) {
			return false;
		}
		LivingEntity target = entity.getAttackTarget();
		Vec3 destination = entity.consumeRepositionDestination();
		if (destination == null) {
			return false;
		}
		boolean retreat = entity.isRetreatTeleportRequested();
		if (retreat
				&& destination.subtract(entity.position()).horizontalDistance() < MINIMUM_RETREAT_DISTANCE) {
			return false;
		}
		runtime.setTarget(target);
		runtime.setPlannedMovement(destination);
		return true;
	}

	@Override
	public boolean canInterruptDuringWindup(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return windupTicks() - runtime.ticksLeft() < INTERRUPTIBLE_WINDUP_TICKS;
	}

	@Override
	public void onWindupStart(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.clearTeleportRequest();
		entity.resetFallDistance();
		entity.playActionAnimation(TheQueenOfHatredAnim.TELEPORT);
	}

	@Override
	public void onWindupTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.resetFallDistance();
		if (entity.isForcedSecondPhaseTeleport()) {
			entity.setDeltaMovement(Vec3.ZERO);
			return;
		}
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target != null) {
			entity.lookAt(target, 30.0F, 30.0F);
		}
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.resetFallDistance();
		Vec3 destination = runtime.plannedMovement();
		if (destination != null) {
			entity.absSnapTo(destination.x, destination.y, destination.z, entity.getYRot(), entity.getXRot());
			entity.setDeltaMovement(Vec3.ZERO);
		}
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target != null) {
			entity.faceTarget(target);
		}
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.stopActionAnimation();
	}

	@Override
	public void onCancel(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.clearTeleportRequest();
		entity.stopActionAnimation();
	}

}
