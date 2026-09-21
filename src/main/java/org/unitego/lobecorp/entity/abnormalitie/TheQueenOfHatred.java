package org.unitego.lobecorp.entity.abnormalitie;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.LcAnimatable;
import org.unitego.lobecorp.animation.LcAnimationController;
import org.unitego.lobecorp.animation.LcAnimationLayerRegistrar;
import org.unitego.lobecorp.animation.LcBlendMode;
import org.unitego.lobecorp.animation.LcBoneMask;
import org.unitego.lobecorp.animation.LcLayerDefinition;
import org.unitego.lobecorp.entity.ai.control.TheQueenOfHatredCombatController;
import org.unitego.lobecorp.entity.ai.control.TheQueenOfHatredMoveControl;
import org.unitego.lobecorp.entity.ai.movement.TheQueenOfHatredMovementIntent;
import org.unitego.lobecorp.entity.ai.movement.TheQueenOfHatredMovementMode;
import org.unitego.lobecorp.entity.ai.movement.TheQueenOfHatredNavigationCoordinator;
import org.unitego.lobecorp.entity.ai.navigation.TheQueenOfHatredFlyingPathNavigation;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkillHolder;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredAttackMode;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredHealSkill;
import org.unitego.lobecorp.entity.entity_skill.abnormalitie.TheQueenOfHatredTeleportSkill;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.entity.LcAttributes;
import org.unitego.lobecorp.registry.entity_skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.registry.tag.LcEntitySkillTags;

import java.util.Set;

import static org.unitego.lobecorp.Lobecorp.id;

public class TheQueenOfHatred extends PathfinderMob implements LcAnimatable, IEntitySkillHolder, IAbnormalitie {
	/// 基础移动动画层名称。
	private static final String LOCOMOTION_ANIMATION_LAYER = "locomotion";
	/// 技能动作动画层名称。
	private static final String ACTION_ANIMATION_LAYER = "action";
	/// 动画层默认过渡时间。
	private static final int ANIMATION_TRANSITION_TICKS = 3;
	/// 进入行走动画所需的移动动画速度。
	private static final float WALK_ANIMATION_SPEED_THRESHOLD = 0.1F;
	/// 退出行走动画所需的移动动画速度。
	private static final float WALK_ANIMATION_EXIT_SPEED_THRESHOLD = 0.05F;
	/// 憎恶女皇模型的全身骨骼遮罩。
	private static final LcBoneMask FULL_BODY_ANIMATION_MASK = LcBoneMask.builder().includeRoot("root").build();
	/// 基础最大生命值。
	private static final double BASE_MAX_HEALTH = 1200.0;
	/// 基础攻击伤害。
	private static final double BASE_ATTACK_DAMAGE = 10.0;
	/// 基础移动速度。
	private static final double BASE_MOVEMENT_SPEED = 0.2;
	/// 远程攻击预测目标位置时允许外推的最大 tick。
	private static final double MAXIMUM_ATTACK_PREDICTION_TICKS = 12.0;
	/// 悬浮飞行速度相对普通移动速度的倍率。
	private static final double HOVER_FLYING_SPEED_MULTIPLIER = 4.0;
	/// 将飞行速度转换为普通空中移动加速度的比例。
	private static final float HOVER_AIR_ACCELERATION_MULTIPLIER = 0.1F;
	/// 移动和位移技能应尽量与每个有效敌对目标保持的最小距离。
	public static final double HOSTILE_SAFE_DISTANCE = 5.0;
	/// 没有有效技能计划时采用的默认环绕最大距离。
	public static final double DEFAULT_ORBIT_MAXIMUM_RANGE = 7.0;
	/// 同步憎恶皇后当前移动执行模式的数据字段。
	private static final EntityDataAccessor<Integer> DATA_MOVEMENT_MODE =
			SynchedEntityData.defineId(TheQueenOfHatred.class, EntityDataSerializers.INT);
	/// 同步不可逆的半血二阶段状态。
	private static final EntityDataAccessor<Boolean> DATA_SECOND_PHASE =
			SynchedEntityData.defineId(TheQueenOfHatred.class, EntityDataSerializers.BOOLEAN);
	/// 二阶段状态的存档字段名称。
	private static final String SECOND_PHASE_SAVE_KEY = "SecondPhase";
	/// 二阶段基础攻击伤害提升十五个百分点的永久属性修饰器。
	private static final AttributeModifier SECOND_PHASE_ATTACK_MODIFIER = new AttributeModifier(
			id("queen_of_hatred_second_phase_attack"), 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	/// 首次进入二阶段的生命比例。
	private static final float SECOND_PHASE_HEALTH_RATIO = 0.5F;
	/// 二阶段技能总冷却时间减少二成，对应冷却速度提升百分之二十五。
	private static final AttributeModifier SECOND_PHASE_COOLDOWN_MODIFIER = new AttributeModifier(
			id("queen_of_hatred_second_phase_cooldown"), -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	/// 二阶段技能结束后允许尝试落地的最长时间，单位为 tick。
	private static final int SECOND_PHASE_LANDING_TIMEOUT_TICKS = 5 * 20;
	/// 二阶段每次技能结束后的落地休息时间，单位为 tick。
	private static final int SECOND_PHASE_REST_TICKS = 5 * 20;
	/// 二阶段休息结束后强制传送选择目标点的距离，单位为格。
	private static final double SECOND_PHASE_TELEPORT_DISTANCE = 24.0;
	/// 二阶段强制落地搜索安全地面的水平半径，单位为格。
	private static final int SECOND_PHASE_SAFE_GROUND_SEARCH_RADIUS = 8;
	/// 减伤法术生效时保留的伤害比例。
	private static final float DAMAGE_REDUCTION_MULTIPLIER = 0.5F;
	/// 直线位移碰撞检查的采样间隔，单位为格。
	private static final double REPOSITION_COLLISION_SAMPLE_DISTANCE = 1.0;
	/// 直线位移受阻时尝试绕过障碍的垂直偏移，单位为格。
	private static final double REPOSITION_OBSTACLE_VERTICAL_OFFSET = 2.0;
	/// 失去飞行控制后等待落地的最长时间，防止异常地形让掉落易伤永久保留。
	private static final int UNCONTROLLED_FALL_TIMEOUT_TICKS = 200;
	/// 动态攻击目标更新的性能分析区段。
	private static final String PROFILER_TARGET_UPDATE = "queenOfHatredTargetUpdate";
	/// 威胁快照更新的性能分析区段。
	private static final String PROFILER_THREAT_SNAPSHOT = "queenOfHatredThreatSnapshot";
	/// Brain 活动更新的性能分析区段。
	private static final String PROFILER_ACTIVITY_UPDATE = "queenOfHatredActivityUpdate";
	/// Brain 执行的性能分析区段。
	private static final String PROFILER_BRAIN = "queenOfHatredBrain";
	/// 导航协调器执行的性能分析区段。
	private static final String PROFILER_NAVIGATION = "queenOfHatredNavigation";

	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
	private final LcAnimationController<TheQueenOfHatred> animationController =
			new LcAnimationController<>(this::registerLcAnimationLayers);
	private @Nullable TheQueenOfHatredAnim locomotionAnimation;
	private final Set<LivingEntity> retaliationTargets = Sets.newIdentityHashSet();
	private final TheQueenOfHatredCombatController combatController = new TheQueenOfHatredCombatController(this);
	private final PathNavigation groundNavigation;
	private final PathNavigation hoverNavigation;
	private final TheQueenOfHatredMoveControl queenMoveControl;
	private final TheQueenOfHatredNavigationCoordinator navigationCoordinator =
			new TheQueenOfHatredNavigationCoordinator(this);
	private TeleportRequest teleportRequest = TeleportRequest.NONE;
	private boolean retreatBlinkRequested;
	private int damageReductionTicks;
	private boolean dispelLanding;
	private boolean threatBlinkCooldownRequested;
	private SecondPhaseRestState secondPhaseRestState = SecondPhaseRestState.NONE;
	private int secondPhaseRestTicks;
	private boolean forcedSecondPhaseTeleport;
	private int uncontrolledFallTicks;
	private boolean uncontrolledFallAirborne;
	@Nullable
	private Vec3 repositionDestination;

	public TheQueenOfHatred(Level level) {
		this(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), level);
	}

	public TheQueenOfHatred(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		groundNavigation = getNavigation();
		hoverNavigation = new TheQueenOfHatredFlyingPathNavigation(this, level);
		queenMoveControl = new TheQueenOfHatredMoveControl(this);
		moveControl = queenMoveControl;
		EntitySkillManager.addSkills(this, TheQueenOfHatredSkills.BLINK.get(),
				TheQueenOfHatredSkills.TELEPORT.get(),
				TheQueenOfHatredSkills.SWEEP.get(),
				TheQueenOfHatredSkills.DISPEL.get(),
				TheQueenOfHatredSkills.SPIN.get(),
				TheQueenOfHatredSkills.LASER.get(),
				TheQueenOfHatredSkills.HEAL.get(),
				TheQueenOfHatredSkills.DAMAGE_REDUCTION.get(),
				TheQueenOfHatredSkills.PURIFICATION.get(),
				TheQueenOfHatredSkills.SLOWNESS.get(),
				TheQueenOfHatredSkills.MARK.get(),
				TheQueenOfHatredSkills.STARFALL.get(),
				TheQueenOfHatredSkills.PILLAR_OF_LIGHT.get());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MOVEMENT_MODE, TheQueenOfHatredMovementMode.GROUND.id());
		builder.define(DATA_SECOND_PHASE, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		if (input.read(SECOND_PHASE_SAVE_KEY, Codec.BOOL).orElse(false)
				|| getHealth() <= getMaxHealth() * SECOND_PHASE_HEALTH_RATIO) {
			enterSecondPhase();
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store(SECOND_PHASE_SAVE_KEY, Codec.BOOL, isSecondPhase());
	}

	@Override
	protected PathNavigation createNavigation(Level level) {
		return new GroundPathNavigation(this, level);
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(animationController);
	}

	@Override
	public void registerLcAnimationLayers(LcAnimationLayerRegistrar registrar) {
		registrar.add(new LcLayerDefinition(LOCOMOTION_ANIMATION_LAYER, LcBlendMode.OVERRIDE,
				FULL_BODY_ANIMATION_MASK, ANIMATION_TRANSITION_TICKS));
		registrar.add(new LcLayerDefinition(ACTION_ANIMATION_LAYER, LcBlendMode.OVERRIDE,
				FULL_BODY_ANIMATION_MASK, ANIMATION_TRANSITION_TICKS));
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
		TheQueenOfHatredAnim animation = speed > WALK_ANIMATION_SPEED_THRESHOLD
				|| locomotionAnimation == TheQueenOfHatredAnim.WALK
				&& speed > WALK_ANIMATION_EXIT_SPEED_THRESHOLD
				? TheQueenOfHatredAnim.WALK : TheQueenOfHatredAnim.IDLE;
		if (animation != locomotionAnimation) {
			animationController.play(LOCOMOTION_ANIMATION_LAYER, animation.getAnimation());
			locomotionAnimation = animation;
		}
	}

	public void playActionAnimation(TheQueenOfHatredAnim animation) {
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
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animatableInstanceCache;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Player.createAttributes()
				.add(Attributes.MAX_HEALTH, BASE_MAX_HEALTH)
				.add(Attributes.ATTACK_DAMAGE, BASE_ATTACK_DAMAGE)
				.add(LcAttributes.DAMAGE_TAKEN_MULTIPLIER)
				.add(Attributes.MOVEMENT_SPEED, BASE_MOVEMENT_SPEED)
				.add(Attributes.FLYING_SPEED, BASE_MOVEMENT_SPEED * HOVER_FLYING_SPEED_MULTIPLIER)
				.add(LcAttributes.ENTITY_SKILL_COOLDOWN_MULTIPLIER, 1.0)
				.add(Attributes.FOLLOW_RANGE, TheQueenOfHatredAi.TARGET_SEARCH_RANGE);
	}

	@Override
	protected float getFlyingSpeed() {
		return isHovering() ? getSpeed() * HOVER_AIR_ACCELERATION_MULTIPLIER : super.getFlyingSpeed();
	}

	public boolean isHovering() {
		return !isUncontrolledFalling() && movementMode().isHovering();
	}

	/// 使女皇暂时失去飞行控制；在她实际落地或超时前，掉落伤害不再被免疫。
	public void beginUncontrolledFall() {
		uncontrolledFallTicks = UNCONTROLLED_FALL_TIMEOUT_TICKS;
		uncontrolledFallAirborne = !onGround();
		navigationCoordinator.clearIntent();
		setMovementMode(TheQueenOfHatredMovementMode.GROUND);
		activateNavigation(TheQueenOfHatredMovementMode.GROUND);
		stopNavigationMovement();
		resetFallDistance();
	}

	public boolean isUncontrolledFalling() {
		return uncontrolledFallTicks > 0;
	}

	public TheQueenOfHatredMovementMode movementMode() {
		return TheQueenOfHatredMovementMode.byId(getEntityData().get(DATA_MOVEMENT_MODE));
	}

	public void setMovementMode(TheQueenOfHatredMovementMode movementMode) {
		getEntityData().set(DATA_MOVEMENT_MODE, movementMode.id());
	}

	public void activateNavigation(TheQueenOfHatredMovementMode movementMode) {
		PathNavigation nextNavigation = movementMode == TheQueenOfHatredMovementMode.GROUND
				? groundNavigation : hoverNavigation;
		if (navigation == nextNavigation) {
			return;
		}
		navigation.stop();
		queenMoveControl.stop();
		navigation = nextNavigation;
	}

	public void stopNavigationMovement() {
		navigation.stop();
		queenMoveControl.stop();
	}

	public double getHoverPathTargetY() {
		return Double.NaN;
	}

	public void setHoverPathTargetY(double targetY) {
	}

	public void clearHoverPathTargetY() {
	}

	public boolean isDispelLanding() {
		return dispelLanding;
	}

	public TheQueenOfHatredAttackMode combatMode() {
		return combatController.mode();
	}

	public double combatMinimumRange() {
		return combatController.minimumRange();
	}

	public double combatMaximumRange() {
		return combatController.maximumRange();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		long aiStepStartNanos = System.nanoTime();
		tickUncontrolledFall();
		if (!isSecondPhase() && getHealth() <= getMaxHealth() * SECOND_PHASE_HEALTH_RATIO) {
			enterSecondPhase();
		}
		if (tickSecondPhaseRest()) {
			combatController.recordAiStepCost(System.nanoTime() - aiStepStartNanos);
			super.customServerAiStep(level);
			return;
		}
		if (damageReductionTicks > 0) {
			damageReductionTicks--;
		}
		ProfilerFiller profiler = Profiler.get();
		profiler.push(PROFILER_TARGET_UPDATE);
		TheQueenOfHatredAi.updateDynamicAttackTarget(this);
		profiler.pop();
		profiler.push(PROFILER_THREAT_SNAPSHOT);
		combatController.updateThreatSnapshot();
		profiler.pop();
		profiler.push(PROFILER_ACTIVITY_UPDATE);
		TheQueenOfHatredAi.updateActivity(this);
		profiler.pop();
		profiler.push(PROFILER_BRAIN);
        getBrain().tick(level, this);
        profiler.pop();
		profiler.push(PROFILER_NAVIGATION);
		navigationCoordinator.tick();
		profiler.pop();
		combatController.recordAiStepCost(System.nanoTime() - aiStepStartNanos);
        super.customServerAiStep(level);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.is(DamageTypeTags.IS_FALL) && !isUncontrolledFalling()) {
			return false;
		}
		if (EntitySkillManager.isActive(this, TheQueenOfHatredSkills.BLINK.get())) {
			return false;
		}
		float previousHealth = getHealth();
		boolean hurt = super.hurtServer(level, source, damage);
		if (getHealth() < previousHealth) {
			if (EntitySkillManager.isActive(this, TheQueenOfHatredSkills.HEAL.get())) {
				EntitySkillManager.cancelSkill(this, TheQueenOfHatredSkills.HEAL.get());
			}
			EntitySkillManager.cancelSkills(this, LcEntitySkillTags.SPELL);
		}
		if (!hurt || !(source.getEntity() instanceof LivingEntity attacker) || attacker == this
				|| attacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return hurt;
		}
		retaliationTargets.add(attacker);
		getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
		combatController.requestEmergencyDodge(attacker);
		return true;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return isUncontrolledFalling() && super.causeFallDamage(fallDistance, damageModifier, damageSource);
	}

	@Override
	public void onEntitySkillPhaseChanged(EntitySkillRuntime<?> runtime) {
		if (runtime.state() == EntitySkillRuntime.SkillState.RECOVERY
				&& isSecondPhase() && isSecondPhaseRestTrigger(runtime)) {
			runtime.setTicksLeft(0);
			beginSecondPhaseLanding();
		}
	}

	@Override
	public void onEntitySkillEnded(EntitySkillRuntime<?> runtime) {
		combatController.recordAttackSkillCompleted(runtime);
		if (forcedSecondPhaseTeleport && runtime.skill() == TheQueenOfHatredSkills.TELEPORT.get()) {
			EntitySkillManager.setCooldown(this, runtime.skill(), 0);
			forcedSecondPhaseTeleport = false;
			secondPhaseRestState = SecondPhaseRestState.NONE;
		}
	}

	@Override
	public boolean canCastEntitySkill(org.unitego.lobecorp.entity.entity_skill.IEntitySkill<?> skill) {
		return secondPhaseRestState == SecondPhaseRestState.NONE
				|| forcedSecondPhaseTeleport && skill == TheQueenOfHatredSkills.TELEPORT.get();
	}

	@Override
	public int adjustEntitySkillCooldown(EntitySkillRuntime<?> runtime, int cooldownTicks) {
		if (forcedSecondPhaseTeleport && runtime.skill() == TheQueenOfHatredSkills.TELEPORT.get()) {
			return 0;
		}
		return cooldownTicks;
	}

	public boolean isSecondPhase() {
		return getEntityData().get(DATA_SECOND_PHASE);
	}

	public boolean isForcedSecondPhaseTeleport() {
		return forcedSecondPhaseTeleport;
	}

	private void enterSecondPhase() {
		getEntityData().set(DATA_SECOND_PHASE, true);
		var attackDamage = getAttribute(Attributes.ATTACK_DAMAGE);
		if (attackDamage != null && !attackDamage.hasModifier(SECOND_PHASE_ATTACK_MODIFIER.id())) {
			attackDamage.addOrUpdateTransientModifier(SECOND_PHASE_ATTACK_MODIFIER);
		}
		var cooldownMultiplier = getAttribute(LcAttributes.ENTITY_SKILL_COOLDOWN_MULTIPLIER);
		if (cooldownMultiplier != null && !cooldownMultiplier.hasModifier(SECOND_PHASE_COOLDOWN_MODIFIER.id())) {
			cooldownMultiplier.addOrUpdateTransientModifier(SECOND_PHASE_COOLDOWN_MODIFIER);
		}
	}

	private boolean isSecondPhaseRestTrigger(EntitySkillRuntime<?> runtime) {
		return runtime.skill().is(LcEntitySkillTags.DAMAGE);
	}

	private void beginSecondPhaseLanding() {
		secondPhaseRestState = SecondPhaseRestState.LANDING;
		secondPhaseRestTicks = 0;
		if (onGround()) {
			stopNavigationMovement();
		} else {
			beginUncontrolledFall();
		}
	}

	private void tickUncontrolledFall() {
		if (!isUncontrolledFalling()) {
			return;
		}
		uncontrolledFallTicks--;
		if (!isUncontrolledFalling()) {
			uncontrolledFallAirborne = false;
			return;
		}
		if (!onGround()) {
			uncontrolledFallAirborne = true;
		} else if (uncontrolledFallAirborne) {
			uncontrolledFallTicks = 0;
			uncontrolledFallAirborne = false;
		}
	}

	private boolean tickSecondPhaseRest() {
		if (secondPhaseRestState == SecondPhaseRestState.NONE) {
			return false;
		}
		stopNavigationMovement();
		if (!isUncontrolledFalling()) {
			resetFallDistance();
		}
		if (secondPhaseRestState == SecondPhaseRestState.LANDING) {
			requestLandingMovement(true);
			navigationCoordinator.tick();
			if (onGround()) {
				secondPhaseRestState = SecondPhaseRestState.RESTING;
				secondPhaseRestTicks = SECOND_PHASE_REST_TICKS;
			} else if (++secondPhaseRestTicks >= SECOND_PHASE_LANDING_TIMEOUT_TICKS) {
				moveToSecondPhaseSafeGround();
			}
			return true;
		}
		setDeltaMovement(Vec3.ZERO);
		if (secondPhaseRestState == SecondPhaseRestState.RESTING && --secondPhaseRestTicks <= 0) {
			startForcedSecondPhaseTeleport();
		}
		return true;
	}

	private void moveToSecondPhaseSafeGround() {
		BlockPos origin = blockPosition();
		for (int radius = 0; radius <= SECOND_PHASE_SAFE_GROUND_SEARCH_RADIUS; radius++) {
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					BlockPos surface = level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
							origin.offset(x, 0, z));
					Vec3 destination = Vec3.atBottomCenterOf(surface);
					if (level().noCollision(this, getBoundingBox().move(destination.subtract(position())))) {
						absSnapTo(destination.x, destination.y, destination.z, getYRot(), getXRot());
						secondPhaseRestState = SecondPhaseRestState.RESTING;
						secondPhaseRestTicks = SECOND_PHASE_REST_TICKS;
						return;
					}
				}
			}
		}
		secondPhaseRestState = SecondPhaseRestState.NONE;
	}

	private void startForcedSecondPhaseTeleport() {
		LivingEntity target = getAttackTarget();
		Vec3 direction = target == null ? getLookAngle().scale(-1.0)
				: position().subtract(target.position());
		if (direction.horizontalDistanceSqr() <= Mth.square(Mth.EPSILON)) {
			direction = Vec3.directionFromRotation(0.0F, getYRot()).scale(-1.0);
		}
		Vec3 destination = position().add(direction.normalize().scale(SECOND_PHASE_TELEPORT_DISTANCE));
		Vec3 safeDestination = findRepositionDestination(destination,
				TheQueenOfHatredTeleportSkill.MAXIMUM_DISTANCE, false);
		if (safeDestination == null) {
			secondPhaseRestState = SecondPhaseRestState.NONE;
			return;
		}
		requestTeleport(true);
		requestRepositionDestination(safeDestination);
		EntitySkillManager.setCooldown(this, TheQueenOfHatredSkills.TELEPORT.get(), 0);
		forcedSecondPhaseTeleport = true;
		secondPhaseRestState = SecondPhaseRestState.TELEPORT_WINDUP;
		if (!EntitySkillManager.cast(this, TheQueenOfHatredSkills.TELEPORT.get())) {
			forcedSecondPhaseTeleport = false;
			secondPhaseRestState = SecondPhaseRestState.NONE;
			clearRepositionDestination();
			clearTeleportRequest();
		}
	}

	@Override
	protected float getDamageAfterArmorAbsorb(DamageSource source, float damage) {
		float reducedDamage = super.getDamageAfterArmorAbsorb(source, damage);
		if (damageReductionTicks > 0) {
			return reducedDamage * DAMAGE_REDUCTION_MULTIPLIER;
		}
		return reducedDamage;
	}

	/// 启动或刷新减伤法术的临时效果。该状态不持久化，也不同步。
	///
	/// @param durationTicks 从当前时刻开始计算的持续 tick
	public void startDamageReduction(int durationTicks) {
		damageReductionTicks = Math.max(durationTicks, 0);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (TheQueenOfHatredHealSkill.handleEntityEvent(this, id)) {
			return;
		}
		super.handleEntityEvent(id);
	}

	/// 判断实体是否属于憎恶皇后当前临时规则下的有效敌对目标。
	/// 原版敌对生物始终有效，其他生物仅在主动伤害憎恶皇后后作为反击目标。
	///
	/// @param entity 候选目标
	/// @return 目标存活、可攻击且符合临时敌对规则时返回 {@code true}
	public boolean isValidTarget(Entity entity) {
		return entity instanceof LivingEntity living && living != this && living.isAlive()
				&& (!(living instanceof Player player) || !player.isCreative() && !player.isSpectator())
				&& (living instanceof Enemy || retaliationTargets.contains(living));
	}

	/// @return Brain 当前保存的攻击目标；没有目标时返回 {@code null}
	@Nullable
	public LivingEntity getAttackTarget() {
		return getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
	}

	/// 记录待执行的传送请求。撤离请求优先于接近请求。
	///
	/// @param retreat {@code true} 表示撤离，{@code false} 表示接近目标
	public void requestTeleport(boolean retreat) {
		teleportRequest = retreat ? TeleportRequest.RETREAT : TeleportRequest.APPROACH;
	}

	/// @return 当前是否存在尚未开始执行的传送请求
	public boolean hasTeleportRequest() {
		return teleportRequest != TeleportRequest.NONE;
	}

	/// @return 当前传送请求是否要求远离敌群
	public boolean isRetreatTeleportRequested() {
		return teleportRequest == TeleportRequest.RETREAT;
	}

	/// 清除尚未执行的传送请求。技能开始或取消时由传送技能调用。
	public void clearTeleportRequest() {
		teleportRequest = TeleportRequest.NONE;
	}

	/// 将位移目标限制到最大距离，并按直线、上绕和下绕顺序寻找可用落点。
	///
	/// @param destination 位移目标
	/// @param maximumDistance 单次位移允许的最大距离
	/// @param requireClearRoute 是否要求起点到终点的整段路线无碰撞
	/// @return 可用落点；没有安全落点时返回 {@code null}
	@Nullable
	public Vec3 findRepositionDestination(Vec3 destination, double maximumDistance, boolean requireClearRoute) {
		Vec3 displacement = destination.subtract(position());
		if (maximumDistance <= 0.0 || displacement.lengthSqr() <= Mth.square(Mth.EPSILON)) {
			return null;
		}
		if (displacement.lengthSqr() > Mth.square(maximumDistance)) {
			destination = position().add(displacement.normalize().scale(maximumDistance));
		}
		Vec3[] candidates = {
				destination,
				destination.add(0.0, REPOSITION_OBSTACLE_VERTICAL_OFFSET, 0.0),
				destination.add(0.0, -REPOSITION_OBSTACLE_VERTICAL_OFFSET, 0.0)
		};
		for (Vec3 candidate : candidates) {
			if (isSafeRepositionDestination(candidate, requireClearRoute)) {
				return candidate;
			}
		}
		navigationCoordinator.reportFailure(TheQueenOfHatredNavigationCoordinator.FailureReason.PATH_NOT_FOUND);
		return null;
	}

	private boolean isSafeRepositionDestination(Vec3 destination, boolean requireClearRoute) {
		Vec3 displacement = destination.subtract(position());
		int samples = requireClearRoute
				? Math.max(1, Mth.ceil(displacement.length() / REPOSITION_COLLISION_SAMPLE_DISTANCE)) : 1;
		for (int index = 1; index <= samples; index++) {
			Vec3 sample = position().add(displacement.scale((double) index / samples));
			if (!level().noCollision(this, getBoundingBox().move(sample.subtract(position())))) {
				return false;
			}
		}
		return true;
	}

	/// 保存攻击规划器为下一次位移技能生成的落点。
	///
	/// @param destination 瞬步或传送应使用的落点
	public void requestRepositionDestination(Vec3 destination) {
		repositionDestination = destination;
	}

	/// 读取并清除本次位移技能落点。
	///
	/// @return 当前待执行落点；没有落点时返回 {@code null}
	@Nullable
	public Vec3 consumeRepositionDestination() {
		Vec3 destination = repositionDestination;
		repositionDestination = null;
		return destination;
	}

	/// 清除尚未被位移技能读取的落点。
	public void clearRepositionDestination() {
		repositionDestination = null;
	}

	public void startDispelLanding(@Nullable Vec3 destination) {
		dispelLanding = true;
		requestLandingMovement(true, destination);
	}

	public void finishDispelLanding() {
		dispelLanding = false;
		navigationCoordinator.finishForcedLanding();
	}

	public boolean consumeThreatBlinkCooldownRequest() {
		boolean requested = threatBlinkCooldownRequested;
		threatBlinkCooldownRequested = false;
		return requested;
	}

	public void requestThreatBlinkCooldown() {
		threatBlinkCooldownRequested = true;
	}

	public void clearThreatBlinkCooldownRequest() {
		threatBlinkCooldownRequested = false;
	}

	/// 读取并清除受击瞬步请求，确保该方向约束只作用于本次施放判定。
	///
	/// @return 本次瞬步是否由受击触发并必须向攻击者外侧移动
	public boolean consumeRetreatBlinkRequest() {
		boolean requested = retreatBlinkRequested;
		retreatBlinkRequested = false;
		return requested;
	}

	/// 立即统一身体、头部和俯仰角，使瞬步或传送后的朝向稳定指向目标。
	///
	/// @param target 需要面向的目标
	public void faceTarget(LivingEntity target) {
		facePosition(target.getEyePosition());
	}

	/// 立即统一身体、头部和俯仰角，使朝向稳定指向指定位置。
	///
	/// @param position 需要面向的世界位置
	public void facePosition(Vec3 position) {
		Vec3 direction = position.subtract(getEyePosition());
		if (direction.lengthSqr() <= Mth.square(1.0E-4)) {
			return;
		}
		float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(direction.y, direction.horizontalDistance()) * Mth.RAD_TO_DEG);
		setYRot(yaw);
		setYHeadRot(yaw);
		yBodyRot = yaw;
		setXRot(pitch);
		yRotO = yaw;
		yHeadRotO = yaw;
		yBodyRotO = yaw;
		xRotO = pitch;
	}

	/// 按指定飞行时间预测目标中心位置。
	///
	/// @param target 目标
	/// @param travelTicks 预计命中所需 tick
	/// @return 受最大提前量限制的预测位置
	public Vec3 predictTargetPosition(LivingEntity target, double travelTicks) {
		double predictionTicks = Mth.clamp(travelTicks, 0.0, MAXIMUM_ATTACK_PREDICTION_TICKS);
		return target.getBoundingBox().getCenter().add(target.getDeltaMovement().scale(predictionTicks));
	}

	/// 根据起点、目标距离和攻击速度预测交汇位置。
	///
	/// @param target 目标
	/// @param origin 攻击起点
	/// @param speed 每 tick 速度
	/// @return 预测交汇位置
	public Vec3 predictTargetPosition(LivingEntity target, Vec3 origin, double speed) {
		if (speed <= 0.0) {
			return predictTargetPosition(target, MAXIMUM_ATTACK_PREDICTION_TICKS);
		}
		double travelTicks = target.getBoundingBox().getCenter().distanceTo(origin) / speed;
		return predictTargetPosition(target, travelTicks);
	}

	/// @param target 目标
	/// @return 使用最大提前量得到的预测位置
	public Vec3 predictTargetPosition(LivingEntity target) {
		return predictTargetPosition(target, MAXIMUM_ATTACK_PREDICTION_TICKS);
	}

	Set<LivingEntity> retaliationTargets() {
		return retaliationTargets;
	}

	boolean shouldRefreshCombatTarget() {
		return combatController.shouldRefreshTarget();
	}

	public int combatEnemyAnalysisIntervalTicks() {
		return combatController.enemyAnalysisIntervalTicks();
	}

    boolean tickCombatController() {
        return combatController.tick();
    }

	void requestCombatMovement(LivingEntity target) {
		navigationCoordinator.submit(TheQueenOfHatredMovementIntent.combatPosition(
				target, combatMinimumRange(), combatMaximumRange()));
	}

	public void requestBlockedCombatReposition(LivingEntity target) {
		combatController.requestEmergencyDodge(target);
	}

	void requestIdleMovement(Vec3 destination) {
		navigationCoordinator.submitIdle(TheQueenOfHatredMovementIntent.idleStroll(destination));
	}

	void requestLandingMovement(boolean forced) {
		requestLandingMovement(forced, null);
	}

	void requestLandingMovement(boolean forced, @Nullable Vec3 destination) {
		navigationCoordinator.submit(TheQueenOfHatredMovementIntent.landing(forced, destination));
	}

	void requestIdleLanding() {
		navigationCoordinator.beginIdleLanding();
	}

	public void enterHoverNavigation() {
		LivingEntity target = getAttackTarget();
		if (target != null) {
			requestCombatMovement(target);
		}
	}

	public void leaveHoverNavigation() {
		if (dispelLanding) {
			requestLandingMovement(true);
		} else {
			requestIdleLanding();
		}
	}

	int noProgressTicks() {
		return queenMoveControl.noProgressTicks();
	}

	public int navigationNoProgressTicks() {
		return noProgressTicks();
	}

	public void requestRetreatBlink() {
		retreatBlinkRequested = true;
	}

	public void cancelRetreatBlinkRequest() {
		retreatBlinkRequested = false;
	}

	@Override
	protected Brain<TheQueenOfHatred> makeBrain(Brain.Packed packedBrain) {
		return TheQueenOfHatredAi.makeBrain(this, packedBrain);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Brain<TheQueenOfHatred> getBrain() {
		return (Brain<TheQueenOfHatred>) super.getBrain();
	}

	private enum TeleportRequest {
		NONE,
		APPROACH,
		RETREAT
	}

	private enum SecondPhaseRestState {
		NONE,
		LANDING,
		RESTING,
		TELEPORT_WINDUP
	}

}
