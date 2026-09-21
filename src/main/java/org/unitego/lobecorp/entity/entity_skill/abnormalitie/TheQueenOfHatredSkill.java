package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.entity_skill.EntitySkill;

import java.util.List;
import java.util.ArrayList;

/// 憎恶皇后技能的公共基类。
public abstract class TheQueenOfHatredSkill extends EntitySkill<TheQueenOfHatred> {
	/// 二阶段多目标技能最多锁定的目标数量。
	protected static final int SECOND_PHASE_TARGET_COUNT = 3;
	/// 临时攻击范围圆环的粒子数量。
	private static final int ATTACK_MARKER_PARTICLE_COUNT = 24;
	/// 寻找预测点所在表面的向上射线余量。
	private static final double ATTACK_MARKER_TRACE_HEIGHT = 4.0;
	/// 寻找预测点所在表面的向下射线距离。
	private static final double ATTACK_MARKER_TRACE_DEPTH = 32.0;
	/// 避免临时粒子与地面重叠的高度。
	private static final double ATTACK_MARKER_HEIGHT_OFFSET = 0.05;
	/// @param properties 注册阶段提供的不可变基础配置
	protected TheQueenOfHatredSkill(Properties properties) {
		super(properties);
	}

	/// @return 该技能对应的攻击站位模式
	public abstract TheQueenOfHatredAttackMode attackMode();

	/// @return 规划器尝试施放该技能的最小距离
	public abstract double minimumPlanningRange();

	/// @return 规划器尝试施放该技能的最大距离
	public abstract double maximumPlanningRange();

	/// @return 本次重新规划时允许该技能进入候选列表的概率
	public float planningChance() {
		return 1.0F;
	}

	/// @return 参与加权随机选择的基础权重
	public double planningWeight(TheQueenOfHatred entity, LivingEntity target, int nearbyEnemyCount) {
		return 1.0;
	}

	/// @return 规划器用于范围判断的目标距离
	public double planningDistance(TheQueenOfHatred entity, LivingEntity target) {
		return entity.distanceTo(target);
	}

	/// @return 该技能本次计划应锁定的目标
	public LivingEntity selectPlanningTarget(TheQueenOfHatred entity, List<LivingEntity> enemies) {
		LivingEntity current = entity.getAttackTarget();
		return current != null && entity.isValidTarget(current) ? current : enemies.getFirst();
	}

	/// @return 该技能是否偏好敌人密集区域作为目标
	public boolean prefersDenseTarget() {
		return false;
	}

	/// 将预测位置投射到其下方的可碰撞表面。
	protected Vec3 groundPosition(TheQueenOfHatred entity, Vec3 predictedPosition) {
		Vec3 start = predictedPosition.add(0.0, ATTACK_MARKER_TRACE_HEIGHT, 0.0);
		Vec3 end = predictedPosition.subtract(0.0, ATTACK_MARKER_TRACE_DEPTH, 0.0);
		HitResult hit = entity.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, entity));
		if (hit.getType() == HitResult.Type.MISS) {
			return predictedPosition;
		}
		return hit.getLocation().add(0.0, ATTACK_MARKER_HEIGHT_OFFSET, 0.0);
	}

	/// 在预测攻击位置显示临时粒子圆环。
	protected void showGroundMarker(ServerLevel level, Vec3 center, double radius, ParticleOptions particle) {
		for (int index = 0; index < ATTACK_MARKER_PARTICLE_COUNT; index++) {
			double angle = Mth.TWO_PI * index / ATTACK_MARKER_PARTICLE_COUNT;
			level.sendParticles(particle, center.x + Math.cos(angle) * radius, center.y,
					center.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	protected List<LivingEntity> selectPhaseTargets(TheQueenOfHatred entity, LivingEntity primary,
			double range, boolean requireLineOfSight) {
		return refreshPhaseTargets(entity, primary == null ? List.of() : List.of(primary), range, requireLineOfSight);
	}

	protected List<LivingEntity> refreshPhaseTargets(TheQueenOfHatred entity, List<LivingEntity> locked,
			double range, boolean requireLineOfSight) {
		int maximum = entity.isSecondPhase() ? SECOND_PHASE_TARGET_COUNT : 1;
		double rangeSquared = range * range;
		List<LivingEntity> selected = new ArrayList<>(maximum);
		for (LivingEntity target : locked) {
			if (selected.size() >= maximum) {
				break;
			}
			if (entity.isValidTarget(target) && entity.distanceToSqr(target) <= rangeSquared
					&& (!requireLineOfSight || entity.hasLineOfSight(target))) {
				selected.add(target);
			}
		}
		List<LivingEntity> candidates = new ArrayList<>(entity.level().getEntitiesOfClass(LivingEntity.class,
				entity.getBoundingBox().inflate(range), target -> entity.isValidTarget(target)
						&& entity.distanceToSqr(target) <= rangeSquared
						&& (!requireLineOfSight || entity.hasLineOfSight(target))
						&& !selected.contains(target)));
		while (selected.size() < maximum && !candidates.isEmpty()) {
			selected.add(candidates.remove(entity.getRandom().nextInt(candidates.size())));
		}
		return List.copyOf(selected);
	}
}
