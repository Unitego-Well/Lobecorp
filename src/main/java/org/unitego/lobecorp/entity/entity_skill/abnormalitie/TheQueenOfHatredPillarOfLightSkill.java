package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredPillarOfLight;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.util.TypedDataKey;

import java.util.List;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 光柱：持续追踪目标后锁定落点，并以信标光柱表现一次单体打击。
public class TheQueenOfHatredPillarOfLightSkill extends TheQueenOfHatredSpellSkill {
	/// AI 选择光柱法术的概率。
	public static final float AI_CAST_CHANCE = 0.25F;
	/// 光柱能够追踪目标的最大距离。
	private static final double USE_RANGE = 20.0;
	/// 使用距离的平方缓存。
	private static final double USE_RANGE_SQUARED = USE_RANGE * USE_RANGE;
	/// 前摇结束前停止追踪目标的时间。
	private static final int LOCK_BEFORE_ACTIVATION_TICKS = 5;
	/// 锁定点的水平命中半径。
	private static final double HIT_RADIUS = 1.5;
	/// 水平命中半径的平方缓存。
	private static final double HIT_RADIUS_SQUARED = HIT_RADIUS * HIT_RADIUS;
	/// 锁定点上下允许的命中距离。
	private static final double HIT_VERTICAL_RANGE = 1.5;
	/// 光柱造成的基础攻击伤害倍率。
	private static final float DAMAGE_MULTIPLIER = 3.0F;
	/// 光柱落空或施法取消后的冷却时间。
	private static final int FAILED_COOLDOWN_TICKS = 3 * TICKS_PER_SECOND;
	/// 光柱成功命中后的冷却时间。
	public static final int COOLDOWN_TICKS = 8 * TICKS_PER_SECOND;
	/// 本次施法最终锁定的世界位置。
	private static final TypedDataKey<List<Vec3>> LOCKED_POSITIONS = TypedDataKey.create();
	private static final TypedDataKey<List<LivingEntity>> TARGETS = TypedDataKey.create();

	/// @param properties 光柱法术的基础技能配置
	public TheQueenOfHatredPillarOfLightSkill(Properties properties) {
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
		return 8.0;
	}

	@Override
	public double maximumPlanningRange() {
		return USE_RANGE;
	}

	@Override
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = entity.getAttackTarget();
		if (!isTrackable(entity, target)) {
			return false;
		}
		runtime.setTarget(target);
		runtime.setData(TARGETS, selectPhaseTargets(entity, target, USE_RANGE, true));
		return true;
	}

	@Override
	public void onWindupStart(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onWindupStart(entity, runtime);
		runtime.setCooldownTicks(FAILED_COOLDOWN_TICKS);
	}

	@Override
	public void onWindupTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		List<LivingEntity> targets = runtime.getData(TARGETS);
		if (targets == null || targets.isEmpty() || !isTrackable(entity, targets.getFirst())) {
			EntitySkillManager.cancelSkill(entity, this);
			return;
		}
		LivingEntity target = targets.getFirst();
		Vec3 predictedPosition = entity.predictTargetPosition(target, runtime.ticksLeft());
		entity.facePosition(predictedPosition);
		if (!runtime.hasData(LOCKED_POSITIONS)
				&& runtime.ticksLeft() <= LOCK_BEFORE_ACTIVATION_TICKS) {
			List<Vec3> positions = targets.stream()
					.map(lockedTarget -> groundPosition(entity,
							entity.predictTargetPosition(lockedTarget, runtime.ticksLeft())))
					.toList();
			runtime.setData(LOCKED_POSITIONS, positions);
		}
		if (entity.level() instanceof ServerLevel level) {
			List<Vec3> markerPositions = runtime.getData(LOCKED_POSITIONS);
			if (markerPositions == null) {
				markerPositions = targets.stream().map(lockedTarget -> groundPosition(entity,
						entity.predictTargetPosition(lockedTarget, runtime.ticksLeft()))).toList();
			}
			for (Vec3 markerPosition : markerPositions) {
				showGroundMarker(level, markerPosition, HIT_RADIUS, ParticleTypes.END_ROD);
			}
		}
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onActivate(entity, runtime);
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		List<LivingEntity> lockedTargets = runtime.getData(TARGETS);
		if (lockedTargets == null) {
			lockedTargets = List.of();
		}
		List<LivingEntity> targets = refreshPhaseTargets(entity,
				lockedTargets, USE_RANGE, true);
		List<Vec3> lockedPositions = runtime.getData(LOCKED_POSITIONS);
		if (lockedPositions == null) {
			return;
		}
		float damage = (float) entity.getAttributeValue(Attributes.ATTACK_DAMAGE) * DAMAGE_MULTIPLIER;
		boolean successful = false;
		for (int index = 0; index < lockedPositions.size(); index++) {
			Vec3 lockedPosition = index < targets.size() && index < lockedTargets.size()
					&& targets.get(index) == lockedTargets.get(index)
					? lockedPositions.get(index)
					: index < targets.size()
						? groundPosition(entity, entity.predictTargetPosition(targets.get(index), 0.0))
						: lockedPositions.get(index);
			spawnPillar(level, lockedPosition);
			if (index < targets.size() && canHit(entity, targets.get(index), lockedPosition)
					&& targets.get(index).hurtServer(level, entity.damageSources().mobAttack(entity), damage)) {
				successful = true;
			}
		}
		if (successful) {
			runtime.markSuccessful();
			runtime.setCooldownTicks(COOLDOWN_TICKS);
		}
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		super.onEnd(entity, runtime);
	}

	private boolean isTrackable(TheQueenOfHatred entity, LivingEntity target) {
		if (target == null || !entity.isValidTarget(target)) {
			return false;
		}
		if (entity.distanceToSqr(target) > USE_RANGE_SQUARED) {
			return false;
		}
		return entity.hasLineOfSight(target);
	}

	private boolean canHit(TheQueenOfHatred entity, LivingEntity target, Vec3 lockedPosition) {
		if (target == null || !entity.isValidTarget(target)) {
			return false;
		}
		if (!entity.hasLineOfSight(target)) {
			return false;
		}
		double offsetX = target.getX() - lockedPosition.x;
		double offsetZ = target.getZ() - lockedPosition.z;
		if (offsetX * offsetX + offsetZ * offsetZ > HIT_RADIUS_SQUARED) {
			return false;
		}
		return Math.abs(target.getY() - lockedPosition.y) <= HIT_VERTICAL_RANGE;
	}

	private void spawnPillar(ServerLevel level, Vec3 position) {
		TheQueenOfHatredPillarOfLight pillar = new TheQueenOfHatredPillarOfLight(
				AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_PILLAR_OF_LIGHT.get(), level);
		pillar.setPos(position);
		level.addFreshEntity(pillar);
	}
}
