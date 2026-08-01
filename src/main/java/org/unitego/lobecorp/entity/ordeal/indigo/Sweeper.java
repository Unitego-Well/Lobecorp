package org.unitego.lobecorp.entity.ordeal.indigo;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
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
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.IEntityTarget;
import org.unitego.lobecorp.entity.ai.behavior.AttackEntity;
import org.unitego.lobecorp.entity.ai.behavior.WalkToEntity;
import org.unitego.lobecorp.entity.ai.util.BrainUtil;
import org.unitego.lobecorp.init.brain.LcMemoryModuleTypes;
import org.unitego.lobecorp.init.LcParticleTypes;
import org.unitego.lobecorp.entity.util.EntityUtil;
import org.unitego.lobecorp.init.brain.LcSensorTypes;

import java.util.List;

/// 清道夫
public class Sweeper extends PathfinderMob implements Enemy, GeoEntity, IIndigoOrdeal, IEntityTarget {
    public static final Identifier ATTACK_MULTIPLIER = Lobecorp.id("attack_multiplier");
    private static final int MELEE_COOLDOWN = 10;
    private static final Brain.Provider<Sweeper> BRAIN_PROVIDER = BrainUtil.provider(Sweeper::getActivities)
            .addSensorTypes(SensorType.NEAREST_LIVING_ENTITIES,
                    SensorType.NEAREST_ITEMS,
                    LcSensorTypes.ORDEAL_ATTACKABLES.get(),
                    LcSensorTypes.NEAREST_CORPSE.get(),
                    LcSensorTypes.NEARBY_CORPSES.get(),
                    SensorType.HURT_BY).build();
    private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private Entity entityTarget;

    public Sweeper(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.LAVA, -1);
        setPathfindingMalus(PathType.FIRE, -1);
        setPathfindingMalus(PathType.WATER, 10.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 5.0F);
    }

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

    protected List<ActivityData<Sweeper>> getActivities() {
        return List.of(
                ActivityData.create(Activity.CORE, 0, ImmutableList.of(
                        new LookAtTargetSink(45, 90), new MoveToTargetSink())
                ),
                ActivityData.create(Activity.IDLE, 5, ImmutableList.of(
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
                )),
                ActivityData.create(Activity.FIGHT,
                        ImmutableList.of(
                                Pair.of(5, WalkToEntity.create(MemoryModuleType.ATTACK_TARGET, 3f, 1)),
                                Pair.of(5, StopAttackingIfTargetInvalid.create()),
                                Pair.of(5, AttackEntity.create(MemoryModuleType.ATTACK_TARGET,
                                        MELEE_COOLDOWN,
                                        Mob::isWithinMeleeAttackRange,
                                        (level, mob, target) -> mob.doHurtTarget(level, target, 1f),
                                        (level, mob, target, hit) ->
                                                EntityUtil.getHitPosOnAABB(mob, target).ifPresent(hitPos ->
                                                        level.sendParticles(LcParticleTypes.SIMPLE_LONG_SLASH.get(), hitPos.x, hitPos.y, hitPos.z, 1, 0, 0, 0, 0)
                                                )))
                        ),
                        Sets.newHashSet(
                                Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT)),
                        Sets.newHashSet(
                                MemoryModuleType.ATTACK_TARGET)
                )
        );
    }

    // ===================== IDLE =====================

    /// 近战攻击尸体（移动由 WalkToEntity 处理）。
    private OneShot<Sweeper> disposeCorpse() {
        return BehaviorBuilder.create(i -> i.group(
                i.present(LcMemoryModuleTypes.NEAREST_CORPSE.get()),
                i.absent(MemoryModuleType.ATTACK_COOLING_DOWN)
        ).apply(i, (nearestCorpse, cool) -> (level, body, time) -> {
            LivingEntity corpse = i.get(nearestCorpse);
            // TODO 引入前摇计时 需要在 LcMemoryModuleTypes 注册
            if (!body.isWithinMeleeAttackRange(corpse)) {
                return false;
            }
            corpse.setHealth(corpse.getHealth() - 2);
            body.heal(2);
            // TODO 播放粒子和特殊动画
            return true;
        }));
    }

    /// 靠近后清理物品（移动由 WalkToEntity 处理）。
    private OneShot<Sweeper> disposeItem() {
        return BehaviorBuilder.create(i -> i.group(
                i.present(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)
        ).apply(i, (nearestItem) -> (level, body, time) -> {
            ItemEntity itemEntity = i.get(nearestItem);
            if (body.distanceToSqr(itemEntity) > 4.0) return false;
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

    // ===================== GeoEntity =====================

    @Override
    public void registerControllers(AnimatableManager.@NonNull ControllerRegistrar controllers) {
        AnimationController<GeoAnimatable> controller = new AnimationController<>((test) -> {
            if (test.isMoving()) {
                test.controller().triggerAnimation("idle");
            }
            return PlayState.CONTINUE;
        });
        controller.triggerableAnim("idle", RawAnimation.begin().thenLoop("idle"));
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
