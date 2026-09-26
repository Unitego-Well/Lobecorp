package org.unitego.lobecorp.entity.entity_skill.sweeper;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperAnim;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.hitbox.SphereSize;
import org.unitego.lobecorp.registry.effect.LcMobEffects;
import org.unitego.lobecorp.registry.entity_state.SweeperStates;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.util.TypedDataKey;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;
import static org.unitego.lobecorp.hitbox.HitboxManager.CURRENT_TICK_DURATION;

/// 清道夫飞扑技能：起跳 leap 冲向目标，落地 leap2 时对落点周围造成范围伤害。
public class SweeperLeapSkill extends SweeperSkill {
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
	/// 自动弹道允许使用的最大水平初速度
	private static final double MAX_HORIZONTAL_SPEED = 1.8;
	/// 自动弹道允许使用的最大垂直初速度
	private static final double MAX_VERTICAL_SPEED = 1.4;
	/// 自动弹道允许使用的最大仰角
	private static final double MAX_LAUNCH_ANGLE = 70.0 * Mth.DEG_TO_RAD;
	/// 计算预计飞行时间时使用的每 tick 目标水平距离
	private static final double TARGET_HORIZONTAL_DISTANCE_PER_TICK = 1.2;
	/// 计算预计飞行时间时使用的每 tick 目标上升距离
	private static final double TARGET_VERTICAL_DISTANCE_PER_TICK = 0.75;
	/// 实体在空中每 tick 的水平速度保留比例
	private static final double HORIZONTAL_MOVEMENT_DRAG = 0.91;
	/// 实体在空中每 tick 的垂直速度保留比例
	private static final double VERTICAL_MOVEMENT_DRAG = 0.98;
	/// 实体每 tick 受到的重力加速度
	private static final double GRAVITY = 0.08;
	/// 近距离目标使用的最短预计飞行时间
	private static final int MIN_FLIGHT_TICKS = 6;
	/// 最远目标使用的最长预计飞行时间
	private static final int MAX_FLIGHT_TICKS = 10;
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

	public SweeperLeapSkill(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canUse(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity target = entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		if (!entity.onGround()) {
			return false;
		}
		if (target == null) {
			return false;
		}
		if (!target.isAlive()) {
			return false;
		}
		if (!entity.isValidTarget(target)) {
			return false;
		}
		if (entity.isWithinMeleeAttackRange(target)) {
			return false;
		}
		if (horizontalDistanceSqr(entity, target) > MAX_LEAP_DISTANCE_SQUARED) {
			return false;
		}
		runtime.setTarget(target);
		return true;
	}

	@Override
	public void onWindupStart(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.addEntityState(SweeperStates.LEAP);
		entity.playActionAnimation(SweeperAnim.LEAP);
		updateLaunchSolution(entity, runtime);
		if (entity.level() instanceof ServerLevel level) {
			int lifetime = windupTicks() + MAX_LEAP_TICKS + recoveryTicks() + CURRENT_TICK_DURATION;
			HitboxInstance hitbox = HitboxManager.create(HITBOX_TEMPLATE, level, entity.position(), lifetime);
			hitbox.follow(entity, Vec3.ZERO, false);
			hitbox.appendTargetFilter(entity::isValidTarget);
			runtime.setData(HITBOX_ID, hitbox.id());
		}
	}

	@Override
	public void onWindupTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity target = getTarget(runtime);
		if (target == null) {
			EntitySkillManager.forceCancelSkill(entity, this);
			return;
		}
		if (!target.isAlive()) {
			EntitySkillManager.forceCancelSkill(entity, this);
			return;
		}
		if (target.isRemoved()) {
			EntitySkillManager.forceCancelSkill(entity, this);
			return;
		}
		updateLaunchSolution(entity, runtime);
	}

	@Override
	public boolean interruptibleDuringWindup() {
		return false;
	}

	@Override
	public void onActivate(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity target = getTarget(runtime);
		if (target == null) {
			EntitySkillManager.forceCancelSkill(entity, this);
			return;
		}
		if (!target.isAlive()) {
			EntitySkillManager.forceCancelSkill(entity, this);
			return;
		}
		if (!entity.isValidTarget(target)) {
			EntitySkillManager.forceCancelSkill(entity, this);
			return;
		}
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		addKnockbackResistance(entity);
		addSafeFallDistance(entity);
		Vec3 movement = runtime.plannedMovement();
		if (movement == null) {
			movement = calculateLaunchMovement(entity, target);
		}
		entity.setOnGround(false);
		entity.setDeltaMovement(movement);
		spawnTakeoffParticles(level, entity);
	}

	@Override
	public void onTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		if (entity.level().isClientSide()) {
			lockFacingToMovement(entity, entity.getDeltaMovement());
			return;
		}
		if (runtime.activeTicks() >= MIN_LANDING_CHECK_TICKS && entity.onGround()) {
			EntitySkillManager.endSkill(entity, this);
			return;
		}

		Vec3 v = entity.getDeltaMovement();
		if (runtime.activeTicks() >= MAX_LEAP_TICKS) {
			v = new Vec3(v.x * FORCED_DESCENT_HORIZONTAL_DRAG,
					Math.min(v.y, FORCED_DESCENT_SPEED), v.z * FORCED_DESCENT_HORIZONTAL_DRAG);
			entity.setDeltaMovement(v);
		}
		lockFacingToMovement(entity, v);
	}

	@Override
	public void onEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
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
		entity.removeEntityState(SweeperStates.LEAP);
		removeHitbox(entity, runtime);
	}

	@Override
	public void onCancel(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		removeKnockbackResistance(entity);
		removeSafeFallDistance(entity);
		entity.removeEntityState(SweeperStates.LEAP);
		entity.stopActionAnimation();
		removeHitbox(entity, runtime);
	}

	private void updateLaunchSolution(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity target = getTarget(runtime);
		if (target == null) {
			return;
		}
		if (!target.isAlive()) {
			return;
		}
		Vec3 movement = calculateLaunchMovement(entity, target);
		runtime.setPlannedMovement(movement);
		lockFacingToMovement(entity, movement);
	}

	private Vec3 calculateLaunchMovement(Sweeper entity, LivingEntity target) {
		double dx = target.getX() - entity.getX();
		double dz = target.getZ() - entity.getZ();
		double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
		double verticalDistance = target.getBoundingBox().getCenter().y
				- entity.getBoundingBox().getCenter().y;
		int horizontalFlightTicks = Mth.ceil(horizontalDistance / TARGET_HORIZONTAL_DISTANCE_PER_TICK);
		int verticalFlightTicks = Mth.ceil(Math.max(verticalDistance, 0.0) / TARGET_VERTICAL_DISTANCE_PER_TICK);
		int flightTicks = Mth.clamp(Math.max(horizontalFlightTicks, verticalFlightTicks),
				MIN_FLIGHT_TICKS, MAX_FLIGHT_TICKS);
		double horizontalMovementFactor = (1.0 - Math.pow(HORIZONTAL_MOVEMENT_DRAG, flightTicks))
				/ (1.0 - HORIZONTAL_MOVEMENT_DRAG);
		double verticalMovementFactor = (1.0 - Math.pow(VERTICAL_MOVEMENT_DRAG, flightTicks))
				/ (1.0 - VERTICAL_MOVEMENT_DRAG);
		double horizontalSpeed = Math.min(horizontalDistance / horizontalMovementFactor, MAX_HORIZONTAL_SPEED);
		double verticalSpeed = (verticalDistance
				+ GRAVITY * VERTICAL_MOVEMENT_DRAG / (1.0 - VERTICAL_MOVEMENT_DRAG)
				* (flightTicks - verticalMovementFactor)) / verticalMovementFactor;
		double maximumVerticalSpeedByAngle = horizontalSpeed * Math.tan(MAX_LAUNCH_ANGLE);
		verticalSpeed = Mth.clamp(verticalSpeed, -MAX_VERTICAL_SPEED,
				Math.min(MAX_VERTICAL_SPEED, maximumVerticalSpeedByAngle));
		if (horizontalDistance == 0.0) {
			return new Vec3(0.0, verticalSpeed, 0.0);
		}
		return new Vec3(dx / horizontalDistance * horizontalSpeed, verticalSpeed,
				dz / horizontalDistance * horizontalSpeed);
	}

	private double horizontalDistanceSqr(Sweeper entity, LivingEntity target) {
		double dx = target.getX() - entity.getX();
		double dz = target.getZ() - entity.getZ();
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
		if (movement.horizontalDistanceSqr() <= 1.0E-8) {
			return;
		}
		float yaw = (float) (Mth.atan2(movement.z, movement.x) * Mth.RAD_TO_DEG) - 90.0F;
		setFacing(entity, yaw);
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

	private void setFacing(Sweeper entity, float yaw) {
		entity.setYRot(yaw);
		entity.setYHeadRot(yaw);
		entity.yBodyRot = yaw;
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
