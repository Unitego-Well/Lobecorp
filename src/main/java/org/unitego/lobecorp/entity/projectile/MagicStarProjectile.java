package org.unitego.lobecorp.entity.projectile;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.UUID;

/// 女皇技能使用的魔法星投射物，负责飞行碰撞和伤害结算。
public class MagicStarProjectile extends ThrowableItemProjectile {
	private static final EntityDataAccessor<Integer> DATA_SIZE =
			SynchedEntityData.defineId(MagicStarProjectile.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_VARIANT =
			SynchedEntityData.defineId(MagicStarProjectile.class, EntityDataSerializers.INT);
	private static final ResourceKey<DamageType> DAMAGE_TYPE =
			ResourceKey.create(Registries.DAMAGE_TYPE, Lobecorp.id("magic_star"));
	/// 普通飞行星星的速度，单位为格/游戏刻。
	private static final double SPEED = 1.0;
	/// 普通飞行星星的寿命，单位为游戏刻；路径星星使用独立寿命。
	private static final int MAX_LIFETIME_TICKS = 12;
	/// 追踪目标的最大距离，单位为格。
	private static final double HOMING_RANGE = 12.0;
	/// 每游戏刻允许的最大追踪转角，单位为弧度。
	private static final double HOMING_TURN_RADIANS = Math.toRadians(12.0);
	/// 星星基础伤害相对女皇攻击属性的倍率，再乘星星尺寸倍率。
	private static final double BASE_DAMAGE_RATIO = 0.5;
	/// 最小尺寸爆破星星的爆炸半径，单位为格。
	private static final double BASE_BURST_RADIUS = 1.0;
	/// 近乎反向时使用固定旋转方向的点积阈值。
	private static final double OPPOSITE_DIRECTION_DOT = -0.999;
	/// 折射生成两个分支时相对目标方向的偏转角，单位为弧度。
	private static final float SPLIT_ANGLE = (float) Math.toRadians(15.0);
	/// 每次有效实体碰撞生成的折射子星数量。
	private static final int SPLIT_STAR_COUNT = 2;
	/// 路径星星寿命、折射剩余代数和最近一次命中目标的存档键。
	private static final String STATIONARY_LIFETIME_KEY = "StationaryLifetime";
	private static final String SPLITS_REMAINING_KEY = "SplitsRemaining";
	private static final String EXCLUDED_TARGET_KEY = "ExcludedTarget";
	/// 单枚星星独立飞行参数的存档键；没有覆盖时保持原有星星规则。
	private static final String FLIGHT_LIFETIME_KEY = "FlightLifetime";
	private static final String DAMAGE_MULTIPLIER_KEY = "DamageMultiplier";
	private static final String BURST_RADIUS_KEY = "BurstRadius";
	private StarSize starSize = StarSize.TINY;
	private int flightLifetimeTicks = MAX_LIFETIME_TICKS;
	@Nullable
	private Double damageMultiplier;
	@Nullable
	private Double burstRadius;
	private int stationaryLifetimeTicks;
	private int splitsRemaining;
	@Nullable
	private UUID excludedTarget;

	public MagicStarProjectile(EntityType<? extends MagicStarProjectile> type, Level level) {
		super(type, level);
	}

	private static Vec3 turnTowards(Vec3 current, Vec3 desired) {
		if (current.lengthSqr() == 0.0 || desired.lengthSqr() == 0.0) {
			return current;
		}
		double dot = Mth.clamp(current.dot(desired), -1.0, 1.0);
		double angle = Math.acos(dot);
		if (angle <= HOMING_TURN_RADIANS) {
			return desired;
		}
		if (dot <= OPPOSITE_DIRECTION_DOT) {
			return current.yRot((float) HOMING_TURN_RADIANS);
		}
		double ratio = HOMING_TURN_RADIANS / angle;
		double sine = Math.sin(angle);
		return current.scale(Math.sin((1.0 - ratio) * angle) / sine)
				.add(desired.scale(Math.sin(ratio * angle) / sine)).normalize();
	}

	public void configure(TheQueenOfHatred queen, StarSize size, StarVariant variant, Vec3 position, Vec3 direction) {
		setOwner(queen);
		setStarSize(size);
		setVariant(variant);
		setPos(position);
		setDeltaMovement(direction.normalize().scale(SPEED));
	}

	public StarSize starSize() {
		return starSize;
	}

	/// 路径星星停留在出生位置，接触一个有效敌人后消失。
	public void setStationary(int lifetimeTicks) {
		stationaryLifetimeTicks = lifetimeTicks;
		setDeltaMovement(Vec3.ZERO);
	}

	/// 每条分支独立保存剩余分裂代数，只排除刚命中的目标。
	public void setRefraction(int remaining, @Nullable UUID excluded) {
		splitsRemaining = remaining;
		excludedTarget = excluded;
	}

	public static double homingRange() {
		return HOMING_RANGE;
	}

	/// 覆盖单枚星星的寿命、攻击属性伤害倍率和爆炸半径，不改变其他技能的默认星星。
	public void setFlightParameters(int lifetimeTicks, double damageMultiplier, double burstRadius) {
		this.flightLifetimeTicks = lifetimeTicks;
		this.damageMultiplier = damageMultiplier;
		this.burstRadius = burstRadius;
	}

	public void setStarSize(StarSize size) {
		starSize = size;
		getEntityData().set(DATA_SIZE, size.ordinal());
		refreshDimensions();
	}

	public StarVariant variant() {
		return StarVariant.byId(getEntityData().get(DATA_VARIANT));
	}

	public void setVariant(StarVariant variant) {
		getEntityData().set(DATA_VARIANT, variant.ordinal());
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		float width = starSize == null ? StarSize.TINY.width() : starSize.width();
		return EntityDimensions.scalable(width, width);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SIZE, StarSize.TINY.ordinal());
		builder.define(DATA_VARIANT, StarVariant.A.ordinal());
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_SIZE.equals(accessor)) {
			starSize = StarSize.byId(getEntityData().get(DATA_SIZE));
			refreshDimensions();
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("StarSize", starSize.ordinal());
		output.putInt("StarVariant", variant().ordinal());
		output.putInt(STATIONARY_LIFETIME_KEY, stationaryLifetimeTicks);
		output.putInt(SPLITS_REMAINING_KEY, splitsRemaining);
		output.putInt(FLIGHT_LIFETIME_KEY, flightLifetimeTicks);
		if (damageMultiplier != null) output.putDouble(DAMAGE_MULTIPLIER_KEY, damageMultiplier);
		if (burstRadius != null) output.putDouble(BURST_RADIUS_KEY, burstRadius);
		if (excludedTarget != null) output.store(EXCLUDED_TARGET_KEY, UUIDUtil.CODEC, excludedTarget);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setStarSize(StarSize.byId(input.getIntOr("StarSize", StarSize.TINY.ordinal())));
		setVariant(StarVariant.byId(input.getIntOr("StarVariant", StarVariant.A.ordinal())));
		stationaryLifetimeTicks = input.getIntOr(STATIONARY_LIFETIME_KEY, 0);
		splitsRemaining = input.getIntOr(SPLITS_REMAINING_KEY, 0);
		flightLifetimeTicks = input.getIntOr(FLIGHT_LIFETIME_KEY, MAX_LIFETIME_TICKS);
		damageMultiplier = input.read(DAMAGE_MULTIPLIER_KEY, Codec.DOUBLE).orElse(null);
		burstRadius = input.read(BURST_RADIUS_KEY, Codec.DOUBLE).orElse(null);
		excludedTarget = input.read(EXCLUDED_TARGET_KEY, UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.NETHER_STAR;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public void tick() {
		if (!level().isClientSide() && (!(getOwner() instanceof TheQueenOfHatred queen) || !queen.isAlive()
				|| queen.level() != level()
				|| tickCount >= (stationaryLifetimeTicks > 0 ? stationaryLifetimeTicks : flightLifetimeTicks))) {
			discard();
			return;
		}
		if (stationaryLifetimeTicks > 0 && level() instanceof ServerLevel level) {
			setDeltaMovement(Vec3.ZERO);
			LivingEntity contact = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox(), this::canHitEntity)
					.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
			if (contact != null) {
				onHit(new EntityHitResult(contact));
				return;
			}
		}
		Vec3 direction = getDeltaMovement().normalize();
		if (!level().isClientSide() && getType() == AbnormalitieEntityTypes.MAGIC_HOMING_STAR.get()
				&& getOwner() instanceof TheQueenOfHatred queen) {
			LivingEntity target = nearestTarget(queen);
			if (target != null) {
				Vec3 desired = target.getBoundingBox().getCenter().subtract(position()).normalize();
				direction = turnTowards(direction, desired);
			}
		}
		setDeltaMovement(direction.scale(SPEED));
		super.tick();
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity) && entity instanceof LivingEntity target && target.isAlive()
				&& !entity.getUUID().equals(excludedTarget)
				&& getOwner() instanceof TheQueenOfHatred queen && queen.isHatedTarget(target);
	}

	@Override
	protected void onHit(HitResult hitResult) {
		if (!(level() instanceof ServerLevel level) || !(getOwner() instanceof TheQueenOfHatred queen)) {
			return;
		}
		float damage = (float) (queen.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* (damageMultiplier == null ? BASE_DAMAGE_RATIO * starSize.scale() : damageMultiplier));
		DamageSource damageSource = new DamageSource(level.registryAccess()
				.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE), this, queen);
		if (getType() == AbnormalitieEntityTypes.MAGIC_BURST_STAR.get()) {
			double radius = burstRadius == null ? BASE_BURST_RADIUS * starSize.scale() : burstRadius;
			AABB bounds = AABB.ofSize(position(), radius * 2.0, radius * 2.0, radius * 2.0);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds,
					candidate -> candidate.isAlive() && queen.isHatedTarget(candidate))) {
				if (target.getBoundingBox().distanceToSqr(position()) <= radius * radius) {
					target.hurtServer(level, damageSource, damage);
				}
			}
		} else if (hitResult instanceof EntityHitResult entityHit
				&& entityHit.getEntity() instanceof LivingEntity target) {
			target.hurtServer(level, damageSource, damage);
			if (splitsRemaining > 0) split(level, queen, target);
		}
		discard();
	}

	@Nullable
	private LivingEntity nearestTarget(TheQueenOfHatred queen) {
		return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(HOMING_RANGE),
				candidate -> candidate.isAlive() && queen.isHatedTarget(candidate)
						&& !candidate.getUUID().equals(excludedTarget)
						&& candidate.distanceToSqr(this) <= HOMING_RANGE * HOMING_RANGE)
				.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
	}

	private void split(ServerLevel level, TheQueenOfHatred queen, LivingEntity hitTarget) {
		for (int index = 0; index < SPLIT_STAR_COUNT; index++) {
			MagicStarProjectile child = new MagicStarProjectile(AbnormalitieEntityTypes.MAGIC_HOMING_STAR.get(), level);
			child.setRefraction(splitsRemaining - 1, hitTarget.getUUID());
			child.setPos(position());
			LivingEntity nearest = child.nearestTarget(queen);
			Vec3 direction = nearest == null ? getDeltaMovement().normalize()
					: nearest.getBoundingBox().getCenter().subtract(position()).normalize();
			direction = direction.yRot(index == 0 ? -SPLIT_ANGLE : SPLIT_ANGLE);
			child.configure(queen, starSize, variant(), position().add(direction.scale(starSize.width())), direction);
			level.addFreshEntity(child);
		}
	}

	public enum StarSize {
		TINY(0.2F), SMALL(0.5F), MEDIUM(1.0F), LARGE(2.5F);

		private final float width;

		private StarSize(float width) {
			this.width = width;
		}

		public static StarSize byId(int id) {
			return values()[Mth.clamp(id, 0, values().length - 1)];
		}

		public float width() {
			return width;
		}

		public double scale() {
			return width / TINY.width;
		}
	}

	public enum StarVariant {
		A, B, C, D;

		public static StarVariant byId(int id) {
			return values()[Mth.clamp(id, 0, values().length - 1)];
		}
	}
}
