package org.unitego.lobecorp.entity.ai.skill;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;

/// 驱动技能状态机的常驻行为。
/// <p>
/// 实现 {@link EntitySkillHolder} 的实体应将此行为注册进 brain 的 {@code Activity.CORE}，
/// 使其在任意活动下都持续推进技能状态机。
///
/// @param <E> 实现 {@link EntitySkillHolder} 的实体类型
public class EntitySkillController extends Behavior<Mob> {

    public EntitySkillController() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Mob owner) {
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Mob owner, long timestamp) {
        return true;
    }

    @Override
    protected boolean timedOut(long timestamp) {
        return false;
    }

    @Override
    protected void tick(ServerLevel level, Mob owner, long timestamp) {
        EntitySkillRuntime runtime = owner.getBrain().getMemory(EntitySkillBrain.SKILL_ACTIVE).orElse(null);
        if (runtime != null) {
            EntitySkillBrain.tick(level, owner, runtime);
        }
    }
}
