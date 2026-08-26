package org.unitego.lobecorp.entity.ordeal.indigo;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.IEntityTarget;
import org.unitego.lobecorp.entity.ai.behavior.WalkToEntity;
import org.unitego.lobecorp.entity.ai.skill.IEntitySkill;
import org.unitego.lobecorp.entity.ai.skill.EntitySkillBrain;
import org.unitego.lobecorp.entity.ai.skill.EntitySkillController;
import org.unitego.lobecorp.entity.ai.skill.EntitySkillHolder;
import org.unitego.lobecorp.entity.ai.util.BrainUtil;
import org.unitego.lobecorp.registry.brain.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.brain.LcSensorTypes;
import org.unitego.lobecorp.registry.entity.skill.SweeperSkills;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/// 清道夫
public class Sweeper extends PathfinderMob implements Enemy, GeoEntity, IIndigoOrdeal, IEntityTarget, EntitySkillHolder {
    public static final Identifier ATTACK_MULTIPLIER = Lobecorp.id("attack_multiplier");
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(Sweeper.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ANIM = SynchedEntityData.defineId(Sweeper.class, EntityDataSerializers.INT);

    // 清理动画阶段 tick（1s = 20 tick）
    private static final int CLEAR_START_TICKS = 17;   // clear 0.83s
    private static final int CLEAR_END_TICKS = 10;     // clear3 0.5s
    private static final int CLEAR_PROCESS_HEAL = 2;   // 每次处理削减尸体血量
    /// 清理最大时长（tick），超过则强制结束（保底）
    private static final int CLEAR_MAX_TICKS = 300;

    // 动画
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_MOVE = RawAnimation.begin().thenLoop("move");
    private static final RawAnimation ANIM_RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ANIM_ATTACK1 = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation ANIM_ATTACK2 = RawAnimation.begin().thenPlay("attack2");
    private static final RawAnimation ANIM_ATTACK3 = RawAnimation.begin().thenPlay("attack3");
    private static final RawAnimation ANIM_LEAP = RawAnimation.begin().thenPlay("leap");
    private static final RawAnimation ANIM_LEAP2 = RawAnimation.begin().thenPlay("leap2");
    private static final RawAnimation ANIM_CLEAR1 = RawAnimation.begin().thenPlay("clear");
    private static final RawAnimation ANIM_CLEAR2 = RawAnimation.begin().thenLoop("clear2");
    private static final RawAnimation ANIM_CLEAR3 = RawAnimation.begin().thenPlay("clear3");

    private static final Brain.Provider<Sweeper> BRAIN_PROVIDER = BrainUtil.provider(Sweeper::getActivities)
            .addSensorTypes(
                    SensorType.NEAREST_LIVING_ENTITIES,
                    SensorType.NEAREST_ITEMS,
                    LcSensorTypes.ORDEAL_ATTACKABLES.get(),
                    LcSensorTypes.NEAREST_CORPSE.get(),
                    LcSensorTypes.NEARBY_CORPSES.get(),
                    SensorType.HURT_BY
            ).addMemoryTypes(
                    LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get(),
                    EntitySkillBrain.SKILL_ACTIVE,
                    EntitySkillBrain.SKILL_COOLDOWNS
            ).build();

    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
    /// 攻击技能（3 段连击）
    private final IEntitySkill attackSkill = SweeperSkills.ATTACK.get();
    /// 飞扑技能
    private final IEntitySkill leapSkill = SweeperSkills.LEAP.get();
    /// 攻击连击计数（0/1/2，服务端）
    private int attackCombo;
    /// 清理进行中的 tick 计数（服务端，用于超时保底）
    private int clearTickCount;
    @Nullable
    private Entity entityTarget;

    public Sweeper(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.LAVA, -1);
        setPathfindingMalus(PathType.FIRE, -1);
        setPathfindingMalus(PathType.WATER, 10.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 5.0F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_ANIM, 0);
    }

    // ===================== 变种 =====================

    /// 当前变种
    public SweeperVariant getVariant() {
        return SweeperVariant.values()[Math.floorMod(getEntityData().get(DATA_VARIANT), SweeperVariant.values().length)];
    }

    public void setVariant(SweeperVariant variant) {
        getEntityData().set(DATA_VARIANT, variant.ordinal());
    }

    /// 生成时随机分配变种
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData) {
        setVariant(SweeperVariant.values()[level.getRandom().nextInt(SweeperVariant.values().length)]);
        return super.finalizeSpawn(level, difficulty, spawnReason, groupData);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setVariant(SweeperVariant.values()[Math.floorMod(input.read("Variant", Codec.INT).orElse(0), SweeperVariant.values().length)]);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.store("Variant", Codec.INT, getVariant().ordinal());
    }

    // ===================== 动画状态 =====================

    /// 当前应播放的动画（服务端写入，客户端渲染时读取）
    public SweeperAnim getAnim() {
        return SweeperAnim.values()[Math.floorMod(getEntityData().get(DATA_ANIM), SweeperAnim.values().length)];
    }

    public void setAnim(SweeperAnim anim) {
        getEntityData().set(DATA_ANIM, anim.ordinal());
    }

    /// 重置动画状态为基础态（客户端按移动速度播放 idle/move/run）
    public void resetAnim() {
        setAnim(SweeperAnim.IDLE);
    }

    /// 攻击连击计数
    public int getAttackCombo() {
        return attackCombo;
    }

    public void setAttackCombo(int combo) {
        this.attackCombo = combo;
    }

    /// 每 tick 更新基础动画（待机/移动/奔跑）。技能或清理进行时不覆盖动作动画。
    private void updateBaseAnim() {
        Brain<Sweeper> brain = getBrain();
        if (brain.getMemory(EntitySkillBrain.SKILL_ACTIVE).isPresent()) {
            return;
        }
        if (brain.getMemory(LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get()).isPresent()) {
            return;
        }

        // 无动作时标记为基础状态，由客户端按移动速度播放 idle/move/run
        setAnim(SweeperAnim.IDLE);
    }

    /// 保底：防止技能/清理状态卡死，确保动画与状态最终能恢复。
    private void safeguardState() {
        Brain<Sweeper> brain = getBrain();
        boolean inSkill = brain.getMemory(EntitySkillBrain.SKILL_ACTIVE).isPresent();
        boolean inClear = brain.getMemory(LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get()).isPresent();

        // 保底 1：动作动画仍在播放，但技能/清理都已结束 → 强制恢复基础动画
        if (isActionAnim(getAnim()) && !inSkill && !inClear) {
            setAnim(SweeperAnim.IDLE);
        }

        // 保底 2：清理进行时间超过上限 → 强制结束清理
        if (inClear) {
            if (++clearTickCount > CLEAR_MAX_TICKS) {
                brain.eraseMemory(LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get());
                clearTickCount = 0;
            }
        } else {
            clearTickCount = 0;
        }
    }

    private static boolean isActionAnim(SweeperAnim anim) {
        return switch (anim) {
            case ATTACK1, ATTACK2, ATTACK3, LEAP, LEAP2, CLEAR1, CLEAR2, CLEAR3 -> true;
            default -> false;
        };
    }

    // ===================== 属性与 AI 步进 =====================

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200)
                .add(Attributes.ATTACK_DAMAGE, 5)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("sweeperBrain");
        getBrain().tick(level, this);
        profiler.pop();
        profiler.push("sweeperActivityUpdate");
        updateActivity();
        profiler.pop();
        safeguardState();
        updateBaseAnim();
        super.customServerAiStep(level);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity livingEntityTarget)) {
            return super.doHurtTarget(level, target);
        }

        float health = livingEntityTarget.getHealth();
        if (!super.doHurtTarget(level, livingEntityTarget)) {
            return false;
        }

        float inflictedDamage = health - livingEntityTarget.getHealth();
        if (inflictedDamage > 0) {
            heal(inflictedDamage);
        }

        return true;
    }

    public boolean doHurtTarget(ServerLevel level, LivingEntity target, AttributeModifier modifier) {
        AttributeInstance attribute = getAttribute(Attributes.ATTACK_DAMAGE);
        if (attribute != null) {
            attribute.addOrUpdateTransientModifier(modifier);
        }
        boolean result = doHurtTarget(level, target);
        if (attribute != null) {
            attribute.removeModifier(ATTACK_MULTIPLIER);
        }
        return result;
    }

    public boolean doHurtTarget(ServerLevel level, LivingEntity target, float multiplier) {
        return doHurtTarget(level, target, new AttributeModifier(ATTACK_MULTIPLIER, multiplier, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    // ===================== 大脑行为 =====================

    protected List<ActivityData<Sweeper>> getActivities() {
        return List.of(
                ActivityData.create(Activity.CORE, 0, ImmutableList.of(
                        new EntitySkillController(), new LookAtTargetSink(45, 90), new MoveToTargetSink())
                ), ActivityData.create(Activity.IDLE, 5, ImmutableList.of(
                        StartAttacking.create((level, mob) -> mob.getBrain()
                                .getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                                .orElse(NearestVisibleLivingEntities.empty())
                                .findClosest(mob::isValidTarget)),
                        WalkToEntity.create(LcMemoryModuleTypes.NEAREST_CORPSE.get(), 3f, 1),
                        WalkToEntity.create(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, 3f, 1),
                        disposeCorpse(),
                        disposeItem(),
                        new RunOne<>(ImmutableList.of(
                                Pair.of(new DoNothing(20, 40), 1),
                                Pair.of(RandomStroll.stroll(1f), 2)))
                )), ActivityData.create(Activity.FIGHT,
                        ImmutableList.of(
                                Pair.of(5, WalkToEntity.create(MemoryModuleType.ATTACK_TARGET, 3f, 1)),
                                Pair.of(5, StopAttackingIfTargetInvalid.create()),
                                Pair.of(5, performAttack()),
                                Pair.of(3, performLeap())
                        ),
                        Sets.newHashSet(
                                Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)),
                        Sets.newHashSet(
                                MemoryModuleType.ATTACK_TARGET)
                )
        );
    }

    /// 近战攻击：目标在射程内时通过技能系统施放（含前摇/后摇延迟）。
    private OneShot<Sweeper> performAttack() {
        return BehaviorBuilder.create(i -> i.group(
                i.present(MemoryModuleType.ATTACK_TARGET)
        ).apply(i, (target) -> (level, body, time) -> {
            LivingEntity t = i.get(target);
            if (!body.isWithinMeleeAttackRange(t)) {
                return false;
            }
            return EntitySkillBrain.cast(body, attackSkill);
        }));
    }

    /// 飞扑：目标不在近战范围时冲向目标。
    private OneShot<Sweeper> performLeap() {
        return BehaviorBuilder.create(i -> i.group(
                i.present(MemoryModuleType.ATTACK_TARGET)
        ).apply(i, (target) -> (level, body, time) -> {
            LivingEntity t = i.get(target);
            if (body.isWithinMeleeAttackRange(t)) {
                return false;
            }
            return EntitySkillBrain.cast(body, leapSkill);
        }));
    }

    // ===================== IDLE =====================

    /// 清理尸体：clear(开始) → clear2(处理循环) → 有目标跳过 clear3 回开始 / 无目标 clear3(结束)
    private OneShot<Sweeper> disposeCorpse() {
        return BehaviorBuilder.create(i -> i.group(
                // registered：清理过程（含 clear3 收尾）由 DISPOSE_CORPSE_WIND_UP_TICKS 驱动，
                // 尸体死后 sensor 会清空 NEAREST_CORPSE，不能依赖它持续 present
                i.registered(LcMemoryModuleTypes.NEAREST_CORPSE.get())
        ).apply(i, (nearestCorpse) -> (level, body, time) -> {
            Brain<Sweeper> brain = body.getBrain();
            Optional<Integer> optStage = brain.getMemory(LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get());

            if (optStage.isEmpty()) {
                // 不在清理中：需要一具活尸体且在近战范围内才启动
                LivingEntity corpse = brain.getMemory(LcMemoryModuleTypes.NEAREST_CORPSE.get()).orElse(null);
                if (corpse == null || !corpse.isAlive() || !body.isWithinMeleeAttackRange(corpse)) {
                    return false;
                }
                body.setAnim(SweeperAnim.CLEAR1);
                brain.setMemory(LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get(), CLEAR_START_TICKS);
                return true;
            }

            int stage = optStage.get();
            if (stage > 0) {
                // 开始阶段：clear 动画倒计时
                brain.setMemory(LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get(), stage - 1);
                return true;
            }

            if (stage < 0) {
                // 结束阶段：clear3 动画倒计时（不依赖 NEAREST_CORPSE）
                if (stage == -CLEAR_END_TICKS) {
                    body.setAnim(SweeperAnim.CLEAR3);
                }
                if (stage + 1 == 0) {
                    brain.eraseMemory(LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get());
                } else {
                    brain.setMemory(LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get(), stage + 1);
                }
                return true;
            }

            // 处理阶段
            LivingEntity corpse = brain.getMemory(LcMemoryModuleTypes.NEAREST_CORPSE.get()).orElse(null);
            if (corpse != null && corpse.isAlive()) {
                body.setAnim(SweeperAnim.CLEAR2);
                corpse.setHealth(corpse.getHealth() - CLEAR_PROCESS_HEAL);
                body.heal(CLEAR_PROCESS_HEAL);
                return true;
            }

            // 尸体已死：移除尸体，决定进入结束动画还是直接开始下一次
            if (corpse != null) {
                corpse.discard();
            }
            if (hasMoreCorpse(brain)) {
                brain.eraseMemory(LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get());
            } else {
                body.setAnim(SweeperAnim.CLEAR3);
                brain.setMemory(LcMemoryModuleTypes.DISPOSE_CORPSE_WIND_UP_TICKS.get(), -CLEAR_END_TICKS);
            }
            return true;
        }));
    }

    private boolean hasMoreCorpse(Brain<Sweeper> brain) {
        return brain.getMemory(LcMemoryModuleTypes.NEAREST_CORPSES.get())
                .orElse(List.of())
                .stream()
                .anyMatch(Entity::isAlive);
    }

    /// 靠近后清理物品（移动由 WalkToEntity 处理）。
    private OneShot<Sweeper> disposeItem() {
        return BehaviorBuilder.create(i -> i.group(
                i.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
        ).apply(i, (nearestItem) -> (level, body, time) -> {
            ItemEntity itemEntity = i.get(nearestItem);
            if (body.distanceToSqr(itemEntity) > 4.0) {
	            return false;
            }
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                body.heal(stack.getCount() * 2);
                itemEntity.discard();
            }
            return true;
        }));
    }

    // ===================== Activity 切换 =====================

    public void updateActivity() {
        getBrain().setActiveActivityToFirstValid(ImmutableList.of(
                Activity.FIGHT,
                Activity.IDLE
        ));
    }

    // ===================== SkillHolder =====================

    @Override
    public Collection<IEntitySkill> skills() {
        return List.of(attackSkill, leapSkill);
    }

    // ===================== GeoEntity =====================

    @Override
    public void registerControllers(AnimatableManager.@NonNull ControllerRegistrar controllers) {
        AnimationController<Sweeper> controller = new AnimationController<>("main", 3, state -> {
            Sweeper sweeper = state.animatable();
            return switch (sweeper.getAnim()) {
                case ATTACK1 -> state.setAndContinue(ANIM_ATTACK1);
                case ATTACK2 -> state.setAndContinue(ANIM_ATTACK2);
                case ATTACK3 -> state.setAndContinue(ANIM_ATTACK3);
                case LEAP -> state.setAndContinue(ANIM_LEAP);
                case LEAP2 -> state.setAndContinue(ANIM_LEAP2);
                case CLEAR1 -> state.setAndContinue(ANIM_CLEAR1);
                case CLEAR2 -> state.setAndContinue(ANIM_CLEAR2);
                case CLEAR3 -> state.setAndContinue(ANIM_CLEAR3);
                default -> {
                    // 基础动画（待机/移动/奔跑）由客户端按移动状态决定
                    float speed = sweeper.walkAnimation.speed();
                    if (speed > 0.5f) {
                        yield state.setAndContinue(ANIM_RUN);
                    } else if (speed > 0.1f) {
                        yield state.setAndContinue(ANIM_MOVE);
                    } else {
                        yield state.setAndContinue(ANIM_IDLE);
                    }
                }
            };
        });
        controllers.add(controller);
    }

    @Override
    public @NonNull AnimatableInstanceCache getAnimatableInstanceCache() {
        return animatableInstanceCache;
    }

    @Override
    @Nullable
    public Entity getEntityTarget() {
        return entityTarget;
    }

    @Override
    public void setEntityTarget(@Nullable Entity entity) {
        entityTarget = entity;
    }

    @Override
    protected Brain<Sweeper> makeBrain(Brain.Packed packedBrain) {
        return BRAIN_PROVIDER.makeBrain(this, packedBrain);
    }

    @Override
    public Brain<Sweeper> getBrain() {
        return (Brain<Sweeper>) super.getBrain();
    }
}
