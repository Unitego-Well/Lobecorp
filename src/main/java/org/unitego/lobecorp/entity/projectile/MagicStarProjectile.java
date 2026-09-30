package org.unitego.lobecorp.entity.projectile;

import net.minecraft.core.registries.Registries;
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

import java.util.Comparator;

/// 女皇技能使用的魔法星投射物，负责飞行碰撞和伤害结算。
public class MagicStarProjectile extends ThrowableItemProjectile {
	private static final EntityDataAccessor<Integer> DATA_SIZE =
			SynchedEntityData.defineId(MagicStarProjectile.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_VARIANT =
			SynchedEntityData.defineId(MagicStarProjectile.class, EntityDataSerializers.INT);
	private static final ResourceKey<DamageType> DAMAGE_TYPE =
			ResourceKey.create(Registries.DAMAGE_TYPE, Lobecorp.id("magic_star"));
	private static final double SPEED = 1.0;
	private static final int MAX_LIFETIME_TICKS = 12;
	private static final double HOMING_RANGE = 12.0;
	private static final double HOMING_TURN_RADIANS = Math.toRadians(12.0);
	private static final double BASE_DAMAGE_RATIO = 0.5;
	private static final double BASE_BURST_RADIUS = 1.0;
	private static final double OPPOSITE_DIRECTION_DOT = -0.999;
	private StarSize starSize = StarSize.TINY;

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
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setStarSize(StarSize.byId(input.getIntOr("StarSize", StarSize.TINY.ordinal())));
		setVariant(StarVariant.byId(input.getIntOr("StarVariant", StarVariant.A.ordinal())));
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
				|| tickCount >= MAX_LIFETIME_TICKS)) {
			discard();
			return;
		}
		Vec3 direction = getDeltaMovement().normalize();
		if (!level().isClientSide() && getType() == AbnormalitieEntityTypes.MAGIC_HOMING_STAR.get()
				&& getOwner() instanceof TheQueenOfHatred queen) {
			LivingEntity target = level().getEntitiesOfClass(LivingEntity.class,
							getBoundingBox().inflate(HOMING_RANGE), candidate -> candidate.isAlive()
									&& queen.isHatedTarget(candidate)
									&& candidate.distanceToSqr(this) <= HOMING_RANGE * HOMING_RANGE)
					.stream()
					.min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(this)))
					.orElse(null);
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
				&& getOwner() instanceof TheQueenOfHatred queen && queen.isHatedTarget(target);
	}

	@Override
	protected void onHit(HitResult hitResult) {
		if (!(level() instanceof ServerLevel level) || !(getOwner() instanceof TheQueenOfHatred queen)) {
			return;
		}
		float damage = (float) (queen.getAttributeValue(Attributes.ATTACK_DAMAGE)
				* BASE_DAMAGE_RATIO * starSize.scale());
		DamageSource damageSource = new DamageSource(level.registryAccess()
				.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE), this, queen);
		if (getType() == AbnormalitieEntityTypes.MAGIC_BURST_STAR.get()) {
			double radius = BASE_BURST_RADIUS * starSize.scale();
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
		}
		discard();
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
