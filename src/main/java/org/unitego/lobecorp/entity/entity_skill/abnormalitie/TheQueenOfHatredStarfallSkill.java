package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredBurstMagicStar;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredMagicStar;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredTrackingMagicStar;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.util.TypedDataKey;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

import java.util.Comparator;
import java.util.List;

/// 星陨：在锁定区域上空持续生成随机种类与轨迹的魔法星星。
public class TheQueenOfHatredStarfallSkill extends TheQueenOfHatredSpellSkill {
	/// AI 选择星陨法术的概率。
	public static final float AI_CAST_CHANCE = 0.25F;
	/// 星陨能够锁定目标的最大距离。
	private static final double USE_RANGE = 30.0;
	/// 使用距离的平方缓存。
	private static final double USE_RANGE_SQUARED = USE_RANGE * USE_RANGE;
	/// 锁定区域半径。
	private static final double AREA_RADIUS = 2.5;
	/// 星星生成位置相对区域中心的最低高度。
	private static final double MINIMUM_SPAWN_HEIGHT = 12.0;
	/// 星星生成位置相对区域中心的最高高度。
	private static final double MAXIMUM_SPAWN_HEIGHT = 18.0;
	/// 两颗星星之间的最短生成间隔。
	private static final int MINIMUM_SPAWN_INTERVAL_TICKS = 5;
	/// 两颗星星之间的最长生成间隔。
	private static final int MAXIMUM_SPAWN_INTERVAL_TICKS = 10;
	/// 普通魔法星星的生成概率。
	private static final float NORMAL_STAR_CHANCE = 0.1F;
	/// 追踪魔法星星的累计生成概率上限。
	private static final float TRACKING_STAR_CHANCE_LIMIT = 0.2F;
	/// 星星初速度相对原版雪球速度的最低倍率。
	private static final float MINIMUM_SPEED_MULTIPLIER = 1.0F;
	/// 星星初速度相对原版雪球速度的最高倍率。
	private static final float MAXIMUM_SPEED_MULTIPLIER = 2.0F;
	/// 星陨持续生成时间。
	public static final int CAST_TICKS = 10 * TICKS_PER_SECOND;
	/// 星陨独立冷却时间。
	public static final int COOLDOWN_TICKS = 45 * TICKS_PER_SECOND;
	/// 下一颗星星相对主动阶段开始的生成 tick。
	private static final TypedDataKey<Integer> NEXT_SPAWN_TICK = TypedDataKey.create();

	/// @param properties 星陨法术的基础技能配置
	public TheQueenOfHatredStarfallSkill(Properties properties) {
		super(properties);
	}

	@Override
	public float planningChance() {
		return AI_CAST_CHANCE;
	}

	@Override
	public TheQueenOfHatredAttackMode attackMode() {
		return TheQueenOfHatredAttackMode.LONG_RANGE_CAST;
	}

	@Override
	public double minimumPlanningRange() {
		return 10.0;
	}

	@Override
	public double maximumPlanningRange() {
		return USE_RANGE;
	}

	@Override
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = selectNearbyTarget(entity);
		if (target == null) {
			return false;
		}
		runtime.setTarget(target);
		return true;
	}

	@Override
	public LivingEntity selectPlanningTarget(TheQueenOfHatred entity, List<LivingEntity> enemies) {
		LivingEntity target = selectNearbyTarget(entity);
		return target == null ? super.selectPlanningTarget(entity, enemies) : target;
	}

	@Override
	public void onWindupTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target == null || !entity.isValidTarget(target)) {
			return;
		}
		Vec3 center = groundPosition(entity, entity.predictTargetPosition(target));
		entity.facePosition(center);
		if (entity.level() instanceof ServerLevel level) {
			showGroundMarker(level, center, AREA_RADIUS, ParticleTypes.WITCH);
		}
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onActivate(entity, runtime);
		runtime.setData(NEXT_SPAWN_TICK, nextSpawnInterval(entity));
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target == null || !entity.isValidTarget(target)) {
			EntitySkillManager.endSkill(entity, this);
			return;
		}
		Vec3 center = groundPosition(entity, entity.predictTargetPosition(target));
		entity.facePosition(center);
		showGroundMarker(level, center, AREA_RADIUS, ParticleTypes.WITCH);
		Integer nextSpawnTick = runtime.getData(NEXT_SPAWN_TICK);
		if (nextSpawnTick == null || runtime.activeTicks() < nextSpawnTick) {
			return;
		}
		spawnStar(level, entity, center);
		runtime.setData(NEXT_SPAWN_TICK, runtime.activeTicks() + nextSpawnInterval(entity));
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onEnd(entity, runtime);
	}

	private void spawnStar(ServerLevel level, TheQueenOfHatred entity, Vec3 center) {
		Vec3 spawn = randomPoint(entity, center, MINIMUM_SPAWN_HEIGHT, MAXIMUM_SPAWN_HEIGHT);
		Vec3 destination = randomPoint(entity, center, 0.0, 0.0);
		Vec3 direction = destination.subtract(spawn).normalize();
		TheQueenOfHatredMagicStar star = createStar(level, entity);
		star.setPos(spawn);
		float speedMultiplier = Mth.lerp(entity.getRandom().nextFloat(),
				MINIMUM_SPEED_MULTIPLIER, MAXIMUM_SPEED_MULTIPLIER);
		star.shoot(direction.x, direction.y, direction.z,
				TheQueenOfHatredMagicStar.scaledSpeed(speedMultiplier), 0.0F);
		level.addFreshEntity(star);
	}

	private TheQueenOfHatredMagicStar createStar(ServerLevel level, TheQueenOfHatred entity) {
		float selection = entity.getRandom().nextFloat();
		if (selection < NORMAL_STAR_CHANCE) {
			return new TheQueenOfHatredMagicStar(level, entity);
		}
		if (selection < TRACKING_STAR_CHANCE_LIMIT) {
			return new TheQueenOfHatredTrackingMagicStar(level, entity);
		}
		return new TheQueenOfHatredBurstMagicStar(level, entity);
	}

	private Vec3 randomPoint(TheQueenOfHatred entity, Vec3 center,
			double minimumHeight, double maximumHeight) {
		double radius = Math.sqrt(entity.getRandom().nextDouble()) * AREA_RADIUS;
		double angle = entity.getRandom().nextDouble() * Mth.TWO_PI;
		double height = Mth.lerp(entity.getRandom().nextDouble(), minimumHeight, maximumHeight);
		return center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
	}

	private int nextSpawnInterval(TheQueenOfHatred entity) {
		int interval = MINIMUM_SPAWN_INTERVAL_TICKS + entity.getRandom().nextInt(
				MAXIMUM_SPAWN_INTERVAL_TICKS - MINIMUM_SPAWN_INTERVAL_TICKS + 1);
		return entity.isSecondPhase() ? Math.max(1, interval / 2) : interval;
	}

	private LivingEntity selectNearbyTarget(TheQueenOfHatred entity) {
		LivingEntity current = entity.getAttackTarget();
		if (current != null && entity.isValidTarget(current)
				&& entity.distanceToSqr(current) <= USE_RANGE_SQUARED) {
			return current;
		}
		AABB bounds = entity.getBoundingBox().inflate(USE_RANGE);
		return entity.level().getEntitiesOfClass(LivingEntity.class, bounds, entity::isValidTarget).stream()
				.filter(target -> entity.distanceToSqr(target) <= USE_RANGE_SQUARED)
				.min(Comparator.comparingDouble(entity::distanceToSqr))
				.orElse(null);
	}
}
