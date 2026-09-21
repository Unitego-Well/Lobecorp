package org.unitego.lobecorp.entity.ordeal.indigo;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
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
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.LcAnimatable;
import org.unitego.lobecorp.animation.LcAnimationController;
import org.unitego.lobecorp.animation.LcAnimationLayerRegistrar;
import org.unitego.lobecorp.animation.LcBlendMode;
import org.unitego.lobecorp.animation.LcBoneMask;
import org.unitego.lobecorp.animation.LcLayerDefinition;
import org.unitego.lobecorp.entity.IEntityTarget;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkillHolder;
import org.unitego.lobecorp.entity.entity_state.EntityState;
import org.unitego.lobecorp.entity.entity_state.EntityStateHolder;
import org.unitego.lobecorp.registry.entity.LcEntityDataSerializers;
import org.unitego.lobecorp.registry.entity.LcAttributes;
import org.unitego.lobecorp.registry.entity_skill.SweeperSkills;

import java.util.List;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 清道夫
public class Sweeper extends PathfinderMob implements Enemy, LcAnimatable, IIndigoOrdeal, IEntityTarget, IEntitySkillHolder, EntityStateHolder {
	/// 清道夫基础最大生命值
	public static final float BASE_MAX_HEALTH = 25.0F;
	/// 清道夫基础护甲值
	private static final double BASE_ARMOR = 8.0;
	/// 清道夫基础护甲韧性
	private static final double BASE_ARMOR_TOUGHNESS = 2.0;
	/// 清道夫基础攻击伤害
	private static final double BASE_ATTACK_DAMAGE = 6.0;
	/// 清道夫基础移动速度
	private static final double BASE_MOVEMENT_SPEED = 0.23;
	/// 清道夫基础击退抗性
	private static final double BASE_KNOCKBACK_RESISTANCE = 0.4;
	/// 普通攻击临时伤害倍率属性的唯一标识
	public static final Identifier ATTACK_MULTIPLIER = Lobecorp.id("attack_multiplier");
	/// 基础移动动画层名称
	private static final String LOCOMOTION_ANIMATION_LAYER = "locomotion";
	/// 技能动作动画层名称
	private static final String ACTION_ANIMATION_LAYER = "action";
	/// 基础移动动画层过渡时间
	private static final int LOCOMOTION_TRANSITION_TICKS = 3;
	/// 技能动作动画层过渡时间
	private static final int ACTION_TRANSITION_TICKS = 3;
	/// 切换到奔跑动画所需的移动动画速度
	private static final float RUN_ANIMATION_SPEED_THRESHOLD = 0.5F;
	/// 从奔跑动画退出所需的移动动画速度
	private static final float RUN_ANIMATION_EXIT_SPEED_THRESHOLD = 0.4F;
	/// 切换到移动动画所需的移动动画速度
	private static final float MOVE_ANIMATION_SPEED_THRESHOLD = 0.1F;
	/// 从移动动画退出所需的移动动画速度
	private static final float MOVE_ANIMATION_EXIT_SPEED_THRESHOLD = 0.05F;
	/// 清道夫模型的全身骨骼遮罩
	private static final LcBoneMask FULL_BODY_ANIMATION_MASK = LcBoneMask.builder().includeRoot("root").build();
	/// 同步清道夫外观变种的数据字段
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(Sweeper.class, EntityDataSerializers.INT);
	/// 同步清道夫当前生物质的数据字段
	private static final EntityDataAccessor<Float> DATA_BIOMASS = SynchedEntityData.defineId(Sweeper.class, EntityDataSerializers.FLOAT);
	/// 同步清道夫当前实体状态的数据字段
	private static final EntityDataAccessor<List<EntityState>> DATA_ENTITY_STATES =
			SynchedEntityData.defineId(Sweeper.class, LcEntityDataSerializers.ENTITY_STATES.get());
	/// 生物质存档字段名称
	private static final String BIOMASS_SAVE_KEY = "Biomass";
	/// 生物质上限相对最大生命值的倍率
	private static final float BIOMASS_CAPACITY_MULTIPLIER = 1.0F;
	/// 治疗溢出转换为生物质的比例分母
	private static final float OVERHEAL_TO_BIOMASS_DIVISOR = 5.0F;
	/// 每秒使用生物质恢复的最大生命比例
	private static final float BIOMASS_HEAL_MAX_HEALTH_RATIO_PER_SECOND = 0.05F;
	/// 脱离战斗后开始使用生物质回血的延迟 tick
	private static final int BIOMASS_HEAL_COMBAT_DELAY_TICKS = 2 * TICKS_PER_SECOND;
	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
	private final LcAnimationController<Sweeper> animationController =
			new LcAnimationController<>(this::registerLcAnimationLayers);
	private @Nullable SweeperAnim locomotionAnimation;
	private long lastCombatGameTime;
	private boolean recoveryCleanup;
	private boolean recoveryCleanupDecisionMade;
	private long recoveryCleanupRetryGameTime;
	@Nullable
	private Entity entityTarget;

	public Sweeper(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		setPathfindingMalus(PathType.LAVA, -1);
		setPathfindingMalus(PathType.FIRE, -1);
		setPathfindingMalus(PathType.WATER, 10.0F);
		setPathfindingMalus(PathType.WATER_BORDER, 5.0F);
		EntitySkillManager.addSkill(this, SweeperSkills.ATTACK.get());
		EntitySkillManager.addSkill(this, SweeperSkills.LEAP.get());
		EntitySkillManager.addSkill(this, SweeperSkills.SWEEP.get());
		EntitySkillManager.addSkill(this, SweeperSkills.REASSEMBLE.get());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, 0);
		builder.define(DATA_BIOMASS, 0.0F);
		builder.define(DATA_ENTITY_STATES, List.of());
	}

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
		return EntitySkillManager.getAttackCombo(this);
	}

	public void setAttackCombo(int combo) {
		EntitySkillManager.setAttackCombo(this, combo);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMobAttributes()
				.add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
				.add(Attributes.ARMOR, BASE_ARMOR)
				.add(Attributes.ARMOR_TOUGHNESS, BASE_ARMOR_TOUGHNESS)
				.add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
				.add(LcAttributes.ENTITY_SKILL_COOLDOWN_MULTIPLIER, 1.0)
				.add(LcAttributes.DAMAGE_TAKEN_MULTIPLIER)
				.add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, BASE_KNOCKBACK_RESISTANCE);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		restoreHealthFromBiomass();
		ProfilerFiller profiler = Profiler.get();
		profiler.push("sweeperBrain");
		getBrain().tick(level, this);
		profiler.pop();
		profiler.push("sweeperActivityUpdate");
		SweeperAi.updateActivity(this);
		profiler.pop();
		super.customServerAiStep(level);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			tickLocomotionAnimation();
		}
	}

	private void tickLocomotionAnimation() {
		if (isRemoved() || isDeadOrDying()) {
			if (locomotionAnimation != null) {
				animationController.end(LOCOMOTION_ANIMATION_LAYER);
				animationController.end(ACTION_ANIMATION_LAYER);
				locomotionAnimation = null;
			}
			return;
		}
		float speed = walkAnimation.speed();
		SweeperAnim animation = selectLocomotionAnimation(speed);
		if (animation != locomotionAnimation) {
			animationController.play(LOCOMOTION_ANIMATION_LAYER, animation.getAnimation());
			locomotionAnimation = animation;
		}
	}

	private SweeperAnim selectLocomotionAnimation(float speed) {
		if (speed > RUN_ANIMATION_SPEED_THRESHOLD
				|| locomotionAnimation == SweeperAnim.RUN && speed > RUN_ANIMATION_EXIT_SPEED_THRESHOLD) {
			return SweeperAnim.RUN;
		}
		if (speed > MOVE_ANIMATION_SPEED_THRESHOLD
				|| locomotionAnimation == SweeperAnim.MOVE && speed > MOVE_ANIMATION_EXIT_SPEED_THRESHOLD) {
			return SweeperAnim.MOVE;
		}
		return SweeperAnim.IDLE;
	}

	public void playActionAnimation(SweeperAnim animation) {
		if (level().isClientSide()) {
			animationController.play(ACTION_ANIMATION_LAYER, animation.getAnimation());
		}
	}

	public void stopActionAnimation() {
		if (level().isClientSide()) {
			animationController.end(ACTION_ANIMATION_LAYER);
		}
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

	public void markCombat() {
		lastCombatGameTime = level().getGameTime();
	}

	private void restoreHealthFromBiomass() {
		if (getHealth() >= getMaxHealth() || getBiomass() <= 0.0F
				|| level().getGameTime() - lastCombatGameTime < BIOMASS_HEAL_COMBAT_DELAY_TICKS) {
			return;
		}
		float restoredHealth = Math.min(Math.min(getMaxHealth() - getHealth(), getBiomass()),
				getMaxHealth() * BIOMASS_HEAL_MAX_HEALTH_RATIO_PER_SECOND / TICKS_PER_SECOND);
		setHealth(getHealth() + restoredHealth);
		setBiomass(getBiomass() - restoredHealth);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof LivingEntity attacker) {
			trySetAttackTargetFromDamage(attacker);
		}
		return hurt;
	}

	/// 尝试把造成伤害的有效实体设为攻击目标；完全由生物质吸收的伤害也可调用。
	public void trySetAttackTargetFromDamage(LivingEntity attacker) {
		if (!isValidTarget(attacker) || !SweeperAi.onHurt(this)) {
			return;
		}
		getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
		getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
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
		markCombat();

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

	boolean isRecoveryCleanup() {
		return recoveryCleanup;
	}

	void setRecoveryCleanup(boolean recoveryCleanup) {
		this.recoveryCleanup = recoveryCleanup;
	}

	boolean isRecoveryCleanupDecisionMade() {
		return recoveryCleanupDecisionMade;
	}

	void setRecoveryCleanupDecisionMade(boolean recoveryCleanupDecisionMade) {
		this.recoveryCleanupDecisionMade = recoveryCleanupDecisionMade;
	}

	long getRecoveryCleanupRetryGameTime() {
		return recoveryCleanupRetryGameTime;
	}

	void setRecoveryCleanupRetryGameTime(long recoveryCleanupRetryGameTime) {
		this.recoveryCleanupRetryGameTime = recoveryCleanupRetryGameTime;
	}

	@Override
	public List<EntityState> getEntityStates() {
		return getEntityData().get(DATA_ENTITY_STATES);
	}

	@Override
	public void setEntityStates(List<EntityState> states) {
		getEntityData().set(DATA_ENTITY_STATES, List.copyOf(states), true);
	}

	@Override
	public void registerControllers(AnimatableManager.@NonNull ControllerRegistrar controllers) {
		controllers.add(animationController);
	}

	@Override
	public void registerLcAnimationLayers(LcAnimationLayerRegistrar registrar) {
		registrar.add(new LcLayerDefinition(LOCOMOTION_ANIMATION_LAYER, LcBlendMode.OVERRIDE,
				FULL_BODY_ANIMATION_MASK, LOCOMOTION_TRANSITION_TICKS));
		registrar.add(new LcLayerDefinition(ACTION_ANIMATION_LAYER, LcBlendMode.OVERRIDE,
				FULL_BODY_ANIMATION_MASK, ACTION_TRANSITION_TICKS));
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
		return SweeperAi.makeBrain(this, packedBrain);
	}

	@Override
	public Brain<Sweeper> getBrain() {
		return (Brain<Sweeper>) super.getBrain();
	}
}
