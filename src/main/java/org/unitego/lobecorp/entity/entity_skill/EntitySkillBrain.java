package org.unitego.lobecorp.entity.entity_skill;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.registry.brain.LcMemoryModuleTypes;

import java.util.HashMap;
import java.util.Map;

/// 技能系统入口：访问 brain 中的技能内存，提供施放、结束、冷却与状态机推进。
/// <p>
/// 状态机由 {@link EntitySkillController} 在每 tick 调用 {@link #tick} 推进。
/// 实体 AI 逻辑在需要时调用 {@link #cast} 触发技能。
public class EntitySkillBrain {

	private EntitySkillBrain() {
	}

	/// 技能是否处于冷却中
	public static boolean isOnCooldown(Mob entity, IEntitySkill<?> skill) {
		Map<Identifier, Long> cooldowns = entity.getBrain()
				.getMemory(LcMemoryModuleTypes.SKILL_COOLDOWNS.get())
				.orElse(Map.of());
		Long until = cooldowns.get(skill.id());
		return until != null && until > entity.level().getGameTime();
	}

	/// 是否满足施放条件（冷却 + 未占用 + 技能自身条件）
	public static <T extends Mob> boolean canCast(T entity, IEntitySkill<T> skill) {
		if (!canStartCast(entity, skill)) {
			return false;
		}
		EntitySkillRuntime<T> runtime = new EntitySkillRuntime<>(entity, skill,
				EntitySkillRuntime.SkillState.WINDUP, skill.windupTicks());
		return runtime.canUse();
	}

	private static boolean canStartCast(Mob entity, IEntitySkill<?> skill) {
		if (isOnCooldown(entity, skill)) {
			return false;
		}
		if (entity.getBrain().getMemory(LcMemoryModuleTypes.SKILL_ACTIVE.get()).isPresent()) {
			return false;
		}
		if (!(entity instanceof EntitySkillHolder holder) || !holder.skills().contains(skill)) {
			return false;
		}
		return true;
	}

	/// 施放技能。成功则进入前摇并返回 true；失败（冷却/占用/条件不满足）返回 false
	public static <T extends Mob> boolean cast(T entity, IEntitySkill<T> skill) {
		if (!canStartCast(entity, skill)) {
			return false;
		}
		EntitySkillRuntime<T> runtime = new EntitySkillRuntime<>(entity, skill,
				EntitySkillRuntime.SkillState.WINDUP, skill.windupTicks());
		if (!runtime.canUse()) {
			return false;
		}
		entity.getBrain().setMemory(LcMemoryModuleTypes.SKILL_ACTIVE.get(), runtime);
		applyMovementLocks(entity, runtime.skill());
		runtime.onWindupStart();
		if (isCurrent(runtime)) {
			advanceImmediateStates(runtime);
		}
		return true;
	}

	/// 主动结束当前技能：跳过剩余持续阶段，进入后摇。
	/// 仅对无限持续（-1）或希望提前收招的技能使用。
	public static void endSkill(Mob entity) {
		Brain<?> brain = entity.getBrain();
		EntitySkillRuntime<?> runtime = brain.getMemory(LcMemoryModuleTypes.SKILL_ACTIVE.get()).orElse(null);
		if (runtime == null || runtime.state() != EntitySkillRuntime.SkillState.ACTIVE) {
			return;
		}
		runtime.onEnd();
		enterRecovery(runtime);
		advanceImmediateStates(runtime);
	}

	/// 立即终止当前技能：无后摇，直接进入冷却
	public static void cancelSkill(Mob entity) {
		cancelSkill(entity, false);
	}

	/// 由技能自身在目标失效等无法继续执行的情况下强制取消。
	public static void forceCancelSkill(Mob entity) {
		cancelSkill(entity, true);
	}

	private static void cancelSkill(Mob entity, boolean forced) {
		Brain<?> brain = entity.getBrain();
		EntitySkillRuntime<?> runtime = brain.getMemory(LcMemoryModuleTypes.SKILL_ACTIVE.get()).orElse(null);
		if (runtime == null) {
			return;
		}
		if (!forced && runtime.state() == EntitySkillRuntime.SkillState.WINDUP
				&& !runtime.skill().interruptibleDuringWindup()) {
			return;
		}
		runtime.onCancel();
		brain.eraseMemory(LcMemoryModuleTypes.SKILL_ACTIVE.get());
		startCooldown(entity, runtime.skill());
	}

	/// 当前是否正在施放指定技能。
	public static boolean isCasting(Mob entity, IEntitySkill<?> skill) {
		return entity.getBrain().getMemory(LcMemoryModuleTypes.SKILL_ACTIVE.get())
				.map(runtime -> runtime.skill() == skill)
				.orElse(false);
	}

	/// 由 {@link EntitySkillController} 每 tick 调用，推进状态机。
	///
	/// @param level   服务端世界（用于获取世界 tick 计算冷却）
	/// @param entity  技能持有实体
	/// @param runtime 当前技能运行时状态
	public static void tick(ServerLevel level, Mob entity, EntitySkillRuntime<?> runtime) {
		if (runtime.owner() != entity) {
			entity.getBrain().eraseMemory(LcMemoryModuleTypes.SKILL_ACTIVE.get());
			return;
		}
		applyMovementLocks(entity, runtime.skill());
		switch (runtime.state()) {
			case WINDUP -> {
				runtime.onWindupTick();
				if (!isCurrent(runtime) || runtime.state() != EntitySkillRuntime.SkillState.WINDUP) {
					return;
				}
				runtime.setTicksLeft(runtime.ticksLeft() - 1);
				if (runtime.ticksLeft() > 0) {
					return;
				}
				activate(runtime);
				if (isCurrent(runtime)) {
					advanceImmediateStates(runtime);
				}
			}
			case ACTIVE -> {
				runtime.incrementActiveTicks();
				runtime.onTick();
				if (!isCurrent(runtime) || runtime.state() != EntitySkillRuntime.SkillState.ACTIVE) {
					return;
				}
				if (runtime.ticksLeft() == -1) {
					return;
				}
				runtime.setTicksLeft(runtime.ticksLeft() - 1);
				if (runtime.ticksLeft() > 0) {
					return;
				}
				runtime.onEnd();
				enterRecovery(runtime);
				advanceImmediateStates(runtime);
			}
			case RECOVERY -> {
				runtime.setTicksLeft(runtime.ticksLeft() - 1);
				if (runtime.ticksLeft() > 0) {
					return;
				}
				finish(runtime);
			}
		}
	}

	private static void activate(EntitySkillRuntime<?> runtime) {
		runtime.setState(EntitySkillRuntime.SkillState.ACTIVE);
		runtime.setTicksLeft(runtime.skill().durationTicks());
		runtime.onActivate();
	}

	private static void enterRecovery(EntitySkillRuntime<?> runtime) {
		runtime.setState(EntitySkillRuntime.SkillState.RECOVERY);
		runtime.setTicksLeft(runtime.skill().recoveryTicks());
	}

	private static void advanceImmediateStates(EntitySkillRuntime<?> runtime) {
		if (runtime.state() == EntitySkillRuntime.SkillState.WINDUP && runtime.ticksLeft() <= 0) {
			activate(runtime);
		}
		if (runtime.state() == EntitySkillRuntime.SkillState.ACTIVE && runtime.ticksLeft() == 0) {
			runtime.onEnd();
			enterRecovery(runtime);
		}
		if (runtime.state() == EntitySkillRuntime.SkillState.RECOVERY && runtime.ticksLeft() <= 0) {
			finish(runtime);
		}
	}

	private static void finish(EntitySkillRuntime<?> runtime) {
		runtime.onRecoveryEnd();
		startCooldown(runtime.owner(), runtime.skill());
		runtime.owner().getBrain().eraseMemory(LcMemoryModuleTypes.SKILL_ACTIVE.get());
	}

	private static boolean isCurrent(EntitySkillRuntime<?> runtime) {
		return runtime.owner().getBrain().getMemory(LcMemoryModuleTypes.SKILL_ACTIVE.get()).orElse(null) == runtime;
	}

	private static void applyMovementLocks(Mob entity, IEntitySkill<?> skill) {
		if (skill.locksNavigation()) {
			entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			entity.getNavigation().stop();
		}
		if (skill.locksMovement()) {
			Vec3 movement = entity.getDeltaMovement();
			entity.setDeltaMovement(0.0, movement.y, 0.0);
		}
	}

	/// 手动设置技能的冷却（用于技能内部自定义冷却时机，如一套连击完成后）
	public static void setCooldown(Mob entity, IEntitySkill<?> skill, int ticks) {
		if (ticks <= 0) {
			return;
		}
		Brain<?> brain = entity.getBrain();
		Map<Identifier, Long> cooldowns = new HashMap<>(brain.getMemory(LcMemoryModuleTypes.SKILL_COOLDOWNS.get()).orElse(Map.of()));
		cooldowns.put(skill.id(), entity.level().getGameTime() + ticks);
		brain.setMemory(LcMemoryModuleTypes.SKILL_COOLDOWNS.get(), cooldowns);
	}

	private static void startCooldown(Mob entity, IEntitySkill<?> skill) {
		setCooldown(entity, skill, skill.cooldownTicks());
	}
}
