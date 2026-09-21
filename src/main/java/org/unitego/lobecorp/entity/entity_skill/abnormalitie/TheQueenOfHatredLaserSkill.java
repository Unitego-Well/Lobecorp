package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredLaser;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.tag.LcEntitySkillTags;
import org.unitego.lobecorp.util.TypedDataKey;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 激光：动态瞄准并持续发射会随首次命中时间强化的光束。
public class TheQueenOfHatredLaserSkill extends TheQueenOfHatredSkill {
	/// AI 使用激光的最小距离。
	public static final double MINIMUM_USE_RANGE = 7.0;
	/// AI 使用激光的最大距离。
	public static final double MAXIMUM_USE_RANGE = 20.0;
	/// 发射阶段等待首次有效命中的时间。
	private static final int FIRST_HIT_TIMEOUT_TICKS = 3 * TICKS_PER_SECOND;
	/// 首次命中至强化的时间。
	private static final int ENHANCEMENT_TICKS = 5 * TICKS_PER_SECOND;
	/// 强化光束持续时间；与强化前阶段合计保持 8 秒命中持续时间。
	private static final int ENHANCED_DURATION_TICKS = 3 * TICKS_PER_SECOND;
	/// 发射阶段每 tick 最大三维转向角。
	private static final double MAXIMUM_TURN_DEGREES = 4.0;
	/// 激光预测目标交汇位置时使用的响应速度。
	private static final double TARGET_PREDICTION_SPEED = 2.0;
	/// 接近地面时优先水平瞄准的最大离地距离。
	private static final double HORIZONTAL_AIM_GROUND_DISTANCE = 1.0;
	/// 激光起点相对脚底的高度。
	private static final double ORIGIN_HEIGHT = 1.35;
	/// 激光起点沿发射方向的前向偏移。
	private static final double ORIGIN_FORWARD_OFFSET = 1.25;
	/// 激光起点沿实体右侧的偏移。
	private static final double ORIGIN_RIGHT_OFFSET = 0.15;
	/// 被取消或首次命中超时后的冷却。
	private static final int CANCELLED_COOLDOWN_TICKS = 3 * TICKS_PER_SECOND;
	/// 正常完成后的冷却。
	private static final int COMPLETED_COOLDOWN_TICKS = 10 * TICKS_PER_SECOND;
	/// 本次技能创建的激光实体编号。
	private static final TypedDataKey<Integer> LASER_ENTITY_ID = TypedDataKey.create();
	/// 本次技能当前使用的发射方向。
	private static final TypedDataKey<Vec3> DIRECTION = TypedDataKey.create();

	/// @param properties 激光的基础技能配置
	public TheQueenOfHatredLaserSkill(Properties properties) {
		super(properties);
	}

	@Override
	public TheQueenOfHatredAttackMode attackMode() {
		return TheQueenOfHatredAttackMode.LASER_LOCK;
	}

	@Override
	public double minimumPlanningRange() {
		return MINIMUM_USE_RANGE;
	}

	@Override
	public double maximumPlanningRange() {
		return MAXIMUM_USE_RANGE;
	}

	@Override
	public double planningDistance(TheQueenOfHatred entity, LivingEntity target) {
		Vec3 origin = origin(entity, entity.getLookAngle().normalize());
		return origin.distanceTo(closestPoint(target, origin));
	}

	@Override
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = entity.getAttackTarget();
		if (target == null || !entity.isValidTarget(target)) {
			return false;
		}
		Vec3 origin = origin(entity, entity.getLookAngle().normalize());
		double distanceSquared = origin.distanceToSqr(closestPoint(target, origin));
		return distanceSquared >= MINIMUM_USE_RANGE * MINIMUM_USE_RANGE
				&& distanceSquared <= MAXIMUM_USE_RANGE * MAXIMUM_USE_RANGE;
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
	public boolean canBeCancelled(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return runtime.state() != EntitySkillRuntime.SkillState.ACTIVE;
	}

	@Override
	public void onWindupStart(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		runtime.setCooldownTicks(CANCELLED_COOLDOWN_TICKS);
		runtime.setTarget(entity.getAttackTarget());
		entity.playActionAnimation(TheQueenOfHatredAnim.AIM);
	}

	@Override
	public void onWindupTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target != null && entity.isValidTarget(target)) {
			entity.faceTarget(target);
		}
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 direction = entity.getLookAngle().normalize();
		TheQueenOfHatredLaser laser = new TheQueenOfHatredLaser(
				AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_LASER.get(), level);
		laser.setOwner(entity);
		laser.setDirection(direction);
		laser.setPos(origin(entity, direction));
		level.addFreshEntity(laser);
		runtime.setData(DIRECTION, direction);
		runtime.setData(LASER_ENTITY_ID, laser.getId());
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		TheQueenOfHatredLaser laser = laser(entity, runtime);
		if (laser == null) {
			EntitySkillManager.endSkill(entity, this);
			return;
		}
		if (!laser.isEnhanced() && !hasNearbyEnemy(entity)) {
			runtime.setCooldownTicks(COMPLETED_COOLDOWN_TICKS);
			EntitySkillManager.endSkill(entity, this);
			return;
		}
		if (!laser.hasHit() && runtime.activeTicks() >= FIRST_HIT_TIMEOUT_TICKS) {
			runtime.setCooldownTicks(CANCELLED_COOLDOWN_TICKS);
			EntitySkillManager.endSkill(entity, this);
			return;
		}
		if (laser.hasHit()) {
			long hitTicks = entity.level().getGameTime() - laser.firstHitGameTime();
			if (hitTicks >= ENHANCEMENT_TICKS) {
				laser.setEnhanced(true);
			}
			if (hitTicks >= ENHANCEMENT_TICKS + ENHANCED_DURATION_TICKS) {
				runtime.setCooldownTicks(COMPLETED_COOLDOWN_TICKS);
				EntitySkillManager.endSkill(entity, this);
				return;
			}
		}
		Vec3 direction = runtime.getData(DIRECTION);
		if (direction == null) {
			direction = entity.getLookAngle().normalize();
		}
		direction = aimDirection(entity, runtime, direction);
		runtime.setData(DIRECTION, direction);
		faceDirection(entity, direction);
		laser.setDirection(direction);
		laser.setPos(origin(entity, direction));
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		removeLaser(entity, runtime);
		entity.playActionAnimation(TheQueenOfHatredAnim.AIM_RECOVERY);
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.stopActionAnimation();
	}

	@Override
	public void onCancel(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		runtime.setCooldownTicks(CANCELLED_COOLDOWN_TICKS);
		removeLaser(entity, runtime);
		onRecoveryEnd(entity, runtime);
	}

	private Vec3 aimDirection(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime, Vec3 current) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target == null || !entity.isValidTarget(target)) {
			return current;
		}
		Vec3 origin = origin(entity, current);
		Vec3 predictedPosition = entity.predictTargetPosition(target, origin, TARGET_PREDICTION_SPEED);
		Vec3 targetCenter = target.getBoundingBox().getCenter();
		Vec3 targetPosition = new Vec3(predictedPosition.x, targetCenter.y, predictedPosition.z);
		if (isNearGround(entity) && targetPosition.y < origin.y) {
			targetPosition = new Vec3(targetPosition.x, origin.y, targetPosition.z);
		}
		Vec3 desired = targetPosition.subtract(origin).normalize();
		double angle = Math.acos(Mth.clamp(current.dot(desired), -1.0, 1.0));
		double maximumTurn = Math.toRadians(MAXIMUM_TURN_DEGREES);
		if (angle <= maximumTurn) {
			return desired;
		}
		double blend = maximumTurn / angle;
		return current.scale(1.0 - blend).add(desired.scale(blend)).normalize();
	}

	private boolean isNearGround(TheQueenOfHatred entity) {
		Vec3 start = entity.position();
		Vec3 end = start.subtract(0.0, HORIZONTAL_AIM_GROUND_DISTANCE, 0.0);
		return entity.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, entity)).getType() != HitResult.Type.MISS;
	}

	/// 非强化阶段是否仍有位于激光使用范围内的有效敌人。
	private boolean hasNearbyEnemy(TheQueenOfHatred entity) {
		double rangeSquared = MAXIMUM_USE_RANGE * MAXIMUM_USE_RANGE;
		AABB bounds = entity.getBoundingBox().inflate(MAXIMUM_USE_RANGE);
		return entity.level().getEntitiesOfClass(LivingEntity.class, bounds,
						target -> entity.isValidTarget(target)
								&& entity.distanceToSqr(target) <= rangeSquared)
				.stream()
				.findAny()
				.isPresent();
	}

	private void faceDirection(TheQueenOfHatred entity, Vec3 direction) {
		if (direction.lengthSqr() == 0.0) {
			return;
		}
		float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
		entity.setYRot(yaw);
		entity.setYHeadRot(yaw);
		entity.setYBodyRot(yaw);
		entity.setXRot(pitch);
	}

	private Vec3 origin(TheQueenOfHatred entity, Vec3 direction) {
		Vec3 horizontal = new Vec3(direction.x, 0.0, direction.z);
		if (horizontal.lengthSqr() == 0.0) {
			horizontal = Vec3.directionFromRotation(0.0F, entity.getYRot());
		}
		horizontal = horizontal.normalize();
		Vec3 right = new Vec3(horizontal.z, 0.0, -horizontal.x);
        return entity.position().add(0.0, ORIGIN_HEIGHT, 0.0)
                .add(horizontal.scale(ORIGIN_FORWARD_OFFSET))
                .add(right.scale(ORIGIN_RIGHT_OFFSET));
	}

	private Vec3 closestPoint(LivingEntity target, Vec3 origin) {
		return new Vec3(
				Mth.clamp(origin.x, target.getBoundingBox().minX, target.getBoundingBox().maxX),
				Mth.clamp(origin.y, target.getBoundingBox().minY, target.getBoundingBox().maxY),
				Mth.clamp(origin.z, target.getBoundingBox().minZ, target.getBoundingBox().maxZ));
	}

	private TheQueenOfHatredLaser laser(TheQueenOfHatred entity,
			EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Integer id = runtime.getData(LASER_ENTITY_ID);
		if (id == null || !(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getEntity(id) instanceof TheQueenOfHatredLaser laser ? laser : null;
	}

	private void removeLaser(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		TheQueenOfHatredLaser laser = laser(entity, runtime);
		runtime.removeData(LASER_ENTITY_ID);
		if (laser != null) {
			laser.discard();
		}
	}
}
