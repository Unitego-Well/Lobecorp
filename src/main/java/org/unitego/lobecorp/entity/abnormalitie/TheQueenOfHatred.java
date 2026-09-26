package org.unitego.lobecorp.entity.abnormalitie;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.animation.*;
import org.unitego.lobecorp.entity.entity_state.EntityState;
import org.unitego.lobecorp.entity.entity_state.EntityStateHolder;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.entity.LcAttributes;
import org.unitego.lobecorp.registry.entity.LcEntityDataSerializers;
import org.unitego.lobecorp.registry.entity_skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.registry.entity_state.TheQueenOfHatredStates;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TheQueenOfHatred extends PathfinderMob implements GeoEntity, LcCustomAnimatable, EntityStateHolder {
	private static final EntityDataAccessor<List<EntityState>> DATA_ENTITY_STATES =
			SynchedEntityData.defineId(TheQueenOfHatred.class, LcEntityDataSerializers.ENTITY_STATES.get());
	private static final float SITTING_COLLISION_HEIGHT = 1.4F;
	private static final int LOCOMOTION_ANIMATION_TRANSITION_TICKS = 2;
	static final int SITTING_FADE_OUT_TICKS = 23;
	private final Set<UUID> attackers = new HashSet<>();
	private float skillLockedYRot;
	private float skillLockedYHeadRot;
	private float skillLockedYBodyRot;
	private long nextSkillCastGameTime;
	private boolean entityStatesInitialized;
	private final TheQueenOfHatredAi ai = new TheQueenOfHatredAi();
	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);

	public TheQueenOfHatred(Level level) {
		this(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), level);
	}

	public TheQueenOfHatred(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		EntitySkillManager.addSkill(this, TheQueenOfHatredSkills.REPEL.get());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ENTITY_STATES, List.of());
		entityStatesInitialized = true;
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

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof Player) {
			return false;
		}
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof LivingEntity attacker && attacker != this) {
			attackers.add(attacker.getUUID());
			ai.onHurtBy(this, attacker);
		}
		return hurt;
	}

	public boolean isHatedTarget(LivingEntity target) {
		return target instanceof Enemy || attackers.contains(target.getUUID());
	}

	public void lockSkillFacing() {
		skillLockedYRot = getYRot();
		skillLockedYHeadRot = getYHeadRot();
		skillLockedYBodyRot = yBodyRot;
	}

	public void restoreSkillFacing() {
		setYRot(skillLockedYRot);
		setYHeadRot(skillLockedYHeadRot);
		yBodyRot = skillLockedYBodyRot;
	}

	long skillCastAvailableAfterGameTime() {
		return nextSkillCastGameTime;
	}

	public void delayNextSkillCast(int delayTicks) {
		nextSkillCastGameTime = level().getGameTime() + delayTicks;
	}

	TheQueenOfHatredAi ai() {
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

	boolean isSitting() {
		return entityStatesInitialized && (hasEntityState(TheQueenOfHatredStates.SITTING)
				|| hasEntityState(TheQueenOfHatredStates.SITTING_EDGE));
	}

	boolean isSittingEdge() {
		return entityStatesInitialized && (hasEntityState(TheQueenOfHatredStates.SITTING_EDGE)
				|| hasEntityState(TheQueenOfHatredStates.SITTING_EDGE_FADE_OUT));
	}

	boolean isSittingFadeOut() {
		return entityStatesInitialized && (hasEntityState(TheQueenOfHatredStates.SITTING_FADE_OUT)
				|| hasEntityState(TheQueenOfHatredStates.SITTING_EDGE_FADE_OUT));
	}

	boolean isSittingOrFadingOut() {
		return isSitting() || isSittingFadeOut();
	}

	void applySittingEdgeFacing(float yaw) {
		setYRot(yaw);
		yBodyRot = yaw;
	}

	private boolean hasSittingDimensions() {
		return isSittingOrFadingOut();
	}

	@Override
	public void travel(Vec3 travelVector) {
		if (isSittingOrFadingOut() || EntitySkillManager.isMovementLocked(this)) {
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		super.travel(travelVector);
	}

	@Override
	protected void tickHeadTurn(float yBodyRotT) {
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
		getBrain().tick(level, this);
		ai.updateActivity(this);
		ai.tick(this);
		super.customServerAiStep(level);
		if (EntitySkillManager.isMovementLocked(this)) {
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
				.build());
	}

	public void playActionAnimation(TheQueenOfHatredAnim animation) {
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
