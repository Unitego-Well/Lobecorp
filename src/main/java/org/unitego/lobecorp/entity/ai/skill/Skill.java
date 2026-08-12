package org.unitego.lobecorp.entity.ai.skill;

import net.minecraft.world.entity.Mob;

/// 通用技能定义。
/// <p>
/// 技能状态机由 {@link SkillBrain} 驱动：
/// <pre>
///  前摇(WINDUP) → 持续(ACTIVE) → 后摇(RECOVERY) → 冷却(COOLDOWN)
/// </pre>
/// 实现此接口并注册到 {@link SkillHolder#skills()} 即可使用，只重写需要的方法。
public interface Skill {

    /// 技能唯一标识，用于冷却表与日志
    String id();

    /// 前摇 tick（施放准备阶段，效果不生效）
    int windupTicks();

    /// 持续时间 tick。-1 表示无限，直到调用 {@link SkillBrain#endSkill} 主动结束
    int durationTicks();

    /// 后摇 tick（效果结束后收招阶段）
    int recoveryTicks();

    /// 冷却 tick（技能完全结束后开始计时）
    int cooldownTicks();

    /// 施放前的额外条件（冷却与占用检查由系统完成，此方法补充业务条件）
    default boolean canUse(Mob entity, SkillRuntime runtime) {
        return true;
    }

    /// 前摇开始
    default void onWindupStart(Mob entity, SkillRuntime runtime) {
    }

    /// 效果触发（前摇结束，技能生效）
    default void onActivate(Mob entity, SkillRuntime runtime) {
    }

    /// 持续期间每 tick 调用
    default void onTick(Mob entity, SkillRuntime runtime) {
    }

    /// 持续结束（进入后摇前）
    default void onEnd(Mob entity, SkillRuntime runtime) {
    }

    /// 后摇结束（进入冷却前）
    default void onRecoveryEnd(Mob entity, SkillRuntime runtime) {
    }
}
