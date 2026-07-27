package org.unitego.lobecorp.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.function.BiPredicate;

/// 可自定义范围、行为和效果的通用攻击。
public class AttackEntity {

    @FunctionalInterface
    public interface AttackAction<E extends Mob> {
        /** 执行攻击动作。返回 true 表示命中。 */
        boolean attack(ServerLevel level, E attacker, LivingEntity target);
    }

    @FunctionalInterface
    public interface AttackOutcome<E extends Mob> {
        /** 攻击尝试后回调，无论是否命中。 */
        void onOutcome(ServerLevel level, E attacker, LivingEntity target, boolean hit);
    }

    /// 完整自定义版。
    /// 从 targetMemory 读取目标，使用 ATTACK_COOLING_DOWN 作为冷却。
    public static <E extends Mob, T extends LivingEntity> BehaviorControl<E> create(
            MemoryModuleType<T> targetMemory,
            int cooldownDuration,
            BiPredicate<E, T> rangeCheck,
            AttackAction<E> action,
            AttackOutcome<E> outcome) {
        return BehaviorBuilder.create(i -> i.group(
                i.registered(MemoryModuleType.LOOK_TARGET),
                i.present(targetMemory),
                i.absent(MemoryModuleType.ATTACK_COOLING_DOWN),
                i.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
        ).apply(i, (lookTarget, attackTarget, coolDown, nearest) -> (level, body, time) -> {
            T target = i.get(attackTarget);
            if (!rangeCheck.test(body, target)) {
                return false;
            }
            if (!i.get(nearest).contains(target)) {
                return false;
            }
            body.swing(InteractionHand.MAIN_HAND);
            boolean hit = action.attack(level, body, target);
            body.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, cooldownDuration);
            outcome.onOutcome(level, body, target, hit);
            return true;
        }));
    }

    /// 简化版：标准近战攻击 + 命中回调。
    public static <E extends Mob> BehaviorControl<E> simpleMelee(int cooldownTicks, AttackOutcome<E> onHit) {
        return create(
                MemoryModuleType.ATTACK_TARGET,
                cooldownTicks,
                Mob::isWithinMeleeAttackRange,
                (level, mob, target) -> mob.doHurtTarget(level, target),
                onHit
        );
    }

    /// 最简版：仅近战攻击，无额外效果。
    public static <E extends Mob> BehaviorControl<E> simpleMelee(int cooldownTicks) {
        return simpleMelee(cooldownTicks, (level, mob, target, hit) -> {
        });
    }
}
