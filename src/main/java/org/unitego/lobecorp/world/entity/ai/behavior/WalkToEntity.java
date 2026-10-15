package org.unitego.lobecorp.world.entity.ai.behavior;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/// 通用实体寻路行为。从指定记忆读取目标实体，持续走向并注视目标，
/// 进入 `closeEnoughDist` 范围后停止移动（但继续注视）。
///
/// 内部使用 [BehaviorUtils#setWalkAndLookTargetMemories] 设置
/// `WALK_TARGET` 和 `LOOK_TARGET` 记忆。
///
/// 用法示例：
/// ```java
/// WalkToEntity.create(MemoryModuleType.ATTACK_TARGET, 1.0f, 2);
/// WalkToEntity.createLiving(LcMemoryModuleTypes.NEAREST_CORPSE.get(), 1.0f, 2);
/// ```
public class WalkToEntity {

	/// 适用于任意 [Entity] 子类的通用版本（ItemEntity、LivingEntity 等）。
	/// 目标记忆存在且目标存活时持续走向目标；目标死亡或记忆被擦除时自动跳过。
	///
	/// @param targetMemory    存储目标的记忆模块
	/// @param speedModifier   移动速度倍率（1.0 为正常速度）
	/// @param closeEnoughDist 视为"到达"的距离（方块）
	public static <E extends PathfinderMob, T extends Entity> BehaviorControl<E> create(
			MemoryModuleType<T> targetMemory, float speedModifier, int closeEnoughDist) {
		return BehaviorBuilder.create(i -> i.group(
				i.present(targetMemory)
		).apply(i, (target) -> (_, body, _) -> {
			T entity = i.get(target);
			if (!entity.isAlive()) {
				return false;
			}

			BehaviorUtils.setWalkAndLookTargetMemories(body, entity, speedModifier, closeEnoughDist);
			return true;
		}));
	}

	/// [LivingEntity] 专用版本。与 [#create] 行为相同，额外逻辑：
	/// 目标死亡时自动擦除 `targetMemory`，避免僵尸记忆导致 AI 持续尝试走向已死实体。
	///
	/// @param targetMemory    存储活体目标的记忆模块
	/// @param speedModifier   移动速度倍率
	/// @param closeEnoughDist 视为"到达"的距离（方块）
	public static <E extends PathfinderMob, T extends LivingEntity> BehaviorControl<E> createLiving(
			MemoryModuleType<T> targetMemory, float speedModifier, int closeEnoughDist) {
		return BehaviorBuilder.create(i -> i.group(
				i.present(targetMemory)
		).apply(i, (target) -> (_, body, _) -> {
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
