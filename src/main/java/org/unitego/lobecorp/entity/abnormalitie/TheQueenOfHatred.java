package org.unitego.lobecorp.entity.abnormalitie;

import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.unitego.lobecorp.animation.*;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity.LcAttributes;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.entity_skill.TheQueenOfHatredSkills;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim.*;

public class TheQueenOfHatred extends PathfinderMob implements LcGeoEntity {
	private final Set<UUID> attackers = new HashSet<>();
	private float skillLockedYRot;
	private float skillLockedYHeadRot;
	private float skillLockedYBodyRot;
	private long nextSkillCastGameTime;
	private final AnimatableInstanceCache animatableInstanceCache = GeckoLibUtil.createInstanceCache(this);
	private final LcAnimationController<TheQueenOfHatred> animationController =
			new LcAnimationController<>(this::registerLcAnimationLayers);

	public TheQueenOfHatred(Level level) {
		this(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get(), level);
	}

	public TheQueenOfHatred(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		EntitySkillManager.addSkill(this, TheQueenOfHatredSkills.REPEL.get());
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
			getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
			getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
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

	boolean canCastSkillNow() {
		return level().getGameTime() >= nextSkillCastGameTime;
	}

	public void delayNextSkillCast(int delayTicks) {
		nextSkillCastGameTime = level().getGameTime() + delayTicks;
	}

	@Override
	public void travel(Vec3 travelVector) {
		if (EntitySkillManager.isMovementLocked(this)) {
			setDeltaMovement(Vec3.ZERO);
			return;
		}
		super.travel(travelVector);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		getBrain().tick(level, this);
		TheQueenOfHatredAi.updateActivity(this);
		super.customServerAiStep(level);
		if (EntitySkillManager.isMovementLocked(this)) {
			restoreSkillFacing();
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		animationController.suppressLayerOutputWhilePlaying(
				LOCOMOTION_ANIMATION_LAYER, IDLE_POSE_ANIMATION_LAYER);
		animationController.suppressLayerOutputWhilePlaying(
				LOCOMOTION_ANIMATION_LAYER, ACTION_ANIMATION_LAYER);
		animationController.suppressLayerOutputWhilePlaying(
				IDLE_POSE_ANIMATION_LAYER, ACTION_ANIMATION_LAYER);
		animationController.setAnimationStateHandler(LOCOMOTION_ANIMATION_LAYER,
				test -> {
					float speed = walkAnimation.speed();
					boolean wasWalking = animationController.currentAnimation(LOCOMOTION_ANIMATION_LAYER)
							== TheQueenOfHatredAnim.WALK.getAnimation();
					TheQueenOfHatredAnim animation = speed > WALK_ANIMATION_SPEED_THRESHOLD
							|| wasWalking && speed > WALK_ANIMATION_EXIT_SPEED_THRESHOLD
							? TheQueenOfHatredAnim.WALK : TheQueenOfHatredAnim.IDLE;
					return test.setAndContinue(animation.getAnimation());
				});
		animationController.triggerableAnim(ACTION_ANIMATION_LAYER,
					TheQueenOfHatredAnim.DISPEL.name(), TheQueenOfHatredAnim.DISPEL.getAnimation());
		controllers.add(animationController);
	}

	@Override
	public void registerLcAnimationLayers(LcAnimationLayerRegistrar registrar) {
		registrar.add(new LcLayerDefinition(LOCOMOTION_ANIMATION_LAYER, LcBlendMode.OVERRIDE,
				FULL_BODY_ANIMATION_MASK, ANIMATION_TRANSITION_TICKS));
		registrar.add(new LcLayerDefinition(IDLE_POSE_ANIMATION_LAYER, LcBlendMode.OVERRIDE,
				FULL_BODY_ANIMATION_MASK, ANIMATION_TRANSITION_TICKS));
		registrar.add(new LcLayerDefinition(ACTION_ANIMATION_LAYER, LcBlendMode.OVERRIDE,
				FULL_BODY_ANIMATION_MASK, ANIMATION_TRANSITION_TICKS));
	}

	public void triggerActionAnimation(TheQueenOfHatredAnim animation) {
		if (!level().isClientSide()) {
			triggerAnimation(LcAnimationController.CONTROLLER_NAME, animation.name());
		}
	}

	public void stopTriggeredActionAnimation() {
		if (!level().isClientSide()) {
			stopTriggeredAnimation(LcAnimationController.CONTROLLER_NAME, null);
		}
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
