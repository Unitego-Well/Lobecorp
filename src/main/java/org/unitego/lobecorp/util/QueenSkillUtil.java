package org.unitego.lobecorp.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// 女皇技能共用的法杖起点、目标选择和施法姿态，不保存实体运行状态。
public class QueenSkillUtil {
	/// 二阶段最多锁定的敌人数；一阶段仅锁定主目标。
	private static final int PHASE_TWO_TARGET_COUNT = 3;
	/// aim 姿态法杖中心的服务端局部估计位置，单位为格。
	private static final Vec3 STAFF_ORIGIN = new Vec3(-0.287, 1.145, 1.560);
	/// aim2 姿态法杖中心的服务端局部估计位置，单位为格。
	private static final Vec3 PHASE_TWO_STAFF_ORIGIN = new Vec3(-0.987, 0.687, 0.604);

	public static Vec3 staffOrigin(Mob mob) {
		return EntityFacingUtil.localPosition(mob, mob instanceof TheQueenOfHatred queen && queen.isPhaseTwo()
				? PHASE_TWO_STAFF_ORIGIN : STAFF_ORIGIN);
	}

	public static Vec3 beamEndpoint(Mob mob, Vec3 position, double range) {
		Vec3 start = staffOrigin(mob);
		Vec3 offset = position.subtract(start);
		Vec3 direction = offset.lengthSqr() > 0.0 ? offset.normalize() : mob.getLookAngle();
		return mob.level().clip(new ClipContext(start, start.add(direction.scale(range)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob)).getLocation();
	}

	@Nullable
	public static LivingEntity primaryTarget(TheQueenOfHatred queen, EntitySkillRuntime<?> runtime, double range) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target == null && runtime.targetPosition() == null) {
			target = queen.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
			if (target != null) runtime.setTarget(target);
		}
		return validTarget(queen, target, range) ? target : null;
	}

	public static boolean validTarget(TheQueenOfHatred queen, @Nullable LivingEntity target, double range) {
		return target != null && target.isAlive() && target.level() == queen.level()
				&& queen.isHatedTarget(target) && queen.hasLineOfSight(target)
				&& queen.distanceToSqr(target) <= range * range;
	}

	/// 空地施放只使用请求中选定的位置，不退回 Brain 目标；仍校验最大施法距离。
	public static boolean validTargetPosition(TheQueenOfHatred queen, EntitySkillRuntime<?> runtime, double range) {
		Vec3 position = runtime.targetPosition();
		return runtime.target(LivingEntity.class) == null && position != null
				&& queen.position().distanceToSqr(position) <= range * range;
	}

	/// 保留原实体选择规则，同时允许在施法距离内选择空地。
	public static boolean canTarget(TheQueenOfHatred queen, EntitySkillRuntime<?> runtime, double range) {
		return primaryTarget(queen, runtime, range) != null || validTargetPosition(queen, runtime, range);
	}

	/// 单个落点技能优先追踪主目标，无实体目标时使用选定位置。
	@Nullable
	public static Vec3 primaryPosition(TheQueenOfHatred queen, EntitySkillRuntime<?> runtime, double range) {
		LivingEntity target = primaryTarget(queen, runtime, range);
		return target != null ? target.position() : validTargetPosition(queen, runtime, range) ? runtime.targetPosition() : null;
	}

	/// 实体施放沿用阶段目标数量；空地施放只产生一个选定落点。
	public static List<Vec3> targetPositions(TheQueenOfHatred queen, EntitySkillRuntime<?> runtime, double range) {
		List<LivingEntity> targets = targets(queen, runtime, range);
		if (!targets.isEmpty()) return targets.stream().map(LivingEntity::position).toList();
		Vec3 position = primaryPosition(queen, runtime, range);
		return position == null ? List.of() : List.of(position);
	}

	/// 优先保留所选主目标，二阶段再补充距女皇最近的有效敌人。
	public static List<LivingEntity> targets(TheQueenOfHatred queen, EntitySkillRuntime<?> runtime, double range) {
		LivingEntity primary = primaryTarget(queen, runtime, range);
		if (primary == null) return List.of();
		List<LivingEntity> targets = new ArrayList<>();
		targets.add(primary);
		if (queen.isPhaseTwo() && queen.level() instanceof ServerLevel level) {
			level.getEntitiesOfClass(LivingEntity.class, queen.getBoundingBox().inflate(range),
					candidate -> candidate != primary && validTarget(queen, candidate, range)).stream()
					.sorted(Comparator.comparingDouble(queen::distanceToSqr))
					.limit(PHASE_TWO_TARGET_COUNT - targets.size()).forEach(targets::add);
		}
		return List.copyOf(targets);
	}

	public static void startSpell(TheQueenOfHatred queen) {
		queen.playActionAnimation(TheQueenOfHatredAnim.SPELL);
		queen.lockSkillFacing();
	}
}
