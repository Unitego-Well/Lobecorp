package org.unitego.lobecorp.entity.abnormalitie;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.hitbox.CylinderSize;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxLineOfSightMode;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;

import static org.unitego.lobecorp.hitbox.HitboxManager.CURRENT_TICK_DURATION;

/// 命中实体或方块后产生无方块破坏范围攻击的爆裂魔法星星。
public class TheQueenOfHatredBurstMagicStar extends TheQueenOfHatredMagicStar {
	/// 爆炸范围半径。
	private static final double EXPLOSION_RADIUS = 3.0;
	/// 爆炸判断高度，与直径保持一致。
	private static final double EXPLOSION_HEIGHT = EXPLOSION_RADIUS * 2.0;
	/// 爆炸伤害相对施法者基础伤害的倍率。
	private static final float DAMAGE_MULTIPLIER = 1.5F;
	/// 爆炸成功伤害后的水平击退强度。
	private static final double KNOCKBACK = 1.0;
	/// 爆炸后生成普通魔法星星的最少数量。
	private static final int MINIMUM_NORMAL_STAR_COUNT = 5;
	/// 爆炸后生成普通魔法星星的最多数量。
	private static final int MAXIMUM_NORMAL_STAR_COUNT = 8;
	/// 爆炸后生成追踪魔法星星的最少数量。
	private static final int MINIMUM_TRACKING_STAR_COUNT = 2;
	/// 爆炸后生成追踪魔法星星的最多数量。
	private static final int MAXIMUM_TRACKING_STAR_COUNT = 4;
	/// 二次星星离开爆炸中心的初始偏移，避免立即重复碰撞。
	private static final double CHILD_STAR_SPAWN_OFFSET = 0.3;
	/// 爆炸判断框模板。
	private static final HitboxTemplate HITBOX_TEMPLATE = new HitboxTemplate(
			new CylinderSize(EXPLOSION_RADIUS, EXPLOSION_HEIGHT),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof TheQueenOfHatred caster)) {
					return false;
				}
				if (!(context.target() instanceof LivingEntity target)
						|| target == caster
						|| !caster.isValidTarget(target)) {
					return false;
				}
				boolean hurt = target.hurtServer(context.level(), caster.damageSources().explosion(caster, caster),
						(float) caster.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULTIPLIER);
				if (!hurt) {
					return false;
				}
				target.knockback(KNOCKBACK, context.instance().position().x - target.getX(),
						context.instance().position().z - target.getZ());
				return true;
			}
	);

	/// 创建由实体注册器或客户端同步数据生成的爆裂星星。
	/// @param type 爆裂星星实体类型
	/// @param level 所在世界
	public TheQueenOfHatredBurstMagicStar(EntityType<? extends TheQueenOfHatredBurstMagicStar> type, Level level) {
		super(type, level);
	}

	/// 创建由憎恶皇后发射的爆裂星星。
	/// @param level 星星所在服务端世界
	/// @param owner 发射星星的憎恶皇后
	public TheQueenOfHatredBurstMagicStar(ServerLevel level, TheQueenOfHatred owner) {
		super(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_BURST_MAGIC_STAR.get(), level);
		setOwner(owner);
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
	}

	@Override
	protected void onHit(HitResult hitResult) {
		if (level() instanceof ServerLevel level
				&& getOwner() instanceof TheQueenOfHatred caster) {
			HitboxInstance hitbox = HitboxManager.create(
					HITBOX_TEMPLATE, level, position(), CURRENT_TICK_DURATION);
			hitbox.setSource(caster);
			hitbox.appendTargetFilter(target -> target != caster && caster.isValidTarget(target));
			hitbox.setLineOfSightMode(HitboxLineOfSightMode.UNRESTRICTED);
			hitbox.activate();
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE,
					SoundSource.BLOCKS, 1.0F, 1.0F);
			spawnChildStars(level, caster);
		}
		super.onHit(hitResult);
	}

	private void spawnChildStars(ServerLevel level, TheQueenOfHatred owner) {
		int normalCount = randomCount(MINIMUM_NORMAL_STAR_COUNT, MAXIMUM_NORMAL_STAR_COUNT);
		for (int index = 0; index < normalCount; index++) {
			spawnChildStar(level, new TheQueenOfHatredMagicStar(level, owner));
		}
		int trackingCount = randomCount(MINIMUM_TRACKING_STAR_COUNT, MAXIMUM_TRACKING_STAR_COUNT);
		for (int index = 0; index < trackingCount; index++) {
			spawnChildStar(level, new TheQueenOfHatredTrackingMagicStar(level, owner));
		}
	}

	private void spawnChildStar(ServerLevel level, TheQueenOfHatredMagicStar star) {
		Vec3 direction = randomSphericalDirection();
		star.setPos(position().add(direction.scale(CHILD_STAR_SPAWN_OFFSET)));
		star.shoot(direction.x, direction.y, direction.z, TheQueenOfHatredMagicStar.launchSpeed(), 0.0F);
		level.addFreshEntity(star);
	}

	private Vec3 randomSphericalDirection() {
		double vertical = getRandom().nextDouble() * 2.0 - 1.0;
		double horizontal = Math.sqrt(1.0 - vertical * vertical);
		double angle = getRandom().nextDouble() * Math.PI * 2.0;
		return new Vec3(Math.cos(angle) * horizontal, vertical, Math.sin(angle) * horizontal);
	}

	private int randomCount(int minimum, int maximum) {
		return minimum + getRandom().nextInt(maximum - minimum + 1);
	}
}
