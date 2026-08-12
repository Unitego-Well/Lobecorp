package org.unitego.lobecorp.entity.ai.skill;

/// 技能状态机阶段
public enum SkillState {
    /// 前摇：施放准备，效果尚未生效
    WINDUP,
    /// 持续：技能生效中（-1 为无限）
    ACTIVE,
    /// 后摇：效果结束，收招阶段
    RECOVERY
}
