package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.animation.*;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.conductor.data.ConductorData;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredAnim;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.util.entity.EntityFacingUtil;
import org.unitego.lobecorp.world.entity.state.EntityState;
import org.unitego.lobecorp.world.entity.state.EntityStateHolder;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.entity.LcAttributes;
import org.unitego.lobecorp.registry.entity.LcEntityDataSerializers;
import org.unitego.lobecorp.registry.entity.state.TheQueenOfHatredStates;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// 憎恶皇后实体，持有实体状态并委托 Brain 与技能系统处理行为。
public class TheQueenOfHatred extends PathfinderMob implements GeoEntity, LcCustomAnimatable, EntityStateHolder {
	static final int SITTING_FADE_OUT_TICKS = 23;
	private static final EntityDataAccessor<List<EntityState>> DATA_ENTITY_STATES =
			SynchedEntityData.defineId(TheQueenOfHatred.class, LcEntityDataSerializers.ENTITY_STATES.get());
	private static final EntityDataAccessor<Boolean> DATA_PHASE_TWO =
			SynchedEntityData.defineId(TheQueenOfHatred.class, EntityDataSerializers.BOOLEAN);
	private static final String PHASE_TWO_SAVE_KEY = "PhaseTwo";
	private static final float PHASE_TWO_HEALTH_RATIO = 0.5F;
	/// 二阶段基础攻击属性提升 25%，不重复叠加，存档加载后重建。
	private static final double PHASE_TWO_ATTACK_DAMAGE_BONUS = 0.25;
	/// 二阶段攻击属性修饰符的稳定资源 ID。
	private static final Identifier PHASE_TWO_ATTACK_DAMAGE_ID = Lobecorp.id("queen_phase_two_attack_damage");
	private static final float SITTING_COLLISION_HEIGHT = 1.4F;
	private static final int LOCOMOTION_ANIMATION_TRANSITION_TICKS = 2;
	private final Set<UUID> attackers = new HashSet<>();
	private final TheQueenOfHatredAi ai = new TheQueenOfHatredAi();
	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
	private float skillLockedYRot;
	private float skillLockedYHeadRot;
	private float skillLockedYBodyRot;
	private boolean entityStatesInitialized;

	public TheQueenOfHatred(Level level) {
		this(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), level);
	}

	public TheQueenOfHatred(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		EntitySkillUtil.initialize(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 400.0)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(LcAttributes.DAMAGE_TAKEN_MULTIPLIER)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.FLYING_SPEED, 0.6)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(LcAttributes.ENTITY_SKILL_COOLDOWN_MULTIPLIER, 1.0);
	}

	public void cancelConductorSitting() {
		ai.cancelSitting(this);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ENTITY_STATES, List.of());
		builder.define(DATA_PHASE_TWO, false);
		entityStatesInitialized = true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof Player) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			updatePhase();
		}
		if (hurt && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
			attackers.add(attacker.getUUID());
			ai.onHurtBy(this, attacker);
		}
		return hurt;
	}

	public boolean isPhaseTwo() {
		return getEntityData().get(DATA_PHASE_TWO);
	}

	private void updatePhase() {
		if (!isPhaseTwo() && getHealth() <= getMaxHealth() * PHASE_TWO_HEALTH_RATIO) {
			getEntityData().set(DATA_PHASE_TWO, true);
		}
		var attackDamage = getAttribute(Attributes.ATTACK_DAMAGE);
		if (isPhaseTwo() && attackDamage != null && !attackDamage.hasModifier(PHASE_TWO_ATTACK_DAMAGE_ID)) {
			attackDamage.addTransientModifier(new AttributeModifier(PHASE_TWO_ATTACK_DAMAGE_ID,
					PHASE_TWO_ATTACK_DAMAGE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(PHASE_TWO_SAVE_KEY, isPhaseTwo());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		getEntityData().set(DATA_PHASE_TWO, input.read(PHASE_TWO_SAVE_KEY, Codec.BOOL).orElse(false));
		updatePhase();
	}

	public boolean isHatedTarget(LivingEntity target) {
		if (isAlliedTo(target)) {
			return false;
		}
		if (level() instanceof ServerLevel serverLevel
				&& ConductorData.get(serverLevel.getServer()).allied(getUUID(), target.getUUID())) {
			return false;
		}
		return target instanceof Enemy || attackers.contains(target.getUUID());
	}

	public void lockSkillFacing() {
		skillLockedYRot = getYRot();
		skillLockedYHeadRot = getYHeadRot();
		skillLockedYBodyRot = yBodyRot;
	}

	public void restoreSkillFacing() {
		EntityFacingUtil.turn(this, skillLockedYRot, skillLockedYHeadRot, skillLockedYBodyRot);
	}

	protected TheQueenOfHatredAi ai() {
		return ai;
	}

	@Override
	public List<EntityState> getEntityStates() {
		return getEntityData().get(DATA_ENTITY_STATES);
	}

	@Override
	public void setEntityStates(List<EntityState> states) {
		boolean hadSittingDimensions = hasSittingDimensions();
		getEntityData().set(DATA_ENTITY_STATES, List.copyOf(states), true);
		if (hadSittingDimensions != hasSittingDimensions()) {
			refreshDimensions();
		}
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ENTITY_STATES.equals(accessor)) {
			refreshDimensions();
		}
	}

	@Override
	protected EntityDimensions getDefaultDimensions(Pose pose) {
		EntityDimensions dimensions = super.getDefaultDimensions(pose);
		return hasSittingDimensions()
				? EntityDimensions.scalable(dimensions.width(), SITTING_COLLISION_HEIGHT)
				: dimensions;
	}

	protected boolean isSitting() {
		return entityStatesInitialized && (hasEntityState(TheQueenOfHatredStates.SITTING)
				|| hasEntityState(TheQueenOfHatredStates.SITTING_EDGE));
	}

	protected boolean isSittingEdge() {
		return entityStatesInitialized && (hasEntityState(TheQueenOfHatredStates.SITTING_EDGE)
				|| hasEntityState(TheQueenOfHatredStates.SITTING_EDGE_FADE_OUT));
	}

	protected boolean isSittingFadeOut() {
		return entityStatesInitialized && (hasEntityState(TheQueenOfHatredStates.SITTING_FADE_OUT)
				|| hasEntityState(TheQueenOfHatredStates.SITTING_EDGE_FADE_OUT));
	}

	protected boolean isSittingOrFadingOut() {
		return isSitting() || isSittingFadeOut();
	}

	protected void applySittingEdgeFacing(float yaw) {
		setYRot(yaw);
		yBodyRot = yaw;
	}

	private boolean hasSittingDimensions() {
		return isSittingOrFadingOut();
	}

	@Override
	public void travel(Vec3 travelVector) {
		if (isSittingOrFadingOut() || EntitySkillUtil.isMovementLocked(this)) {
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		super.travel(travelVector);
	}

	@Override
	protected void tickHeadTurn(float yBodyRotT) {
		if (EntitySkillUtil.isMovementLocked(this)) {
			EntityFacingUtil.turn(this, getYRot());
			return;
		}
		if (!isSittingEdge()) {
			super.tickHeadTurn(yBodyRotT);
			return;
		}
		if (!level().isClientSide()) {
			setYRot(ai.sittingEdgeFacingYaw());
		}
		setYBodyRot(getYRot());
		clampHeadRotationToBody();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		updatePhase();
		getBrain().tick(level, this);
		ai.updateActivity(this);
		ai.tick(this);
		super.customServerAiStep(level);
		if (EntitySkillUtil.isMovementLocked(this)) {
			restoreSkillFacing();
		}
		if (ai.sittingEdgeFacingLocked()) {
			getMoveControl().setWait();
			ai.applySittingEdgeFacing(this);
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new LcAnimationControllerBuilder<TheQueenOfHatred>("locomotion",
				LOCOMOTION_ANIMATION_TRANSITION_TICKS, state -> {
			if (isSittingFadeOut()) {
				return PlayState.STOP;
			}
			boolean cancelledSittingAnimation = !isSitting()
					&& (state.controller().getCurrentRawAnimation() == TheQueenOfHatredAnim.SIT_SEQUENCE.getAnimation()
					|| state.controller().getCurrentRawAnimation() == TheQueenOfHatredAnim.SIT_SEQUENCE_2.getAnimation());
			state.controller().setTransitionTicks(
					cancelledSittingAnimation ? 0 : LOCOMOTION_ANIMATION_TRANSITION_TICKS);
			if (isSitting()) {
				state.setControllerSpeed(1);
				return state.setAndContinue((isSittingEdge()
						? TheQueenOfHatredAnim.SIT_SEQUENCE_2
						: TheQueenOfHatredAnim.SIT_SEQUENCE).getAnimation());
			}
			if (!walkAnimation.isMoving() || !state.isMoving()) {
				state.setControllerSpeed(1);
				return state.setAndContinue(TheQueenOfHatredAnim.IDLE.getAnimation());
			}

			float speed = (float) (getAttributeBaseValue(Attributes.MOVEMENT_SPEED) + walkAnimation.speed());
			state.setControllerSpeed(0.5f + speed);
			return state.setAndContinue(TheQueenOfHatredAnim.WALK.getAnimation());
		}).blendType(LcControllerBlendType.ADDITIVE)
				.fadeOutTicks(SITTING_FADE_OUT_TICKS)
				.rotationTransitionMode(LcRotationTransitionMode.SHORTEST_PATH)
				.build());

		controllers.add(new LcAnimationControllerBuilder<TheQueenOfHatred>("eyes", 1, state -> {
			if (tickCount % (20 * 5) == 0) {
				return PlayState.STOP;
			}
			return state.setAndContinue(TheQueenOfHatredAnim.EYES.getAnimation());
		}).blendType(LcControllerBlendType.MASK)
				.fadeOutTicks(0)
				.fadeInTicks(0)
				.fadeInTransitionMode(LcTransitionMode.SEQUENTIAL)
				.fadeOutTransitionMode(LcTransitionMode.SEQUENTIAL)
				.rotationTransitionMode(LcRotationTransitionMode.SHORTEST_PATH)
				.build());

		controllers.add(new LcAnimationControllerBuilder<TheQueenOfHatred>("action", 2, state -> PlayState.STOP)
				.blendType(LcControllerBlendType.OVERRIDE)
				.rotationTransitionMode(LcRotationTransitionMode.SHORTEST_PATH)
				.triggerableAnim(TheQueenOfHatredAnim.DISPEL.name(), TheQueenOfHatredAnim.DISPEL.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.SWEEP.name(), TheQueenOfHatredAnim.SWEEP.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.SPIN.name(), TheQueenOfHatredAnim.SPIN.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.AIM.name(), TheQueenOfHatredAnim.AIM.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.AIM_2.name(), TheQueenOfHatredAnim.AIM_2.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.BLINK.name(), TheQueenOfHatredAnim.BLINK.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.TELEPORT.name(), TheQueenOfHatredAnim.TELEPORT.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.KISS.name(), TheQueenOfHatredAnim.KISS.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.SPELL.name(), TheQueenOfHatredAnim.SPELL.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.ATTACK.name(), TheQueenOfHatredAnim.ATTACK.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.ATTACK_2.name(), TheQueenOfHatredAnim.ATTACK_2.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.REST.name(), TheQueenOfHatredAnim.REST.getAnimation())
				.triggerableAnim(TheQueenOfHatredAnim.TOSS.name(), TheQueenOfHatredAnim.TOSS.getAnimation())
				.build());
	}

	public void playActionAnimation(TheQueenOfHatredAnim animation) {
		cancelConductorSitting();
		playCustomAnimation("action", animation.name());
	}

	public void stopActionAnimation() {
		stopCustomAnimation("action", null);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return animatableInstanceCache;
	}

	@Override
	protected Brain<TheQueenOfHatred> makeBrain(Brain.Packed packedBrain) {
		return TheQueenOfHatredAi.makeBrain(this, packedBrain);
	}

	@Override
	public Brain<TheQueenOfHatred> getBrain() {
		return (Brain<TheQueenOfHatred>) super.getBrain();
	}
}
