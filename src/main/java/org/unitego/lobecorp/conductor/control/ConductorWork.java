package org.unitego.lobecorp.conductor.control;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import org.unitego.lobecorp.conductor.config.ConductorRules;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.registry.world.entity.ai.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.world.entity.ai.LcSensorTypes;
import org.unitego.lobecorp.registry.world.entity.skill.SweeperSkills;
import org.unitego.lobecorp.util.entity.ai.SweeperWorkUtil;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.world.entity.EntityCorpse;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.ordeal.indigo.skill.ReassembleSkill;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/// 清道夫清理与重组持续工作的运行控制。
public final class ConductorWork {

	private ConductorWork() {
	}

	/// 仅 CLEANUP 与 REASSEMBLE 两种持续指令视为工作激活。
	public static boolean active(Mob mob) {
		if (!(mob instanceof Sweeper) || !(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		ConductorData.Unit unit = ConductorData.get(level.getServer()).unit(mob.getUUID());
		return unit != null && (unit.order() == ConductorData.OrderType.CLEANUP
				|| unit.order() == ConductorData.OrderType.REASSEMBLE);
	}

	/// 开始工作前停止控制器与原版 Brain，避免旧指令继续驱动单位。
	public static boolean start(Sweeper mob, boolean reassemble) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		ConductorData data = ConductorData.get(level.getServer());
		ConductorData.Unit unit = data.unit(mob.getUUID());
		if (unit == null) {
			return false;
		}
		ConductorController.stop(mob);
		mob.getBrain().stopAll(level, mob);
		ConductorUnitRuntime.get(mob).work = new Work(reassemble);
		data.update(mob.getUUID(), state -> state.command(reassemble ? ConductorData.OrderType.REASSEMBLE
				: ConductorData.OrderType.CLEANUP, null, mob.position()));
		return true;
	}

	public static void cancel(Mob mob) {
		if (!(mob instanceof Sweeper sweeper)) {
			return;
		}
		ConductorUnitRuntime.get(sweeper).work = null;
		sweeper.getBrain().eraseMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get());
		EntitySkillUtil.forceCancelSkills(sweeper, SweeperSkills.SWEEP.get(), SweeperSkills.REASSEMBLE.get());
	}

	/// 工作 tick 先让位于活动技能，再维护目标、接近与施放重试。
	public static void tick(Sweeper mob, ServerLevel level, ConductorData data, ConductorData.Unit unit) {
		if (ConductorUnitRuntime.get(mob).work == null) {
			ConductorUnitRuntime.get(mob).work = new Work(unit.order() == ConductorData.OrderType.REASSEMBLE);
		}
		Work work = ConductorUnitRuntime.get(mob).work;
		mob.setTarget(null);
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		long now = level.getGameTime();
		if (EntitySkillUtil.hasActiveSkills(mob)) {
			work.approach.pause(mob, now);
			return;
		}

		Entity target = work.target == null ? null : level.getEntity(work.target);
		if (!valid(mob, target, work)) {
			target = level.getEntities(mob, mob.getBoundingBox().inflate(LcSensorTypes.CLEANUP_HORIZONTAL_RANGE,
									LcSensorTypes.CLEANUP_VERTICAL_RANGE, LcSensorTypes.CLEANUP_HORIZONTAL_RANGE),
							candidate -> valid(mob, candidate, work))
					.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
			work.target = target == null ? null : target.getUUID();
			work.approach.clear(mob);
			work.retryAt = 0;
		}

		if (target == null) {
			ConductorController.stop(mob);
			data.update(mob.getUUID(), state -> state.command(ConductorData.OrderType.NONE, null, mob.position()));
			return;
		}

		mob.getBrain().setMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get(), target);
		if (!work.approach.advance(mob, target, ConductorRules.MOVEMENT_SPEED, now)) {
			work.skipped.add(target.getUUID());
			work.target = null;
			work.approach.clear(mob);
			return;
		}

		if (!SweeperWorkUtil.isReady(mob, target)) {
			return;
		}

		var skill = work.reassemble ? SweeperSkills.REASSEMBLE.get() : SweeperSkills.SWEEP.get();
		if (now >= work.retryAt && !EntitySkillUtil.isOnCooldown(mob, skill)
				&& EntitySkillUtil.require(mob).canBeginCast(skill)) {
			work.retryAt = now + ConductorRules.MOVEMENT_RETRY_TICKS;
			ConductorController.cast(mob, skill, target, null);
		}
	}

	/// 重组只接受可重组尸体；清理接受非清道夫来源尸体或非空掉落物。
	private static boolean valid(Sweeper mob, Entity target, Work work) {
		if (target == null || !target.isAlive() || work.skipped.contains(target.getUUID())) {
			return false;
		}
		if (work.reassemble) {
			return target instanceof EntityCorpse<?> corpse && ReassembleSkill.canReassemble(mob, corpse);
		}
		return target instanceof EntityCorpse<?> corpse && !(corpse.getOwnerEntity() instanceof Sweeper)
				|| target instanceof ItemEntity item && !item.getItem().isEmpty();
	}

	protected static final class Work {
		private final boolean reassemble;
		// 本次工作中不再尝试的目标，目标刷新时保留。
		private final Set<UUID> skipped = new HashSet<>();
		private final SweeperWorkUtil.Approach approach = new SweeperWorkUtil.Approach();
		// 当前目标 UUID，工作时按 UUID 从世界解析实体。
		private UUID target;
		// 下一次允许尝试施放的游戏时间。
		private long retryAt;

		private Work(boolean reassemble) {
			this.reassemble = reassemble;
		}
	}
}
