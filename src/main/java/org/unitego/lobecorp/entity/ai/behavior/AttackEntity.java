package org.unitego.lobecorp.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.function.BiPredicate;

/// 通用自定义攻击行为。覆盖从读取目标、范围检查、挥行动画、攻击动作到冷却的完整流程。
/// <p>
/// 使用以下记忆模块：
/// <ul>
///   <li>{@code targetMemory} — 攻击目标（必选，运行时必须 present）</li>
///   <li>{@code ATTACK_COOLING_DOWN} — 攻击冷却标记（必须 absent 才能攻击，攻击后设置 expiry）</li>
///   <li>{@code LOOK_TARGET} — 注册但不强制，攻击时自动注视目标</li>
///   <li>{@code NEAREST_VISIBLE_LIVING_ENTITIES} — 攻击前确保目标在视线内</li>
/// </ul>
/// <p>
/// 提供了三个静态工厂方法，覆盖从最简到完全自定义的场景：
/// <pre>{@code
/// // 最简：标准近战
/// AttackEntity.simpleMelee(10);
///
/// // 标准近战 + 命中回调
/// AttackEntity.simpleMelee(10, (level, mob, target, hit) -> { ... });
///
/// // 完全自定义：范围、动作、回调
/// AttackEntity.create(targetMem, cooldown, rangeCheck, action, outcome);
/// }</pre>
public class AttackEntity {

    /// 攻击动作接口。在挥拳动画后调用。
    ///
    /// @param <E> 攻击者类型
    @FunctionalInterface
    public interface AttackAction<E extends Mob> {
        /// 执行攻击动作（如伤害计算、投掷物生成等）。
        ///
        /// @param level   服务端世界
        /// @param attacker 攻击实体
        /// @param target   攻击目标
        /// @return true 表示本次攻击成功命中；false 表示未命中
        boolean attack(ServerLevel level, E attacker, LivingEntity target);
    }

    /// 攻击结果回调接口。无论是否命中都会调用，可用于粒子效果、音效、属性调整等。
    ///
    /// @param <E> 攻击者类型
    @FunctionalInterface
    public interface AttackOutcome<E extends Mob> {
        /// 攻击完成后的回调。
        ///
        /// @param level   服务端世界
        /// @param attacker 攻击实体
        /// @param target   攻击目标
        /// @param hit      {@link AttackAction#attack} 的返回值，true 表示这次攻击有效命中
        void onOutcome(ServerLevel level, E attacker, LivingEntity target, boolean hit);
    }

    /// 完全自定义版。适用于非标准攻击（远程、多段攻击、特殊技能等）。
    /// <p>
    /// 执行流程：
    /// <ol>
    ///   <li>从 targetMemory 读取目标</li>
    ///   <li>通过 rangeCheck 验证距离</li>
    ///   <li>通过 NEAREST_VISIBLE_LIVING_ENTITIES 验证可见性</li>
    ///   <li>播放挥拳动画（main hand swing）</li>
    ///   <li>执行 action.attack()</li>
    ///   <li>设置冷却记忆 ATTACK_COOLING_DOWN</li>
    ///   <li>调用 outcome.onOutcome() 回调</li>
    /// </ol>
    ///
    /// @param targetMemory    存储攻击目标的记忆模块
    /// @param cooldownDuration 攻击冷却的 tick 数
    /// @param rangeCheck      距离/范围判定（true 表示在攻击范围内）
    /// @param action          攻击动作逻辑
    /// @param outcome         攻击结果回调（粒子、音效等）
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
    /// <p>
    /// 固定使用 {@code ATTACK_TARGET} 作为目标记忆，{@link Mob#isWithinMeleeAttackRange} 作为范围判定，
    /// 攻击动作调用 {@link Mob#doHurtTarget(ServerLevel, Entity)}。
    /// 适用于需要额外命中效果（粒子、吸血等）的标准近战攻击。
    ///
    /// @param cooldownTicks 攻击冷却的 tick 数
    /// @param onHit         命中回调（仅命中时调用，未命中时 {@code hit} 为 false）
    public static <E extends Mob> BehaviorControl<E> simpleMelee(int cooldownTicks, AttackOutcome<E> onHit) {
        return create(
                MemoryModuleType.ATTACK_TARGET,
                cooldownTicks,
                Mob::isWithinMeleeAttackRange,
                (level, mob, target) -> mob.doHurtTarget(level, target),
                onHit
        );
    }

    /// 最简版：标准近战攻击，无额外效果。
    /// <p>
    /// 适用于没有特殊需求的简单近战 AI。
    ///
    /// @param cooldownTicks 攻击冷却的 tick 数
    public static <E extends Mob> BehaviorControl<E> simpleMelee(int cooldownTicks) {
        return simpleMelee(cooldownTicks, (level, mob, target, hit) -> {
        });
    }
}
