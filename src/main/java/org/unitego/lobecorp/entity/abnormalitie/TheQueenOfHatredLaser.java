package org.unitego.lobecorp.entity.abnormalitie;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.hitbox.CylinderSize;
import org.unitego.lobecorp.hitbox.HitboxHitMode;
import org.unitego.lobecorp.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxLineOfSightMode;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.registry.effect.LcMobEffects;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 激光技能使用的同步实体，同时管理服务端光束判断框。
public class TheQueenOfHatredLaser extends Entity {
	/// 光束最大长度。
	public static final float MAXIMUM_LENGTH = 20.0F;
	/// 二阶段激光长度倍率。
	private static final float SECOND_PHASE_LENGTH_MULTIPLIER = 1.25F;
	/// 未强化光束半径。
	public static final float NORMAL_RADIUS = 1.0F;
	/// 强化光束半径。
	public static final float ENHANCED_RADIUS = 1.5F;
	/// 初始伤害倍率。
	private static final float INITIAL_DAMAGE_MULTIPLIER = 0.5F;
	/// 强化后的伤害倍率。
	private static final float ENHANCED_DAMAGE_MULTIPLIER = 3.0F;
	/// 每次造成有效伤害后恢复的最大生命比例。
	private static final float HIT_HEALING_MAXIMUM_HEALTH_RATIO = 0.01F;
	/// 达到强化所需的计时。
	private static final int ENHANCEMENT_TICKS = 5 * TICKS_PER_SECOND;
	/// 每次成功命中的眩晕时间。
	private static final int STUN_DURATION_TICKS = TICKS_PER_SECOND;
	/// 未强化时的眩晕等级。
	private static final int NORMAL_STUN_AMPLIFIER = 0;
	/// 强化后的眩晕等级。
	private static final int ENHANCED_STUN_AMPLIFIER = 1;
	/// 防止动态圆柱长度退化为零的下限。
	private static final double MINIMUM_HITBOX_LENGTH = 1.0E-4;
	/// 同步所有者实体编号。
	private static final EntityDataAccessor<Integer> DATA_OWNER_ID = SynchedEntityData.defineId(
			TheQueenOfHatredLaser.class, EntityDataSerializers.INT);
	/// 同步光束方向 X。
	private static final EntityDataAccessor<Float> DATA_DIRECTION_X = SynchedEntityData.defineId(
			TheQueenOfHatredLaser.class, EntityDataSerializers.FLOAT);
	/// 同步光束方向 Y。
	private static final EntityDataAccessor<Float> DATA_DIRECTION_Y = SynchedEntityData.defineId(
			TheQueenOfHatredLaser.class, EntityDataSerializers.FLOAT);
	/// 同步光束方向 Z。
	private static final EntityDataAccessor<Float> DATA_DIRECTION_Z = SynchedEntityData.defineId(
			TheQueenOfHatredLaser.class, EntityDataSerializers.FLOAT);
	/// 同步当前可见长度。
	private static final EntityDataAccessor<Float> DATA_LENGTH = SynchedEntityData.defineId(
			TheQueenOfHatredLaser.class, EntityDataSerializers.FLOAT);
	/// 同步强化状态。
	private static final EntityDataAccessor<Boolean> DATA_ENHANCED = SynchedEntityData.defineId(
			TheQueenOfHatredLaser.class, EntityDataSerializers.BOOLEAN);
	/// 激光伤害判断框模板。
	private static final HitboxTemplate HITBOX_TEMPLATE = new HitboxTemplate(
			new CylinderSize(NORMAL_RADIUS, MAXIMUM_LENGTH),
			target -> target instanceof LivingEntity,
			context -> context.source() instanceof TheQueenOfHatredLaser laser
					&& context.target() instanceof LivingEntity target
					&& laser.hurtTarget(context.level(), target));

	@Nullable
	private TheQueenOfHatred owner;
	@Nullable
	private HitboxInstance hitbox;
	private long firstHitGameTime = Long.MIN_VALUE;

	/// 创建由注册器或网络数据生成的激光实体。
	/// @param type 激光实体类型
	/// @param level 所在世界
	public TheQueenOfHatredLaser(EntityType<? extends TheQueenOfHatredLaser> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_OWNER_ID, -1);
		builder.define(DATA_DIRECTION_X, 0.0F);
		builder.define(DATA_DIRECTION_Y, 0.0F);
		builder.define(DATA_DIRECTION_Z, 1.0F);
		builder.define(DATA_LENGTH, MAXIMUM_LENGTH);
		builder.define(DATA_ENHANCED, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		TheQueenOfHatred queen = owner();
		if (queen == null || !queen.isAlive()) {
			discard();
			return;
		}
		updateBeam(level);
	}

	@Override
	public void remove(RemovalReason reason) {
		if (hitbox != null) {
			HitboxManager.remove(hitbox.level(), hitbox.id());
			hitbox = null;
		}
		super.remove(reason);
	}

	/// 设置服务端所有者并同步其实体编号。
	/// @param owner 施放激光的憎恶皇后
	public void setOwner(TheQueenOfHatred owner) {
		this.owner = owner;
		entityData.set(DATA_OWNER_ID, owner.getId());
	}

	/// 设置归一化发射方向。
	/// @param direction 新的世界方向
	public void setDirection(Vec3 direction) {
		Vec3 normalized = direction.normalize();
		entityData.set(DATA_DIRECTION_X, (float) normalized.x);
		entityData.set(DATA_DIRECTION_Y, (float) normalized.y);
		entityData.set(DATA_DIRECTION_Z, (float) normalized.z);
	}

	/// @return 当前同步发射方向
	public Vec3 direction() {
		return new Vec3(entityData.get(DATA_DIRECTION_X), entityData.get(DATA_DIRECTION_Y),
				entityData.get(DATA_DIRECTION_Z)).normalize();
	}

	/// @return 当前由方块碰撞截断后的长度
	public float beamLength() {
		return entityData.get(DATA_LENGTH);
	}

	/// @return 当前渲染和判定半径
	public float beamRadius() {
		return isEnhanced() ? ENHANCED_RADIUS : NORMAL_RADIUS;
	}

	/// @param enhanced 是否进入强化阶段
	public void setEnhanced(boolean enhanced) {
		entityData.set(DATA_ENHANCED, enhanced);
	}

	/// @return 光束是否已经强化
	public boolean isEnhanced() {
		return entityData.get(DATA_ENHANCED);
	}

	/// @return 是否已经至少成功伤害过一个目标
	public boolean hasHit() {
		return firstHitGameTime != Long.MIN_VALUE;
	}

	/// @return 首次成功命中的服务端游戏时间
	public long firstHitGameTime() {
		return firstHitGameTime;
	}

	@Nullable
	private TheQueenOfHatred owner() {
		if (owner != null) {
			return owner;
		}
		Entity entity = level().getEntity(entityData.get(DATA_OWNER_ID));
		if (entity instanceof TheQueenOfHatred queen) {
			owner = queen;
		}
		return owner;
	}

	private void updateBeam(ServerLevel level) {
		Vec3 direction = direction();
		float maximumLength = maximumLength();
		Vec3 end = position().add(direction.scale(maximumLength));
		HitResult result = level.clip(new ClipContext(position(), end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, this));
		float length = result.getType() == HitResult.Type.MISS
				? maximumLength
				: (float) position().distanceTo(result.getLocation());
		entityData.set(DATA_LENGTH, length);
		Vec3 center = position().add(direction.scale(length / 2.0));
		if (hitbox == null) {
			hitbox = HitboxManager.create(HITBOX_TEMPLATE, level, center, TICKS_PER_SECOND);
			hitbox.setSource(this);
			hitbox.setHitPolicy(new HitboxHitPolicy(HitboxHitMode.EVERY_TICK, 0, -1, -1));
			hitbox.setLineOfSightMode(HitboxLineOfSightMode.CENTER_TO_ENTITY);
			hitbox.activate();
		}
		hitbox.setRemainingTicks(TICKS_PER_SECOND);
		hitbox.setPosition(center);
		hitbox.setSize(new CylinderSize(beamRadius(), Math.max(length, MINIMUM_HITBOX_LENGTH)));
		double horizontal = direction.horizontalDistance();
		double pitchFromVertical = Math.toDegrees(Math.atan2(horizontal, direction.y));
		double yaw = Math.toDegrees(Math.atan2(direction.x, direction.z));
		hitbox.setRotation(new Vec3(pitchFromVertical, yaw, 0.0));
	}

	private float maximumLength() {
		TheQueenOfHatred queen = owner();
		return queen != null && queen.isSecondPhase()
				? MAXIMUM_LENGTH * SECOND_PHASE_LENGTH_MULTIPLIER : MAXIMUM_LENGTH;
	}

	private boolean hurtTarget(ServerLevel level, LivingEntity target) {
		TheQueenOfHatred queen = owner();
		if (queen == null || !queen.isValidTarget(target)) {
			return false;
		}
		float multiplier = INITIAL_DAMAGE_MULTIPLIER;
		if (hasHit()) {
			float progress = Mth.clamp((float) (level.getGameTime() - firstHitGameTime) / ENHANCEMENT_TICKS,
					0.0F, 1.0F);
			multiplier = Mth.lerp(progress, INITIAL_DAMAGE_MULTIPLIER, ENHANCED_DAMAGE_MULTIPLIER);
		}
		boolean hurt = target.hurtServer(level, queen.damageSources().mobAttack(queen),
				(float) queen.getAttributeValue(Attributes.ATTACK_DAMAGE) * multiplier);
		if (!hurt) {
			return false;
		}
		queen.heal(queen.getMaxHealth() * HIT_HEALING_MAXIMUM_HEALTH_RATIO);
		if (!hasHit()) {
			firstHitGameTime = level.getGameTime();
		}
		int amplifier = isEnhanced() ? ENHANCED_STUN_AMPLIFIER : NORMAL_STUN_AMPLIFIER;
		target.addEffect(new MobEffectInstance(LcMobEffects.STUN, STUN_DURATION_TICKS, amplifier), queen);
		return true;
	}
}
