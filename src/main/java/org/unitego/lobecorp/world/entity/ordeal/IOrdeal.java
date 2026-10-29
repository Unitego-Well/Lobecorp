package org.unitego.lobecorp.world.entity.ordeal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/// 异常实体的共同标记契约，提供目标与营地归属判定。
public interface IOrdeal {
	/// 按世界上下文判断是否可作为攻击目标；默认忽略世界信息。
	default boolean canTarget(Entity entity, Level level) {
		return canTarget(entity);
	}

	/// 判断是否可作为攻击目标；默认复用 [#isValidTarget(Entity)]。
	default boolean canTarget(Entity entity) {
		return isValidTarget(entity);
	}

	/// 排除自身与无效攻击目标；玩家检查创造/旁观模式，其他实体检查同阵营。
	default boolean isValidTarget(Entity entity) {
		if (entity == this) {
			return false;
		}

		if (!entity.isAlive() || !entity.isAttackable()) {
			return false;
		}

		if (entity instanceof LivingEntity livingEntity) {
			if (!livingEntity.attackable()) {
				return false;
			}
		}

		if (entity instanceof Player player) {
			return !player.isCreative() && !player.isSpectator();
		}

		return !isCamp(entity);
	}

	/// 默认按实体类型是否相同判定同阵营；子类型可扩展。
	default boolean isCamp(Entity entity) {
		return getMob().getType() == entity.getType();
	}

	default GoalSelector getTargetSelector() {
		return getMob().targetSelector;
	}

	default Mob getMob() {
		return (Mob) this;
	}

	default GoalSelector getGoalSelector() {
		return getMob().goalSelector;
	}
}
