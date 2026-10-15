package org.unitego.lobecorp.world.entity.ordeal.indigo.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.ordeal.indigo.SweeperAnim;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.entity.EntityFacingUtil;
import org.unitego.lobecorp.util.entity.EntityGrabUtil;
import org.unitego.lobecorp.util.entity.EntityLeapUtil;
import org.unitego.lobecorp.util.entity.EntityLeapUtil.Trajectory;
import org.unitego.lobecorp.world.entity.skill.EntitySkill;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.world.hitbox.HitboxInstance;
import org.unitego.lobecorp.world.hitbox.HitboxManager;
import org.unitego.lobecorp.world.hitbox.HitboxTemplate;
import org.unitego.lobecorp.world.hitbox.geometry.SphereSize;
import org.unitego.lobecorp.world.hitbox.geometry.BoxSize;
import org.unitego.lobecorp.world.hitbox.HitboxEffectContext;
import org.unitego.lobecorp.registry.effect.LcMobEffects;
import org.unitego.lobecorp.registry.entity.state.SweeperStates;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.util.TypedDataKey;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;
import static org.unitego.lobecorp.world.hitbox.HitboxManager.CURRENT_TICK_DURATION;

/// 清道夫飞扑技能：起跳 leap 接触伤害并抓取目标，落地 leap2 时释放抓取并造成范围伤害。
public class LeapSkill extends EntitySkill<Sweeper> {
	/// 落地范围伤害半径
	private static final float DAMAGE_RADIUS = 2.5f;
	/// 落地命中目标时的击飞强度
	private static final double LANDING_KNOCKBACK_POWER = 1.0;
	/// 落地命中施加的眩晕持续时间
	private static final int LANDING_STUN_DURATION_TICKS = 3 * TICKS_PER_SECOND;
	/// 落地命中施加的眩晕增幅等级，对应游戏内一级效果
	private static final int LANDING_STUN_AMPLIFIER = 0;
	/// 允许开始飞扑的最大水平距离
	private static final double MAX_LEAP_DISTANCE = 12.0;
	/// 最大水平距离的平方，用于避免距离判断时开方
	private static final double MAX_LEAP_DISTANCE_SQUARED = MAX_LEAP_DISTANCE * MAX_LEAP_DISTANCE;
	/// 跳跃可达范围比较的平方距离误差，单位为方块平方。
	private static final double RANGE_TOLERANCE_SQUARED = 1.0E-6;
	/// 自动弹道允许使用的最大水平初速度
	private static final double MAX_HORIZONTAL_SPEED = 1.8;
	/// 自动弹道允许使用的最大垂直初速度
	private static final double MAX_VERTICAL_SPEED = 1.4;
	/// 飞行接触造成当前攻击属性的 15% 伤害。
	private static final float FLIGHT_DAMAGE_RATIO = 0.15F;
	/// 飞行伤害与抓取判断框相对当前实体尺寸每侧外扩的距离，单位为格。
	private static final double FLIGHT_HITBOX_PADDING = 0.3;
	/// 飞扑可抓取生物的最大实际高度和宽度，单位为格。
	private static final float MAX_GRAB_SIZE = 2.5F;
	/// 被抓者偏离之前锚点达到此距离即摆脱，单位为格。
	private static final double GRAB_ESCAPE_DISTANCE = 3.0;
	/// 低弧弹道高于起点与落点中较高端的最小高度，单位为格。
	private static final double MIN_ARC_HEIGHT = 1.0;
	/// 最大距离时的目标弧高及允许的最高弧高，单位为格。
	private static final double MAX_ARC_HEIGHT = 2.5;
	/// 落点支撑面相对目标脚底允许搜索的上下距离，兼容半砖、台阶和小幅离地，单位为格。
	private static final double LANDING_SEARCH_HEIGHT = 1.0;
	/// 求解终点向支撑面内探入的距离，确保最后一个 tick 由原版碰撞判定落地，单位为格。
	private static final double LANDING_PENETRATION = 0.01;
	/// 碰撞路径与落点比较的浮点误差，单位为格。
	private static final double COLLISION_EPSILON = 1.0E-6;
	/// 原版空中方块速度因子查询的脚下偏移；未落地时没有支撑块缓存，单位为格。
	private static final float MOVEMENT_BLOCK_BELOW_OFFSET = 0.500001F;
	/// 飞扑期间的击退抗性
	private static final double KNOCKBACK_RESISTANCE = 0.8;
	/// 飞扑击退抗性临时属性的唯一标识
	private static final Identifier KNOCKBACK_RESISTANCE_MODIFIER = Lobecorp.id("sweeper_leap_knockback_resistance");
	/// 飞扑期间临时提高到的安全掉落高度
	private static final double SAFE_FALL_DISTANCE = 12.0;
	/// 飞扑安全掉落高度临时属性的唯一标识
	private static final Identifier SAFE_FALL_DISTANCE_MODIFIER = Lobecorp.id("sweeper_leap_safe_fall_distance");
	/// 飞扑未落地时的最大持续时间
	private static final int MAX_LEAP_TICKS = 2 * TICKS_PER_SECOND;
	/// 飞扑超时后每 tick 保留的水平速度比例
	private static final double FORCED_DESCENT_HORIZONTAL_DRAG = 0.5;
	/// 飞扑超时后保证采用的最大向下速度
	private static final double FORCED_DESCENT_SPEED = -0.5;
	/// 至少经过该 tick 数后才判断落地，避免起跳当 tick 被当作落地
	private static final int MIN_LANDING_CHECK_TICKS = 2;
	/// 起跳时生成的普通烟雾数量
	private static final int TAKEOFF_SMOKE_COUNT = 12;
	/// 起跳时生成的短时烟雾数量
	private static final int TAKEOFF_SHORT_SMOKE_COUNT = 12;
	/// 起跳时生成的灰尘数量
	private static final int TAKEOFF_DUST_COUNT = 8;
	/// 起跳时生成的方块碎屑数量
	private static final int TAKEOFF_BLOCK_DEBRIS_COUNT = 12;
	/// 起跳粒子的水平扩散范围
	private static final double TAKEOFF_PARTICLE_HORIZONTAL_SPREAD = 0.6;
	/// 起跳粒子的垂直扩散范围
	private static final double TAKEOFF_PARTICLE_VERTICAL_SPREAD = 0.1;
	/// 起跳粒子的初始扩散速度
	private static final double TAKEOFF_PARTICLE_SPEED = 0.04;
	/// 落地时生成的普通烟雾数量
	private static final int LANDING_SMOKE_COUNT = 40;
	/// 落地时生成的短时烟雾数量
	private static final int LANDING_SHORT_SMOKE_COUNT = 24;
	/// 落地时生成的灰尘数量
	private static final int LANDING_DUST_COUNT = 16;
	/// 落地时生成的方块碎屑数量
	private static final int LANDING_BLOCK_DEBRIS_COUNT = 30;
	/// 起跳和落地粒子相对实体脚底的高度偏移
	private static final double LANDING_PARTICLE_Y_OFFSET = 0.1;
	/// 落地粒子的水平扩散范围
	private static final double LANDING_PARTICLE_HORIZONTAL_SPREAD = 1.5;
	/// 落地粒子的垂直扩散范围
	private static final double LANDING_PARTICLE_VERTICAL_SPREAD = 0.2;
	/// 落地烟雾的初始扩散速度
	private static final double LANDING_SMOKE_SPEED = 0.05;
	/// 落地灰尘的初始扩散速度
	private static final double LANDING_DUST_SPEED = 0.02;
	/// 落地方块碎屑的初始扩散速度
	private static final double LANDING_BLOCK_DEBRIS_SPEED = 0.1;
	/// 本次飞扑创建的落地判断框编号。
	private static final TypedDataKey<Integer> HITBOX_ID = TypedDataKey.create();
	/// 本次飞扑的飞行接触判断框编号，独立于落地范围伤害。
	private static final TypedDataKey<Integer> FLIGHT_HITBOX_ID = TypedDataKey.create();
	/// 本次飞扑已经接触的生物，失败伤害和摆脱抓取也不允许再次处理。
	private static final TypedDataKey<Set<UUID>> FLIGHT_CONTACTS = TypedDataKey.create();
	/// 施放前的阻力标记，结束、取消或卸载时原样恢复。
	private static final TypedDataKey<Boolean> ORIGINAL_DISCARD_FRICTION = TypedDataKey.create();
	/// 飞行伤害与抓取共用的接触判断框，按清道夫当前尺寸加外扩距离更新。
	private static final HitboxTemplate FLIGHT_HITBOX_TEMPLATE = new HitboxTemplate(
			new BoxSize(0.0, 0.0, 0.0),
			target -> target instanceof LivingEntity,
			LeapSkill::onFlightContact
	);
	/// 飞扑落地范围判断框共享模板。
	private static final HitboxTemplate HITBOX_TEMPLATE = new HitboxTemplate(
			new SphereSize(DAMAGE_RADIUS),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof Sweeper entity)) {
					return false;
				}

				if (!(context.target() instanceof LivingEntity target)) {
					return false;
				}

				if (!entity.doHurtTarget(context.level(), target, 1.0F)) {
					return false;
				}

				target.knockback(LANDING_KNOCKBACK_POWER,
						entity.getX() - target.getX(), entity.getZ() - target.getZ());
				target.addEffect(new MobEffectInstance(LcMobEffects.STUN,
						LANDING_STUN_DURATION_TICKS, LANDING_STUN_AMPLIFIER), entity);
				return true;
			}
	);

	public LeapSkill(Properties properties) {
		super(properties);
	}

	public static double conductorMaximumRange() {
		return MAX_LEAP_DISTANCE;
	}

	public static double conductorDamageRadius() {
		return DAMAGE_RADIUS;
	}

	@Override
	public boolean canUse(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target == null && runtime.targetPosition() == null) {
			target = entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		}

		if (!entity.onGround()) {
			return false;
		}

		if (target == null && runtime.targetPosition() == null) {
			return false;
		}

		if (target != null && !target.isAlive()) {
			return false;
		}

		if (target != null && !entity.isValidTarget(target)) {
			return false;
		}

		if (target != null && AttackSkill.isWithinAttackRange(entity, target)) {
			return false;
		}

		Vec3 targetPosition = target == null ? runtime.targetPosition() : target.position();
		if (horizontalDistanceSqr(entity, targetPosition) > MAX_LEAP_DISTANCE_SQUARED + RANGE_TOLERANCE_SQUARED) {
			return false;
		}

		runtime.setTarget(target);
		return calculateLaunchSolution(entity, targetPosition) != null;
	}

	@Override
	public boolean prepareAim(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		Vec3 position = target == null ? runtime.targetPosition() : target.position();
		LaunchSolution solution = position == null ? null : calculateLaunchSolution(entity, position);
		return solution != null && aimToMovement(entity, solution.trajectory().movement());
	}

	@Override
	public void onWindupStart(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.addEntityState(SweeperStates.LEAP);
		entity.playActionAnimation(SweeperAnim.LEAP);
		updateLaunchSolution(entity, runtime);
		if (entity.level() instanceof ServerLevel level) {
			int lifetime = windupTicks() + MAX_LEAP_TICKS + recoveryTicks() + CURRENT_TICK_DURATION;
			HitboxInstance hitbox = HitboxManager.create(HITBOX_TEMPLATE, level, entity.position(), lifetime, runtime);
			hitbox.follow(entity, Vec3.ZERO, false);
			hitbox.appendTargetFilter(entity::isValidTarget);
			runtime.setData(HITBOX_ID, hitbox.id());
		}
	}

	@Override
	public void onWindupTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target == null && runtime.targetPosition() == null) {
			EntitySkillUtil.forceCancelSkill(entity, this);
			return;
		}

		if (target != null && !target.isAlive()) {
			EntitySkillUtil.forceCancelSkill(entity, this);
			return;
		}

		if (target != null && target.isRemoved()) {
			EntitySkillUtil.forceCancelSkill(entity, this);
			return;
		}

		if (!updateLaunchSolution(entity, runtime)) {
			EntitySkillUtil.forceCancelSkill(entity, this);
		}
	}

	@Override
	public boolean interruptibleDuringWindup() {
		return false;
	}

	@Override
	public void onActivate(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.playActionAnimation(SweeperAnim.LEAP_FLIGHT);
		runtime.setData(ORIGINAL_DISCARD_FRICTION, entity.shouldDiscardFriction());
		entity.setDiscardFriction(true);
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		LivingEntity target = runtime.target(LivingEntity.class);
		Vec3 targetPosition = target == null ? runtime.targetPosition() : target.position();
		if (targetPosition == null || target != null && (!target.isAlive() || !entity.isValidTarget(target))) {
			EntitySkillUtil.forceCancelSkill(entity, this);
			return;
		}

		LaunchSolution solution = calculateLaunchSolution(entity, targetPosition);
		if (solution == null) {
			EntitySkillUtil.forceCancelSkill(entity, this);
			return;
		}

		// 起跳这一刻锁定落点和初速度；飞行中只交给原版重力与碰撞，不再追踪目标。
		runtime.setTargetPosition(solution.landingPosition());
		runtime.setPlannedMovement(solution.trajectory().movement());
		addKnockbackResistance(entity);
		addSafeFallDistance(entity);
		clearMovementInput(entity);
		entity.setOnGround(false);
		entity.setDeltaMovement(solution.trajectory().movement());
		createFlightHitbox(entity, runtime, level);
		spawnTakeoffParticles(level, entity);
	}

	@Override
	public void onTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		clearMovementInput(entity);
		if (entity.level().isClientSide()) {
			lockFacingToMovement(entity, entity.getDeltaMovement());
			return;
		}

		if (runtime.activeTicks() >= MIN_LANDING_CHECK_TICKS && entity.onGround()) {
			EntitySkillUtil.endSkill(entity, this);
			return;
		}

		Vec3 v = entity.getDeltaMovement();
		if (runtime.activeTicks() >= MAX_LEAP_TICKS) {
			v = new Vec3(v.x * FORCED_DESCENT_HORIZONTAL_DRAG,
					Math.min(v.y, FORCED_DESCENT_SPEED), v.z * FORCED_DESCENT_HORIZONTAL_DRAG);
			entity.setDeltaMovement(v);
		}

		lockFacingToMovement(entity, v);
		updateFlightHitbox(entity, runtime);
	}

	@Override
	public void onEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		EntityGrabUtil.releaseAll(entity);
		removeFlightHitbox(entity, runtime);
		restoreFriction(entity, runtime);
		restoreLevelFacing(entity);
		entity.playActionAnimation(SweeperAnim.LEAP2);
		removeKnockbackResistance(entity);
		removeSafeFallDistance(entity);
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		HitboxInstance hitbox = getHitbox(level, runtime);
		if (hitbox != null) {
			hitbox.activate();
			hitbox.setRemainingTicks(CURRENT_TICK_DURATION);
		}

		Vec3 pos = entity.position();
		double particleY = pos.y + LANDING_PARTICLE_Y_OFFSET;
		level.sendParticles(ParticleTypes.POOF, pos.x, particleY, pos.z, LANDING_SMOKE_COUNT,
				LANDING_PARTICLE_HORIZONTAL_SPREAD, LANDING_PARTICLE_VERTICAL_SPREAD,
				LANDING_PARTICLE_HORIZONTAL_SPREAD, LANDING_SMOKE_SPEED);
		level.sendParticles(LcParticleTypes.SHORT_SMOKE.get(), pos.x, particleY, pos.z, LANDING_SHORT_SMOKE_COUNT,
				LANDING_PARTICLE_HORIZONTAL_SPREAD, LANDING_PARTICLE_VERTICAL_SPREAD,
				LANDING_PARTICLE_HORIZONTAL_SPREAD, LANDING_SMOKE_SPEED);
		level.sendParticles(ParticleTypes.ASH, pos.x, particleY, pos.z, LANDING_DUST_COUNT,
				LANDING_PARTICLE_HORIZONTAL_SPREAD, LANDING_PARTICLE_VERTICAL_SPREAD,
				LANDING_PARTICLE_HORIZONTAL_SPREAD, LANDING_DUST_SPEED);
		BlockState blockState = entity.getBlockStateOn();
		if (!blockState.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
					pos.x, particleY, pos.z, LANDING_BLOCK_DEBRIS_COUNT,
					LANDING_PARTICLE_HORIZONTAL_SPREAD, LANDING_PARTICLE_VERTICAL_SPREAD,
					LANDING_PARTICLE_HORIZONTAL_SPREAD, LANDING_BLOCK_DEBRIS_SPEED);
		}
	}

	@Override
	public void onRecoveryEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		restoreFriction(entity, runtime);
		EntityGrabUtil.releaseAll(entity);
		removeFlightHitbox(entity, runtime);
		entity.removeEntityState(SweeperStates.LEAP);
		removeHitbox(entity, runtime);
	}

	@Override
	public void onCancel(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		restoreFriction(entity, runtime);
		restoreLevelFacing(entity);
		EntityGrabUtil.releaseAll(entity);
		removeFlightHitbox(entity, runtime);
		removeKnockbackResistance(entity);
		removeSafeFallDistance(entity);
		entity.removeEntityState(SweeperStates.LEAP);
		entity.stopActionAnimation();
		removeHitbox(entity, runtime);
	}

	private boolean updateLaunchSolution(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		Vec3 position = target == null ? runtime.targetPosition() : target.position();
		LaunchSolution solution = position == null ? null : calculateLaunchSolution(entity, position);
		if (solution == null) {
			return false;
		}

		runtime.setPlannedMovement(solution.trajectory().movement());
		lockFacingToMovement(entity, solution.trajectory().movement());
		return true;
	}

	private @Nullable LaunchSolution calculateLaunchSolution(Sweeper entity, Vec3 target) {
		if (!entity.onGround() || entity.isInWater() || entity.isInLava() || entity.onClimbable()
				|| entity.hasEffect(MobEffects.LEVITATION) || entity.hasEffect(MobEffects.SLOW_FALLING)
				|| horizontalDistanceSqr(entity, target) > MAX_LEAP_DISTANCE_SQUARED + RANGE_TOLERANCE_SQUARED) {
			return null;
		}

		Vec3 landing = findLandingPosition(entity, target);
		if (landing == null) {
			return null;
		}

		Vec3 displacement = landing.subtract(entity.position());
		double desiredArc = Mth.lerp(Mth.clamp(displacement.horizontalDistance() / MAX_LEAP_DISTANCE, 0.0, 1.0),
				MIN_ARC_HEIGHT, MAX_ARC_HEIGHT);
		// 探入只用于触发碰撞，弧高仍以真实支撑面为基准。
		double heightCorrection = Math.max(0.0, displacement.y) - Math.max(0.0, displacement.y - LANDING_PENETRATION);
		Trajectory trajectory = EntityLeapUtil.solve(displacement.subtract(0.0, LANDING_PENETRATION, 0.0),
				MIN_ARC_HEIGHT + heightCorrection, MAX_ARC_HEIGHT + heightCorrection, desiredArc + heightCorrection,
				entity.getGravity(), MAX_HORIZONTAL_SPEED, MAX_VERTICAL_SPEED, MAX_LEAP_TICKS);
		return trajectory != null && hasClearTrajectory(entity, landing, trajectory)
				? new LaunchSolution(landing, trajectory) : null;
	}

	@SuppressWarnings("resource")
	private @Nullable Vec3 findLandingPosition(Sweeper entity, Vec3 target) {
		AABB targetBounds = entity.getBoundingBox().move(target.subtract(entity.position()));
		AABB searchBounds = new AABB(targetBounds.minX, target.y - LANDING_SEARCH_HEIGHT, targetBounds.minZ,
				targetBounds.maxX, target.y + LANDING_SEARCH_HEIGHT, targetBounds.maxZ);
		Vec3 nearest = null;
		double nearestDistance = Double.POSITIVE_INFINITY;
		for (var shape : entity.level().getBlockCollisions(entity, searchBounds)) {
			for (AABB surface : shape.toAabbs()) {
				double height = surface.maxY;
				double distance = Math.abs(height - target.y);
				if (distance > LANDING_SEARCH_HEIGHT || distance >= nearestDistance) {
					continue;
				}

				AABB bounds = targetBounds.move(0.0, height - target.y, 0.0);
				if (!isSupportedLanding(entity, bounds)) {
					continue;
				}

				nearest = new Vec3(target.x, height, target.z);
				nearestDistance = distance;
			}
		}

		return nearest;
	}

	@SuppressWarnings("resource")
	private boolean isSupportedLanding(Sweeper entity, AABB bounds) {
		if (!entity.level().noBlockCollision(entity, bounds.deflate(COLLISION_EPSILON))
				|| !entity.level().noBorderCollision(entity, bounds)
				|| entity.level().containsAnyLiquid(bounds)) {
			return false;
		}

		Vec3 probe = new Vec3(0.0, -LANDING_PENETRATION, 0.0);
		return Entity.collideBoundingBox(entity, probe, bounds, entity.level(), List.of()).y
				> probe.y + COLLISION_EPSILON;
	}

	private boolean hasClearTrajectory(Sweeper entity, Vec3 landing, Trajectory trajectory) {
		AABB bounds = entity.getBoundingBox();
		Vec3 movement = trajectory.movement();
		for (int tick = 1; tick <= trajectory.flightTicks(); tick++) {
			Vec3 allowed = Entity.collideBoundingBox(entity, movement, bounds, entity.level(), List.of());
			if (Math.abs(allowed.x - movement.x) > COLLISION_EPSILON
					|| Math.abs(allowed.z - movement.z) > COLLISION_EPSILON) {
				return false;
			}

			if (tick == trajectory.flightTicks()) {
				return movement.y < 0.0 && allowed.y > movement.y + COLLISION_EPSILON
						&& Math.abs(bounds.minY + allowed.y - landing.y) <= COLLISION_EPSILON;
			}

			if (Math.abs(allowed.y - movement.y) > COLLISION_EPSILON) {
				return false;
			}

			bounds = bounds.move(allowed);
			if (!hasStandardAirPhysics(entity, bounds)) {
				return false;
			}

			movement = movement.subtract(0.0, entity.getGravity(), 0.0);
		}

		return false;
	}

	@SuppressWarnings("resource")
	private boolean hasStandardAirPhysics(Sweeper entity, AABB bounds) {
		if (entity.level().containsAnyLiquid(bounds.deflate(COLLISION_EPSILON))) {
			return false;
		}

		try (var states = entity.level().getBlockStates(bounds.deflate(COLLISION_EPSILON))) {
			if (states.anyMatch(state -> state.is(BlockTags.CLIMBABLE) || state.is(Blocks.COBWEB)
					|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW))) {
				return false;
			}
		}

		Vec3 center = bounds.getCenter();
		BlockPos position = BlockPos.containing(center.x, bounds.minY, center.z);
		BlockState state = entity.level().getBlockState(position);
		if (state.is(BlockTags.CLIMBABLE)) {
			return false;
		}

		float factor = state.getBlock().getSpeedFactor();
		if (factor == 1.0F) {
			BlockPos below = BlockPos.containing(center.x, bounds.minY - MOVEMENT_BLOCK_BELOW_OFFSET, center.z);
			factor = entity.level().getBlockState(below).getBlock().getSpeedFactor();
		}

		float effectiveFactor = Mth.lerp((float) entity.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY), factor, 1.0F);
		return Math.abs(effectiveFactor - 1.0) <= COLLISION_EPSILON;
	}

	private void clearMovementInput(Sweeper entity) {
		entity.getMoveControl().setWait();
		entity.setXxa(0.0F);
		entity.setYya(0.0F);
		entity.setZza(0.0F);
		entity.setJumping(false);
	}

	private void restoreFriction(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		Boolean original = runtime.removeData(ORIGINAL_DISCARD_FRICTION);
		if (original != null) {
			entity.setDiscardFriction(original);
		}
	}

	private void restoreLevelFacing(Sweeper entity) {
		aimToMovement(entity, Vec3.directionFromRotation(0.0F, entity.getYRot()));
	}

	private record LaunchSolution(Vec3 landingPosition, Trajectory trajectory) {
	}

	private double horizontalDistanceSqr(Sweeper entity, Vec3 target) {
		double dx = target.x - entity.getX();
		double dz = target.z - entity.getZ();
		return dx * dx + dz * dz;
	}

	private void spawnTakeoffParticles(ServerLevel level, Sweeper entity) {
		Vec3 pos = entity.position();
		double particleY = pos.y + LANDING_PARTICLE_Y_OFFSET;
		level.sendParticles(ParticleTypes.POOF, pos.x, particleY, pos.z, TAKEOFF_SMOKE_COUNT,
				TAKEOFF_PARTICLE_HORIZONTAL_SPREAD, TAKEOFF_PARTICLE_VERTICAL_SPREAD,
				TAKEOFF_PARTICLE_HORIZONTAL_SPREAD, TAKEOFF_PARTICLE_SPEED);
		level.sendParticles(LcParticleTypes.SHORT_SMOKE.get(), pos.x, particleY, pos.z, TAKEOFF_SHORT_SMOKE_COUNT,
				TAKEOFF_PARTICLE_HORIZONTAL_SPREAD, TAKEOFF_PARTICLE_VERTICAL_SPREAD,
				TAKEOFF_PARTICLE_HORIZONTAL_SPREAD, TAKEOFF_PARTICLE_SPEED);
		level.sendParticles(ParticleTypes.ASH, pos.x, particleY, pos.z, TAKEOFF_DUST_COUNT,
				TAKEOFF_PARTICLE_HORIZONTAL_SPREAD, TAKEOFF_PARTICLE_VERTICAL_SPREAD,
				TAKEOFF_PARTICLE_HORIZONTAL_SPREAD, TAKEOFF_PARTICLE_SPEED);
		BlockState blockState = entity.getBlockStateOn();
		if (!blockState.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
					pos.x, particleY, pos.z, TAKEOFF_BLOCK_DEBRIS_COUNT,
					TAKEOFF_PARTICLE_HORIZONTAL_SPREAD, TAKEOFF_PARTICLE_VERTICAL_SPREAD,
					TAKEOFF_PARTICLE_HORIZONTAL_SPREAD, TAKEOFF_PARTICLE_SPEED);
		}
	}

	private void lockFacingToMovement(Sweeper entity, Vec3 movement) {
		aimToMovement(entity, movement);
	}

	private boolean aimToMovement(Sweeper entity, Vec3 movement) {
		float yaw = EntityFacingUtil.yaw(movement, entity.getYRot());
		float pitch = (float) (-Mth.atan2(movement.y, movement.horizontalDistance()) * Mth.RAD_TO_DEG);
		Vec3 origin = entity.getEyePosition();
		return EntityFacingUtil.aim(entity, origin.add(Vec3.directionFromRotation(pitch, yaw)), origin, true);
	}

	private void createFlightHitbox(Sweeper entity, EntitySkillRuntime<Sweeper> runtime, ServerLevel level) {
		runtime.setData(FLIGHT_CONTACTS, new HashSet<>());
		HitboxInstance hitbox = HitboxManager.create(FLIGHT_HITBOX_TEMPLATE, level,
				entity.getBoundingBox().getCenter(), MAX_LEAP_TICKS + CURRENT_TICK_DURATION, runtime);
		hitbox.follow(entity, new Vec3(0.0, entity.getBbHeight() / 2.0, 0.0), false);
		hitbox.setSize(new BoxSize(entity.getBbWidth() + FLIGHT_HITBOX_PADDING * 2.0,
				entity.getBbHeight() + FLIGHT_HITBOX_PADDING * 2.0,
				entity.getBbWidth() + FLIGHT_HITBOX_PADDING * 2.0));
		hitbox.appendTargetFilter(entity::isValidTarget);
		hitbox.activate();
		runtime.setData(FLIGHT_HITBOX_ID, hitbox.id());
	}

	@SuppressWarnings("resource")
	private void updateFlightHitbox(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		Integer id = runtime.getData(FLIGHT_HITBOX_ID);
		if (id == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		HitboxInstance hitbox = HitboxManager.get(level, id);
		if (hitbox != null) {
			hitbox.setSize(new BoxSize(entity.getBbWidth() + FLIGHT_HITBOX_PADDING * 2.0,
					entity.getBbHeight() + FLIGHT_HITBOX_PADDING * 2.0,
					entity.getBbWidth() + FLIGHT_HITBOX_PADDING * 2.0));
			hitbox.setRemainingTicks(CURRENT_TICK_DURATION);
		}
	}

	@SuppressWarnings("resource")
	private void removeFlightHitbox(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		Integer id = runtime.removeData(FLIGHT_HITBOX_ID);
		if (id != null && entity.level() instanceof ServerLevel level) {
			HitboxManager.remove(level, id);
		}
	}

	private static boolean onFlightContact(HitboxEffectContext context) {
		if (!(context.source() instanceof Sweeper entity)
				|| !(context.target() instanceof LivingEntity target)) {
			return false;
		}

		EntitySkillRuntime<?> runtime = context.instance().skillRuntime();
		Set<UUID> contacts = runtime == null ? null : runtime.getData(FLIGHT_CONTACTS);
		if (contacts == null || !contacts.add(target.getUUID())) {
			return false;
		}

		boolean hurt = target.hurtServer(context.level(), entity.damageSources().mobAttack(entity),
				(float) entity.getAttributeValue(Attributes.ATTACK_DAMAGE) * FLIGHT_DAMAGE_RATIO);
		if (hurt) {
			entity.markCombat();
			runtime.markSuccessful();
		}

		// 接触判定后的抓取不依赖伤害成功，伤害无敌帧不会阻止抓取。
		boolean grabbed = target.getBbWidth() <= MAX_GRAB_SIZE && target.getBbHeight() <= MAX_GRAB_SIZE
				&& EntityGrabUtil.grab(entity, target, GRAB_ESCAPE_DISTANCE);
		return hurt || grabbed;
	}

	private HitboxInstance getHitbox(ServerLevel level, EntitySkillRuntime<Sweeper> runtime) {
		Integer id = runtime.getData(HITBOX_ID);
		if (id == null) {
			return null;
		}

		return HitboxManager.get(level, id);
	}

	private void removeHitbox(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		Integer id = runtime.removeData(HITBOX_ID);
		if (id == null) {
			return;
		}

		if (entity.level() instanceof ServerLevel level) {
			HitboxManager.remove(level, id);
		}
	}

	private void addKnockbackResistance(Sweeper entity) {
		AttributeInstance attribute = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (attribute != null) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(KNOCKBACK_RESISTANCE_MODIFIER,
					KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	private void removeKnockbackResistance(Sweeper entity) {
		AttributeInstance attribute = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (attribute != null) {
			attribute.removeModifier(KNOCKBACK_RESISTANCE_MODIFIER);
		}
	}

	private void addSafeFallDistance(Sweeper entity) {
		AttributeInstance attribute = entity.getAttribute(Attributes.SAFE_FALL_DISTANCE);
		if (attribute != null && attribute.getValue() < SAFE_FALL_DISTANCE) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(SAFE_FALL_DISTANCE_MODIFIER,
					SAFE_FALL_DISTANCE - attribute.getValue(), AttributeModifier.Operation.ADD_VALUE));
		}
	}

	private void removeSafeFallDistance(Sweeper entity) {
		AttributeInstance attribute = entity.getAttribute(Attributes.SAFE_FALL_DISTANCE);
		if (attribute != null) {
			attribute.removeModifier(SAFE_FALL_DISTANCE_MODIFIER);
		}
	}
}
