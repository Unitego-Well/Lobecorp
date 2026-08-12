package org.unitego.lobecorp.entity.ai.skill;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;

/// 驱动技能状态机的常驻行为。
/// <p>
/// 实现 {@link SkillHolder} 的实体应将此行为注册进 brain 的 {@code Activity.CORE}，
/// 使其在任意活动下都持续推进技能状态机。
///
/// @param <E> 实现 {@link SkillHolder} 的实体类型
public class SkillController<E extends Mob & SkillHolder> extends Behavior<E> {

    public SkillController() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E owner) {
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E owner, long timestamp) {
        return true;
    }

    @Override
    protected boolean timedOut(long timestamp) {
        return false;
    }

    @Override
    protected void tick(ServerLevel level, E owner, long timestamp) {
        SkillRuntime runtime = owner.getBrain().getMemory(SkillBrain.SKILL_ACTIVE).orElse(null);
        if (runtime != null) {
            SkillBrain.tick(level, owner, runtime);
        }
    }
}
