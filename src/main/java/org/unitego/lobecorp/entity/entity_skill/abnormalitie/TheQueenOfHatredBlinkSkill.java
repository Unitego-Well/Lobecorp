package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.TypedDataKey;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 瞬步：在四 tick 内沿可碰撞路径高速接近目标或向侧后方闪避。
public class TheQueenOfHatredBlinkSkill extends TheQueenOfHatredSkill {
	/// 单次瞬步允许移动的最大水平距离。
	public static final double MAXIMUM_DISTANCE = 10.0;
	/// 瞬步动态冷却的最低时间。
	private static final int MINIMUM_COOLDOWN_TICKS = TICKS_PER_SECOND;
	/// 每移动一格增加的冷却时间。
	private static final double COOLDOWN_TICKS_PER_BLOCK = TICKS_PER_SECOND * 0.5;
	/// 瞬步动态冷却的最高时间。
	private static final int MAXIMUM_COOLDOWN_TICKS = 5 * TICKS_PER_SECOND;
	/// 本次瞬步开始时的位置。
	private static final TypedDataKey<Vec3> START_POSITION = TypedDataKey.create();
	/// 本次瞬步采用的直线落点。
	private static final TypedDataKey<Vec3> DESTINATION = TypedDataKey.create();

	/// @param properties 瞬步的基础技能配置
	public TheQueenOfHatredBlinkSkill(Properties properties) {
		super(properties);
	}

	@Override
	public TheQueenOfHatredAttackMode attackMode() {
		return TheQueenOfHatredAttackMode.REPOSITION;
	}

	@Override
	public double minimumPlanningRange() {
		return 0.0;
	}

	@Override
	public double maximumPlanningRange() {
		return 24.0;
	}

	@Override
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = entity.getAttackTarget();
		boolean retreat = entity.consumeRetreatBlinkRequest();
		if (target == null && !retreat) {
			return false;
		}
		if (target != null && !entity.isValidTarget(target)) {
			return false;
		}
		Vec3 destination = entity.consumeRepositionDestination();
		if (destination == null) {
			return false;
		}
		runtime.setTarget(target);
		runtime.setData(DESTINATION, destination);
		runtime.setPlannedMovement(destination.subtract(entity.position()));
		return true;
	}

	@Override
	public void onWindupStart(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		runtime.setData(START_POSITION, entity.position());
		entity.resetFallDistance();
		entity.playActionAnimation(TheQueenOfHatredAnim.BLINK);
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.resetFallDistance();
		Vec3 destination = runtime.getData(DESTINATION);
		if (destination == null) {
			return;
		}
		int duration = Math.max(1, durationTicks());
		Vec3 movement = destination.subtract(entity.position());
		double distance = movement.length();
		if (distance > Mth.EPSILON) {
			double movementDistance = Math.min(distance, MAXIMUM_DISTANCE / duration);
			entity.move(MoverType.SELF, movement.scale(movementDistance / distance));
		}
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target != null) {
			entity.faceTarget(target);
		}
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		updateCooldown(entity, runtime);
		entity.setDeltaMovement(Vec3.ZERO);
		entity.resetFallDistance();
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.stopActionAnimation();
	}

	@Override
	public void onCancel(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		updateCooldown(entity, runtime);
		entity.setDeltaMovement(Vec3.ZERO);
		entity.stopActionAnimation();
	}

	private void updateCooldown(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (entity.consumeThreatBlinkCooldownRequest()) {
			runtime.setCooldownTicks(TICKS_PER_SECOND);
			return;
		}
		Vec3 startPosition = runtime.getData(START_POSITION);
		if (startPosition == null) {
			runtime.setCooldownTicks(MINIMUM_COOLDOWN_TICKS);
			return;
		}
		double movedDistance = entity.position().subtract(startPosition).horizontalDistance();
		int cooldownTicks = Mth.ceil(MINIMUM_COOLDOWN_TICKS + movedDistance * COOLDOWN_TICKS_PER_BLOCK);
		runtime.setCooldownTicks(Mth.clamp(cooldownTicks, MINIMUM_COOLDOWN_TICKS, MAXIMUM_COOLDOWN_TICKS));
	}

}
