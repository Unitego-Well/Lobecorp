package org.unitego.lobecorp.entity.abnormalitie;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 憎恶皇后横扫发射的直线魔法星星，客户端使用原版雪球物品模型渲染。
public class TheQueenOfHatredMagicStar extends ThrowableItemProjectile {
	/// 原版雪球发射速度。
	public static final float SNOWBALL_SPEED = 1.5F;
	/// 普通魔法星星使用的雪球速度倍率。
	public static final float SPEED_MULTIPLIER = 1.5F;
	/// 所有魔法星星的整体速度倍率。
	private static final float STAR_SPEED_SCALE = 0.5F;
	/// 星星伤害相对施法者基础伤害的倍率。
	private static final float DAMAGE_MULTIPLIER = 0.5F;
	/// 星星未命中时的最大存活 tick。
	private static final int MAXIMUM_LIFETIME_TICKS = 3 * TICKS_PER_SECOND;

	/// 创建由实体类型注册器或客户端同步数据生成的魔法星星。
	/// @param type 魔法星星的实体类型
	/// @param level 星星所在的世界
	public TheQueenOfHatredMagicStar(EntityType<? extends TheQueenOfHatredMagicStar> type, Level level) {
		super(type, level);
		setNoGravity(true);
	}

	/// 创建由憎恶皇后发射的魔法星星，并将施法者记录为伤害来源。
	/// @param level 星星所在的世界
	/// @param owner 发射星星的憎恶皇后
	public TheQueenOfHatredMagicStar(Level level, TheQueenOfHatred owner) {
		super(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_MAGIC_STAR.get(), owner, level,
				new ItemStack(Items.SNOWBALL));
		setNoGravity(true);
	}

	/// @return 普通魔法星星的发射速度
	public static float launchSpeed() {
		return scaledSpeed(SPEED_MULTIPLIER);
	}

	/// @param speedMultiplier 相对原版雪球速度的倍率
	/// @return 应用整体速度调整后的星星速度
	public static float scaledSpeed(float speedMultiplier) {
		return SNOWBALL_SPEED * speedMultiplier * STAR_SPEED_SCALE;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.SNOWBALL;
	}

	@Override
	public void tick() {
		super.tick();
		if (tickCount >= MAXIMUM_LIFETIME_TICKS) {
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity entity) {
		return super.canHitEntity(entity)
				&& getOwner() instanceof TheQueenOfHatred queen
				&& queen.isValidTarget(entity);
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		super.onHitEntity(hitResult);
		if (!(level() instanceof ServerLevel level)
				|| !(getOwner() instanceof TheQueenOfHatred queen)
				|| !(hitResult.getEntity() instanceof LivingEntity target)) {
			return;
		}
		target.hurtServer(level, damageSources().thrown(this, queen),
				(float) queen.getAttributeValue(Attributes.ATTACK_DAMAGE)
						* DAMAGE_MULTIPLIER);
	}

	@Override
	protected void onHit(HitResult hitResult) {
		super.onHit(hitResult);
		if (!level().isClientSide()) {
			level().broadcastEntityEvent(this, (byte) 3);
			discard();
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id != 3) {
			super.handleEntityEvent(id);
			return;
		}
		ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM,
				ItemStackTemplate.fromNonEmptyStack(getItem()));
		for (int index = 0; index < 8; index++) {
			level().addParticle(particle, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
		}
	}
}
