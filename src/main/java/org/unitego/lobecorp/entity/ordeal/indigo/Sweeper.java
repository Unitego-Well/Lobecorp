package org.unitego.lobecorp.entity.ordeal.indigo;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.constant.DefaultAnimations;
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
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
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
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.entity.EntityCorpse;
import org.unitego.lobecorp.entity.IEntityTarget;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillBrain;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillController;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillHolder;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity.entity_state.EntityState;
import org.unitego.lobecorp.entity.entity_state.EntityStateHolder;
import org.unitego.lobecorp.entity.ai.util.BrainUtil;
import org.unitego.lobecorp.registry.brain.LcMemoryModuleTypes;
import org.unitego.lobecorp.registry.brain.LcSensorTypes;
import org.unitego.lobecorp.registry.entity.LcEntityDataSerializers;
import org.unitego.lobecorp.registry.entity.OrdealEntityTypes;
import org.unitego.lobecorp.registry.entity_skill.SweeperSkills;

import java.util.Collection;
import java.util.List;

/// 清道夫
public class Sweeper extends PathfinderMob implements Enemy, GeoEntity, IIndigoOrdeal, IEntityTarget, EntitySkillHolder, EntityStateHolder {
	/// 普通攻击临时伤害倍率属性的唯一标识
	public static final Identifier ATTACK_MULTIPLIER = Lobecorp.id("attack_multiplier");
	/// GeckoLib 动作动画控制器名称
	public static final String ACTION_ANIMATION_CONTROLLER = "Actions";
	/// 同步清道夫外观变种的数据字段
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(Sweeper.class, EntityDataSerializers.INT);
	/// 同步清道夫当前生物质的数据字段
	private static final EntityDataAccessor<Float> DATA_BIOMASS = SynchedEntityData.defineId(Sweeper.class, EntityDataSerializers.FLOAT);
	/// 同步清道夫当前实体状态的数据字段
	private static final EntityDataAccessor<List<EntityState>> DATA_ENTITY_STATES =
			SynchedEntityData.defineId(Sweeper.class, LcEntityDataSerializers.ENTITY_STATES.get());
	/// 清道夫追踪目标时的行走速度
	private static final float TARGET_WALK_SPEED = 3.0F;
	/// 寻路目标允许停止移动的距离
	private static final int TARGET_CLOSE_ENOUGH_DISTANCE = 1;
	/// 清理掉落物时允许的最大距离平方
	private static final double ITEM_DISPOSE_DISTANCE_SQUARED = 4.0;
	/// 每个被清理物品为清道夫恢复的生命值
	private static final float ITEM_HEALTH_PER_COUNT = 2.0F;
	/// 尝试进入恢复清理状态的生命比例阈值
	private static final float RECOVERY_CLEANUP_HEALTH_THRESHOLD = 0.2F;
	/// 低生命时进入恢复清理状态的概率
	private static final float RECOVERY_CLEANUP_START_CHANCE = 0.6F;
	/// 退出恢复清理状态的生命比例阈值
	private static final float RECOVERY_CLEANUP_END_HEALTH_THRESHOLD = 0.7F;
	/// 恢复清理期间受击后转入战斗的概率
	private static final float RECOVERY_CLEANUP_INTERRUPTION_CHANCE = 0.3F;
	/// 生物质存档字段名称
	private static final String BIOMASS_SAVE_KEY = "Biomass";
	/// 生物质上限相对最大生命值的倍率
	private static final float BIOMASS_CAPACITY_MULTIPLIER = 2.0F;
	/// 治疗溢出转换为生物质的比例分母
	private static final float OVERHEAL_TO_BIOMASS_DIVISOR = 5.0F;
	/// 空闲时每 tick 可恢复的最大生命比例
	private static final float IDLE_HEAL_MAX_HEALTH_RATIO = 0.01F;
	/// 触发普通尸体生成时允许存在的清道夫数量上限
	private static final int SWEEPER_POPULATION_LIMIT = 5;
	/// 普通尸体生成检查的范围
	private static final double SWEEPER_POPULATION_CHECK_RANGE = 30.0;
	/// 普通尸体为新清道夫提供的最大生命比例
	private static final float CORPSE_HEALTH_CONTRIBUTION_RATIO = 0.5F;

	/// 清道夫使用的 Brain 配置提供器
	private static final Brain.Provider<Sweeper> BRAIN_PROVIDER = BrainUtil.provider(Sweeper::getActivities)
			.addSensorTypes(
					SensorType.NEAREST_LIVING_ENTITIES,
					LcSensorTypes.ORDEAL_ATTACKABLES.get(),
					LcSensorTypes.NEAREST_CLEANUP_TARGET.get(),
					SensorType.HURT_BY
			).addMemoryTypes(
					LcMemoryModuleTypes.ATTACK_COMBO.get(),
					LcMemoryModuleTypes.SKILL_ACTIVE.get(),
					LcMemoryModuleTypes.SKILL_COOLDOWNS.get()
			).build();

	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
	private boolean recoveryCleanup;
	private boolean recoveryCleanupDecisionMade;
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
		builder.define(DATA_BIOMASS, 0.0F);
		builder.define(DATA_ENTITY_STATES, List.of());
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
		setBiomass(input.read(BIOMASS_SAVE_KEY, Codec.FLOAT).orElse(0.0F));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("Variant", Codec.INT, getVariant().ordinal());
		output.store(BIOMASS_SAVE_KEY, Codec.FLOAT, getBiomass());
	}

	/// 当前生物质
	public float getBiomass() {
		return Math.min(getEntityData().get(DATA_BIOMASS), getBiomassCapacity());
	}

	public void setBiomass(float biomass) {
		getEntityData().set(DATA_BIOMASS, Mth.clamp(biomass, 0.0F, getBiomassCapacity()));
	}

	/// 当前生物质上限
	public float getBiomassCapacity() {
		return getMaxHealth() * BIOMASS_CAPACITY_MULTIPLIER;
	}

	/// 攻击连击计数
	public int getAttackCombo() {
		return getBrain().getMemory(LcMemoryModuleTypes.ATTACK_COMBO.get()).orElse(0);
	}

	public void setAttackCombo(int combo) {
		getBrain().setMemory(LcMemoryModuleTypes.ATTACK_COMBO.get(), combo);
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
		restoreHealthFromBiomass();
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
	public void heal(float amount) {
		if (amount <= 0.0F || isDeadOrDying()) {
			super.heal(amount);
			return;
		}
		float healthBeforeHealing = getHealth();
		super.heal(amount);
		float restoredHealth = getHealth() - healthBeforeHealing;
		float overflowHealing = Math.max(0.0F, amount - restoredHealth);
		if (overflowHealing > 0.0F) {
			setBiomass(getBiomass() + overflowHealing / OVERHEAL_TO_BIOMASS_DIVISOR);
		}
	}

	/// 使用生物质抵消已经完成常规减伤计算的伤害，并返回剩余伤害。
	public float absorbDamageWithBiomass(float damage) {
		float absorbedDamage = Math.min(damage, getBiomass());
		setBiomass(getBiomass() - absorbedDamage);
		return damage - absorbedDamage;
	}

	private void restoreHealthFromBiomass() {
		if (getHealth() >= getMaxHealth() || getBiomass() <= 0.0F
				|| getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()
				|| getBrain().getMemory(LcMemoryModuleTypes.SKILL_ACTIVE.get()).isPresent()) {
			return;
		}
		float restoredHealth = Math.min(Math.min(getMaxHealth() - getHealth(), getBiomass()),
				getMaxHealth() * IDLE_HEAL_MAX_HEALTH_RATIO);
		setHealth(getHealth() + restoredHealth);
		setBiomass(getBiomass() - restoredHealth);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (!hurt || !recoveryCleanup || getRandom().nextFloat() >= RECOVERY_CLEANUP_INTERRUPTION_CHANCE
				|| !(source.getEntity() instanceof LivingEntity attacker) || !isValidTarget(attacker)) {
			return hurt;
		}
		recoveryCleanup = false;
		recoveryCleanupDecisionMade = true;
		EntitySkillBrain.cancelSkill(this);
		getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
		getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		return true;
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
						walkToCleanupTarget(),
						disposeCleanupTarget(),
						new RunOne<>(ImmutableList.of(
								Pair.of(new DoNothing(20, 40), 1),
								Pair.of(RandomStroll.stroll(1f), 2)))
				)), ActivityData.create(Activity.FIGHT,
						ImmutableList.of(
								Pair.of(5, walkToAttackTarget()),
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

	/// 没有技能占用时走向战斗目标。
	private OneShot<Sweeper> walkToAttackTarget() {
		return BehaviorBuilder.create(i -> i.group(
				i.present(MemoryModuleType.ATTACK_TARGET),
				i.absent(LcMemoryModuleTypes.SKILL_ACTIVE.get())
		).apply(i, (target, skillActive) -> (level, body, time) -> {
			BehaviorUtils.setWalkAndLookTargetMemories(body, i.get(target), TARGET_WALK_SPEED, TARGET_CLOSE_ENOUGH_DISTANCE);
			return true;
		}));
	}

	/// 近战攻击：目标在射程内时通过技能系统施放（含前摇/后摇延迟）。
	private OneShot<Sweeper> performAttack() {
		return BehaviorBuilder.create(i -> i.group(
				i.present(MemoryModuleType.ATTACK_TARGET),
				i.absent(LcMemoryModuleTypes.SKILL_ACTIVE.get())
		).apply(i, (target, skillActive) -> (level, body, time) -> {
			LivingEntity t = i.get(target);
			if (!body.isWithinMeleeAttackRange(t)) {
				return false;
			}
			return EntitySkillBrain.cast(body, SweeperSkills.ATTACK.get());
		}));
	}

	/// 飞扑：目标不在近战范围时冲向目标。
	private OneShot<Sweeper> performLeap() {
		return BehaviorBuilder.create(i -> i.group(
				i.present(MemoryModuleType.ATTACK_TARGET),
				i.absent(LcMemoryModuleTypes.SKILL_ACTIVE.get())
		).apply(i, (target, skillActive) -> (level, body, time) -> {
			LivingEntity t = i.get(target);
			if (body.isWithinMeleeAttackRange(t)) {
				return false;
			}
			return EntitySkillBrain.cast(body, SweeperSkills.LEAP.get());
		}));
	}

	// ===================== IDLE =====================

	/// 没有战斗目标和技能占用时，走向最近的清理目标。
	private OneShot<Sweeper> walkToCleanupTarget() {
		return BehaviorBuilder.create(i -> i.group(
				i.present(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get()),
				i.absent(MemoryModuleType.ATTACK_TARGET),
				i.absent(LcMemoryModuleTypes.SKILL_ACTIVE.get())
		).apply(i, (nearestCleanupTarget, attackTarget, skillActive) -> (level, body, time) -> {
			Entity target = i.get(nearestCleanupTarget);
			if (!isValidCleanupTarget(target) || isWithinCleanupRange(body, target)) {
				return false;
			}
			BehaviorUtils.setWalkAndLookTargetMemories(body, target, TARGET_WALK_SPEED, TARGET_CLOSE_ENOUGH_DISTANCE);
			return true;
		}));
	}

	/// 靠近后按目标类型清理尸体或物品。
	private OneShot<Sweeper> disposeCleanupTarget() {
		return BehaviorBuilder.create(i -> i.group(
				i.present(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get()),
				i.absent(MemoryModuleType.ATTACK_TARGET),
				i.absent(LcMemoryModuleTypes.SKILL_ACTIVE.get())
		).apply(i, (nearestCleanupTarget, attackTarget, skillActive) -> (level, body, time) -> {
			Entity target = i.get(nearestCleanupTarget);
			if (!isValidCleanupTarget(target) || !isWithinCleanupRange(body, target)) {
				return false;
			}
			if (target instanceof EntityCorpse<?> corpse) {
				if (body.tryCreateSweeperFromCorpse(level, corpse)) {
					body.getBrain().eraseMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get());
					return true;
				}
				if (corpse.getOwnerEntity() instanceof Sweeper) {
					return false;
				}
				return EntitySkillBrain.cast(body, SweeperSkills.DISPOSE_CORPSE.get());
			}
			ItemEntity itemEntity = (ItemEntity) target;
			ItemStack stack = itemEntity.getItem();
			body.heal(stack.getCount() * ITEM_HEALTH_PER_COUNT);
			itemEntity.discard();
			return true;
		}));
	}

	private boolean tryCreateSweeperFromCorpse(ServerLevel level, EntityCorpse<?> corpse) {
		if (getBiomass() <= 0.0F) {
			return false;
		}
		if (corpse.getOwnerEntity() instanceof Sweeper corpseSweeper) {
			return reviveSweeper(level, corpse, corpseSweeper);
		}
		if (countNearbySweepers(level) >= SWEEPER_POPULATION_LIMIT) {
			return false;
		}
		Sweeper sweeper = OrdealEntityTypes.SWEEPER.get().create(level, EntitySpawnReason.MOB_SUMMONED);
		if (sweeper == null) {
			return false;
		}
		sweeper.absSnapTo(corpse.getX(), corpse.getY(), corpse.getZ(), corpse.getYRot(), corpse.getXRot());
		sweeper.finalizeSpawn(level, level.getCurrentDifficultyAt(corpse.blockPosition()),
				EntitySpawnReason.MOB_SUMMONED, null);
		float corpseHealthCost = sweeper.getMaxHealth() * CORPSE_HEALTH_CONTRIBUTION_RATIO;
		float corpseHealthContribution = Math.min(corpse.getHealth(), corpseHealthCost);
		float biomassContribution = Math.min(getBiomass(), sweeper.getMaxHealth() - corpseHealthContribution);
		sweeper.setHealth(corpseHealthContribution + biomassContribution);
		if (!level.addFreshEntity(sweeper)) {
			return false;
		}
		setBiomass(getBiomass() - biomassContribution);
		corpse.setHealth(corpse.getHealth() - corpseHealthCost);
		if (!corpse.isAlive()) {
			corpse.discard();
		}
		return true;
	}

	private boolean reviveSweeper(ServerLevel level, EntityCorpse<?> corpse, Sweeper corpseSweeper) {
		float corpseHealthContribution = Math.min(corpse.getHealth(), corpseSweeper.getMaxHealth());
		float biomassContribution = Math.min(getBiomass(), corpseSweeper.getMaxHealth() - corpseHealthContribution);
		corpseSweeper.absSnapTo(corpse.getX(), corpse.getY(), corpse.getZ(), corpse.getYRot(), corpse.getXRot());
		corpseSweeper.setHealth(corpseHealthContribution + biomassContribution);
		if (!level.addFreshEntity(corpseSweeper)) {
			return false;
		}
		setBiomass(getBiomass() - biomassContribution);
		corpse.discard();
		return true;
	}

	private int countNearbySweepers(ServerLevel level) {
		AABB searchBounds = getBoundingBox().inflate(SWEEPER_POPULATION_CHECK_RANGE);
		double maximumDistanceSquared = SWEEPER_POPULATION_CHECK_RANGE * SWEEPER_POPULATION_CHECK_RANGE;
		return level.getEntitiesOfClass(Sweeper.class, searchBounds,
				sweeper -> sweeper.isAlive() && distanceToSqr(sweeper) <= maximumDistanceSquared).size();
	}

	private static boolean isValidCleanupTarget(Entity target) {
		return target.isAlive() && (target instanceof EntityCorpse<?>
				|| target instanceof ItemEntity itemEntity && !itemEntity.getItem().isEmpty());
	}

	private static boolean isWithinCleanupRange(Sweeper body, Entity target) {
		return target instanceof LivingEntity livingEntity
				? body.isWithinMeleeAttackRange(livingEntity)
				: body.distanceToSqr(target) <= ITEM_DISPOSE_DISTANCE_SQUARED;
	}

	// ===================== Activity 切换 =====================

	public void updateActivity() {
		updateRecoveryCleanup();
		if (getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()
				&& EntitySkillBrain.isCasting(this, SweeperSkills.DISPOSE_CORPSE.get())) {
			EntitySkillBrain.cancelSkill(this);
		}
		getBrain().setActiveActivityToFirstValid(ImmutableList.of(
				Activity.FIGHT,
				Activity.IDLE
		));
	}

	private void updateRecoveryCleanup() {
		float healthRatio = getHealth() / getMaxHealth();
		if (healthRatio >= RECOVERY_CLEANUP_END_HEALTH_THRESHOLD) {
			recoveryCleanup = false;
			recoveryCleanupDecisionMade = false;
			return;
		}
		if (!recoveryCleanup && healthRatio >= RECOVERY_CLEANUP_HEALTH_THRESHOLD) {
			recoveryCleanupDecisionMade = false;
			return;
		}
		boolean hasCleanupTarget = getBrain().getMemory(LcMemoryModuleTypes.NEAREST_CLEANUP_TARGET.get())
				.filter(Sweeper::isValidCleanupTarget)
				.isPresent();
		if (!recoveryCleanup && !recoveryCleanupDecisionMade && hasCleanupTarget) {
			recoveryCleanupDecisionMade = true;
			recoveryCleanup = getRandom().nextFloat() < RECOVERY_CLEANUP_START_CHANCE;
		}
		if (!recoveryCleanup || !hasCleanupTarget) {
			return;
		}
		if (!EntitySkillBrain.isCasting(this, SweeperSkills.DISPOSE_CORPSE.get())) {
			EntitySkillBrain.cancelSkill(this);
		}
		getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
	}

	// ===================== SkillHolder =====================

	@Override
	public Collection<IEntitySkill<Sweeper>> skills() {
		return List.of(SweeperSkills.ATTACK.get(), SweeperSkills.LEAP.get(), SweeperSkills.DISPOSE_CORPSE.get());
	}

	// ===================== EntityStateHolder =====================

	@Override
	public List<EntityState> getEntityStates() {
		return getEntityData().get(DATA_ENTITY_STATES);
	}

	@Override
	public void setEntityStates(List<EntityState> states) {
		getEntityData().set(DATA_ENTITY_STATES, List.copyOf(states), true);
	}

	// ===================== GeoEntity =====================

	@Override
	public void registerControllers(AnimatableManager.@NonNull ControllerRegistrar controllers) {
		controllers.add(new AnimationController<Sweeper>("main", 3, state -> {
			Sweeper sweeper = state.animatable();
			if (sweeper.isRemoved()) {
				return PlayState.STOP;
			}
			// 基础动画（待机/移动/奔跑）由客户端按移动状态决定
			float speed = sweeper.walkAnimation.speed();
			if (speed > 0.5f) {
				return state.setAndContinue(SweeperAnim.RUN.getAnimation());
			}
			if (speed > 0.1f) {
				return state.setAndContinue(SweeperAnim.MOVE.getAnimation());
			}
			return state.setAndContinue(SweeperAnim.IDLE.getAnimation());
		}));
		controllers.add(DefaultAnimations.triggerOnlyController()
				.triggerableAnim(SweeperAnim.ATTACK1.getId(), SweeperAnim.ATTACK1.getAnimation())
				.triggerableAnim(SweeperAnim.ATTACK2.getId(), SweeperAnim.ATTACK2.getAnimation())
				.triggerableAnim(SweeperAnim.ATTACK3.getId(), SweeperAnim.ATTACK3.getAnimation())
				.triggerableAnim(SweeperAnim.LEAP.getId(), SweeperAnim.LEAP.getAnimation())
				.triggerableAnim(SweeperAnim.LEAP2.getId(), SweeperAnim.LEAP2.getAnimation())
				.triggerableAnim(SweeperAnim.CLEAR1.getId(), SweeperAnim.CLEAR1.getAnimation())
				.triggerableAnim(SweeperAnim.CLEAR2.getId(), SweeperAnim.CLEAR2.getAnimation())
				.triggerableAnim(SweeperAnim.CLEAR3.getId(), SweeperAnim.CLEAR3.getAnimation())
		);
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
