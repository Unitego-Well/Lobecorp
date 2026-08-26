package org.unitego.lobecorp.entity.ai.skill;

/// 单个技能运行时的可变状态，存放于实体 brain 内存（不持久化）。
public final class EntitySkillRuntime {
    private final IEntitySkill skill;
    private SkillState state;
    private int ticksLeft;

    public EntitySkillRuntime(IEntitySkill skill, SkillState state, int ticksLeft) {
        this.skill = skill;
        this.state = state;
        this.ticksLeft = ticksLeft;
    }

    public IEntitySkill skill() {
        return skill;
    }

    public SkillState state() {
        return state;
    }

    public void setState(SkillState state) {
        this.state = state;
    }

    /// 当前阶段剩余 tick。-1 仅用于无限持续的 ACTIVE 阶段
    public int ticksLeft() {
        return ticksLeft;
    }

    public void setTicksLeft(int ticksLeft) {
        this.ticksLeft = ticksLeft;
    }

    /// 技能状态机阶段
    public enum SkillState {
        /// 前摇：施放准备，效果尚未生效
        WINDUP,
        /// 持续：技能生效中（-1 为无限）
        ACTIVE,
        /// 后摇：效果结束，收招阶段
        RECOVERY
    }
}
