package org.unitego.lobecorp.entity.ai.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.unitego.lobecorp.init.brain.LcMemoryModuleTypes;

import java.util.HashMap;
import java.util.Map;

/// 技能系统入口：访问 brain 中的技能内存，提供施放、结束、冷却与状态机推进。
/// <p>
/// 状态机由 {@link SkillController} 在每 tick 调用 {@link #tick} 推进。
/// 实体 AI 逻辑在需要时调用 {@link #cast} 触发技能。
public class SkillBrain {

    /// 当前激活技能的状态（运行时，不持久化）
    public static final MemoryModuleType<SkillRuntime> SKILL_ACTIVE = LcMemoryModuleTypes.SKILL_ACTIVE.get();
    /// 技能冷却表：skillId → 冷却结束的世界 tick（运行时，不持久化）
    public static final MemoryModuleType<Map<String, Long>> SKILL_COOLDOWNS = LcMemoryModuleTypes.SKILL_COOLDOWNS.get();

    private SkillBrain() {
    }

    /// 技能是否处于冷却中
    public static boolean isOnCooldown(Mob entity, Skill skill) {
        Map<String, Long> cooldowns = entity.getBrain().getMemory(SKILL_COOLDOWNS).orElse(Map.of());
        Long until = cooldowns.get(skill.id());
        return until != null && until > entity.level().getGameTime();
    }

    /// 是否满足施放条件（冷却 + 未占用 + 技能自身条件）
    public static boolean canCast(Mob entity, Skill skill) {
        if (isOnCooldown(entity, skill)) return false;
        if (entity.getBrain().getMemory(SKILL_ACTIVE).isPresent()) return false;
        return skill.canUse(entity, null);
    }

    /// 施放技能。成功则进入前摇并返回 true；失败（冷却/占用/条件不满足）返回 false
    public static boolean cast(Mob entity, Skill skill) {
        if (!canCast(entity, skill)) return false;
        SkillRuntime runtime = new SkillRuntime(skill, SkillState.WINDUP, skill.windupTicks());
        entity.getBrain().setMemory(SKILL_ACTIVE, runtime);
        skill.onWindupStart(entity, runtime);
        return true;
    }

    /// 主动结束当前技能：跳过剩余持续阶段，进入后摇。
    /// 仅对无限持续（-1）或希望提前收招的技能使用。
    public static void endSkill(Mob entity) {
        Brain<?> brain = entity.getBrain();
        SkillRuntime runtime = brain.getMemory(SKILL_ACTIVE).orElse(null);
        if (runtime == null || runtime.state() != SkillState.ACTIVE) return;
        runtime.skill().onEnd(entity, runtime);
        runtime.setState(SkillState.RECOVERY);
        runtime.setTicksLeft(runtime.skill().recoveryTicks());
    }

    /// 立即终止当前技能：无后摇，直接进入冷却
    public static void cancelSkill(Mob entity) {
        Brain<?> brain = entity.getBrain();
        SkillRuntime runtime = brain.getMemory(SKILL_ACTIVE).orElse(null);
        if (runtime == null) return;
        brain.eraseMemory(SKILL_ACTIVE);
        startCooldown(entity, runtime.skill());
    }

    /// 由 {@link SkillController} 每 tick 调用，推进状态机。
    ///
    /// @param level   服务端世界（用于获取世界 tick 计算冷却）
    /// @param entity  技能持有实体
    /// @param runtime 当前技能运行时状态
    public static void tick(ServerLevel level, Mob entity, SkillRuntime runtime) {
        Skill skill = runtime.skill();
        runtime.setTicksLeft(runtime.ticksLeft() - 1);
        int ticksLeft = runtime.ticksLeft();

        switch (runtime.state()) {
            case WINDUP -> {
                if (ticksLeft > 0) return;
                skill.onActivate(entity, runtime);
                int duration = skill.durationTicks();
                if (duration == 0) {
                    // 无持续阶段，直接结束进入后摇
                    skill.onEnd(entity, runtime);
                    enterRecovery(runtime, skill);
                } else {
                    runtime.setState(SkillState.ACTIVE);
                    runtime.setTicksLeft(duration);
                }
            }
            case ACTIVE -> {
                if (ticksLeft != -1 && ticksLeft <= 0) {
                    skill.onEnd(entity, runtime);
                    enterRecovery(runtime, skill);
                    return;
                }
                skill.onTick(entity, runtime);
            }
            case RECOVERY -> {
                if (ticksLeft > 0) return;
                skill.onRecoveryEnd(entity, runtime);
                startCooldown(entity, skill);
                entity.getBrain().eraseMemory(SKILL_ACTIVE);
            }
        }
    }

    private static void enterRecovery(SkillRuntime runtime, Skill skill) {
        runtime.setState(SkillState.RECOVERY);
        runtime.setTicksLeft(skill.recoveryTicks());
    }

    /// 手动设置技能的冷却（用于技能内部自定义冷却时机，如一套连击完成后）
    public static void setCooldown(Mob entity, Skill skill, int ticks) {
        if (ticks <= 0) return;
        Brain<?> brain = entity.getBrain();
        Map<String, Long> cooldowns = new HashMap<>(brain.getMemory(SKILL_COOLDOWNS).orElse(Map.of()));
        cooldowns.put(skill.id(), entity.level().getGameTime() + ticks);
        brain.setMemory(SKILL_COOLDOWNS, cooldowns);
    }

    private static void startCooldown(Mob entity, Skill skill) {
        setCooldown(entity, skill, skill.cooldownTicks());
    }
}
