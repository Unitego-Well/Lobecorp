package org.unitego.lobecorp.entity.ordeal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public interface IOrdeal {
    /// 目标选择
    default boolean canTarget(Entity entity, Level level) {
        return canTarget(entity);
    }

    /// 目标选择
    default boolean canTarget(Entity entity) {
        return isValidTarget(entity);
    }

    /// 判断是否是可以攻击目标
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

    /// 判断是否是同阵营
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
