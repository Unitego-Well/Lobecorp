package org.unitego.lobecorp.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/// 走向任意实体记忆的目标。
public class WalkToEntity {

    /// 适用于任意 Entity（ItemEntity、LivingEntity 等）。
    /// 目标记忆存在时持续走向目标，不存在时自动跳过。
    public static <E extends PathfinderMob, T extends Entity> BehaviorControl<E> create(
            MemoryModuleType<T> targetMemory, float speedModifier, int closeEnoughDist) {
        return BehaviorBuilder.create(i -> i.group(
                i.present(targetMemory)
        ).apply(i, (target) -> (level, body, time) -> {
            T entity = i.get(target);
            if (!entity.isAlive()) {
                return false;
            }
            BehaviorUtils.setWalkAndLookTargetMemories(body, entity, speedModifier, closeEnoughDist);
            return true;
        }));
    }

    /// LivingEntity 版：目标死亡时自动擦除记忆。
    public static <E extends PathfinderMob, T extends LivingEntity> BehaviorControl<E> createLiving(
            MemoryModuleType<T> targetMemory, float speedModifier, int closeEnoughDist) {
        return BehaviorBuilder.create(i -> i.group(
                i.present(targetMemory)
        ).apply(i, (target) -> (level, body, time) -> {
            T entity = i.get(target);
            if (!entity.isAlive()) {
                body.getBrain().eraseMemory(targetMemory);
                return false;
            }
            BehaviorUtils.setWalkAndLookTargetMemories(body, entity, speedModifier, closeEnoughDist);
            return true;
        }));
    }
}
