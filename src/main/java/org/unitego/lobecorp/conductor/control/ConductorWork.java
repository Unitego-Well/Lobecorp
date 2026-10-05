package org.unitego.lobecorp.conductor.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.ordeal.indigo.skill.ReassembleSkill;
import org.unitego.lobecorp.world.entity.ordeal.indigo.skill.SweepSkill;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.registry.entity.ai.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.entity.ai.LcSensorTypes;
import org.unitego.lobecorp.registry.entity.skill.SweeperSkills;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/// 清道夫清理与重组持续工作的运行控制。
public final class ConductorWork {

	private ConductorWork() {
	}

	public static boolean active(Mob mob) {
		if (!(mob instanceof Sweeper) || !(mob.level() instanceof ServerLevel level))
			return false;
		ConductorData.Unit unit = ConductorData.get(level.getServer()).unit(mob.getUUID());
		return unit != null && (unit.order() == ConductorData.OrderType.CLEANUP
				|| unit.order() == ConductorData.OrderType.REASSEMBLE);
	}

	public static boolean start(Sweeper mob, boolean reassemble) {
		if (!(mob.level() instanceof ServerLevel level))
			return false;
		ConductorData data = ConductorData.get(level.getServer());
		ConductorData.Unit unit = data.unit(mob.getUUID());
		if (unit == null)
			return false;
		ConductorController.stop(mob);
		mob.getBrain().stopAll(level, mob);
		ConductorUnitRuntime.get(mob).work = new Work(reassemble);
		data.update(mob.getUUID(), state -> state.command(reassemble ? ConductorData.OrderType.REASSEMBLE
				: ConductorData.OrderType.CLEANUP, null, mob.position()));
		return true;
	}

	public static void cancel(Mob mob) {
		if (!(mob instanceof Sweeper sweeper))
			return;
		ConductorUnitRuntime.get(sweeper).work = null;
		sweeper.getBrain().eraseMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get());
		EntitySkillUtil.forceCancelSkills(sweeper, SweeperSkills.SWEEP.get(), SweeperSkills.REASSEMBLE.get());
	}

	public static void tick(Sweeper mob, ServerLevel level, ConductorData data, ConductorData.Unit unit) {
		if (ConductorUnitRuntime.get(mob).work == null)
			ConductorUnitRuntime.get(mob).work = new Work(unit.order() == ConductorData.OrderType.REASSEMBLE);
		Work work = ConductorUnitRuntime.get(mob).work;
		mob.setTarget(null);
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		if (EntitySkillUtil.hasActiveSkills(mob))
			return;
		Entity target = work.target == null ? null : level.getEntity(work.target);
		if (!valid(mob, target, work)) {
			target = level.getEntities(mob, mob.getBoundingBox().inflate(LcSensorTypes.CLEANUP_HORIZONTAL_RANGE,
									LcSensorTypes.CLEANUP_VERTICAL_RANGE, LcSensorTypes.CLEANUP_HORIZONTAL_RANGE),
							candidate -> valid(mob, candidate, work))
					.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
			work.target = target == null ? null : target.getUUID();
			work.progress = mob.position();
			work.progressAt = level.getGameTime();
			work.retryAt = 0;
		}
		if (target == null) {
			ConductorController.stop(mob);
			data.update(mob.getUUID(), state -> state.command(ConductorData.OrderType.NONE, null, mob.position()));
			return;
		}
		mob.getBrain().setMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get(), target);
		if (SweepSkill.isWithinRange(mob, target)) {
			mob.getNavigation().stop();
			mob.getMoveControl().setWait();
			var skill = work.reassemble ? SweeperSkills.REASSEMBLE.get() : SweeperSkills.SWEEP.get();
			if (!EntitySkillUtil.isOnCooldown(mob, skill) && !ConductorController.cast(mob, skill, target, null)) {
				work.skipped.add(target.getUUID());
				work.target = null;
			}
			return;
		}
		long now = level.getGameTime();
		if (work.progress == null || mob.position().distanceToSqr(work.progress)
				>= ConductorRules.MOVEMENT_PROGRESS_DISTANCE * ConductorRules.MOVEMENT_PROGRESS_DISTANCE) {
			work.progress = mob.position();
			work.progressAt = now;
		}
		if (now - work.progressAt >= ConductorRules.MOVEMENT_STALL_TICKS) {
			work.skipped.add(target.getUUID());
			work.target = null;
			mob.getNavigation().stop();
			mob.getMoveControl().setWait();
			return;
		}
		if (now >= work.retryAt && mob.getNavigation().isDone()) {
			work.retryAt = now + ConductorRules.MOVEMENT_RETRY_TICKS;
			Path path = mob.getNavigation().createPath(target, 0);
			if (path == null || !path.canReach()) {
				mob.getNavigation().stop();
				mob.getMoveControl().setWait();
				work.skipped.add(target.getUUID());
				work.target = null;
				return;
			}
			mob.getNavigation().moveTo(path, ConductorRules.MOVEMENT_SPEED);
		}
	}

	private static boolean valid(Sweeper mob, Entity target, Work work) {
		if (target == null || !target.isAlive() || work.skipped.contains(target.getUUID()))
			return false;
		if (work.reassemble)
			return target instanceof EntityCorpse<?> corpse && ReassembleSkill.canReassemble(mob, corpse);
		return target instanceof EntityCorpse<?> corpse && !(corpse.getOwnerEntity() instanceof Sweeper)
				|| target instanceof ItemEntity item && !item.getItem().isEmpty();
	}

	protected static final class Work {
		private final boolean reassemble;
		private final Set<UUID> skipped = new HashSet<>();
		private UUID target;
		private Vec3 progress;
		private long progressAt;
		private long retryAt;

		private Work(boolean reassemble) {
			this.reassemble = reassemble;
		}
	}
}
