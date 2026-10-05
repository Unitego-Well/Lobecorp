package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredAnim;
import org.unitego.lobecorp.world.entity.skill.EntitySkill;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.entity.EntityFacingUtil;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;

/// 瞬步沿已瞄准的水平方向冲刺，由原版实体移动处理方块碰撞。
public class BlinkSkill extends EntitySkill<TheQueenOfHatred> {
	/// 瞬步一次施放的最大水平距离，单位为格。
	public static final double MAXIMUM_DISTANCE = 10.0;
	/// 判断水平位移被方块阻挡的误差容限，单位为格的平方。
	private static final double COLLISION_EPSILON = 1.0E-6;
	/// 保持脚下碰撞检测的向下位移，单位为格；上台阶高度仍由实体自身决定。
	private static final double GROUND_CONTACT_OFFSET = 1.0E-5;

	public BlinkSkill(Properties properties) {
		super(properties);
	}

	/// 子技能可保留自己的移动距离，不随瞬步配置变化。
	public double maximumDistance() {
		return MAXIMUM_DISTANCE;
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Vec3 target = EntityFacingUtil.targetPosition(queen, runtime, false);
		return target != null && target.subtract(queen.position()).horizontalDistanceSqr() > 0.0;
	}

	@Override
	public boolean prepareAim(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Vec3 target = EntityFacingUtil.targetPosition(queen, runtime, false);
		return target != null && EntityFacingUtil.aim(queen, target, false);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.playActionAnimation(TheQueenOfHatredAnim.BLINK);
		queen.lockSkillFacing();
		Vec3 target = EntityFacingUtil.targetPosition(queen, runtime, false);
		if (target != null) {
			double distance = Math.min(maximumDistance(), target.subtract(queen.position()).horizontalDistance());
			runtime.setPlannedMovement(EntityFacingUtil.horizontalDirection(queen, target).scale(distance / durationTicks()));
		}
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
		Vec3 movement = runtime.plannedMovement();
		if (movement == null)
			return;
		Vec3 before = queen.position();
		queen.setDeltaMovement(Vec3.ZERO);
		queen.move(MoverType.SELF, movement.add(0.0, -GROUND_CONTACT_OFFSET, 0.0));
		if (queen.horizontalCollision || queen.position().subtract(before).horizontalDistanceSqr()
				+ COLLISION_EPSILON < movement.horizontalDistanceSqr()) {
			EntitySkillUtil.endSkill(queen, this);
		}
	}

	@Override
	public void onEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.stopActionAnimation();
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.setDeltaMovement(Vec3.ZERO);
		queen.stopActionAnimation();
	}
}
