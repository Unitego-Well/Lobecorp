package org.unitego.lobecorp.entity.abnormalitie;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.unitego.lobecorp.animation.LcAnimationControllerBuilder;
import org.unitego.lobecorp.animation.LcControllerBlendType;
import org.unitego.lobecorp.animation.LcCustomAnimatable;
import org.unitego.lobecorp.animation.LcRotationTransitionMode;
import org.unitego.lobecorp.entity.entity_state.EntityState;
import org.unitego.lobecorp.entity.entity_state.EntityStateHolder;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.entity.LcAttributes;
import org.unitego.lobecorp.registry.entity.LcEntityDataSerializers;
import org.unitego.lobecorp.registry.entity_skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.registry.entity_state.TheQueenOfHatredStates;

import java.util.*;

public class TheQueenOfHatred extends PathfinderMob implements GeoEntity, LcCustomAnimatable, EntityStateHolder {
	private record SittingEdgeApproachTarget(Vec3 position, float facingYaw) {
	}

	enum SittingGround {
		UNSAFE,
		FLAT,
		EDGE
	}

	private static final EntityDataAccessor<List<EntityState>> DATA_ENTITY_STATES =
			SynchedEntityData.defineId(TheQueenOfHatred.class, LcEntityDataSerializers.ENTITY_STATES.get());
	private static final float SITTING_COLLISION_HEIGHT = 1.4F;
	private static final double GROUND_CHECK_EPSILON = 1.0E-5D;
	private static final double SITTING_EDGE_OVERHANG = 0.1D;
	private final Set<UUID> attackers = new HashSet<>();
	private float skillLockedYRot;
	private float skillLockedYHeadRot;
	private float skillLockedYBodyRot;
	private long nextSkillCastGameTime;
	private long nextIdleStrollGameTime;
	private long nextIdleActionGameTime;
	private long sittingEndGameTime;
	private long sittingFadeOutEndGameTime;
	private BlockPos sittingEdgePathTarget;
	private Vec3 sittingEdgeApproachTarget;
	private float sittingEdgeApproachSpeedModifier;
	private float sittingEdgeFacingYaw;
	private boolean hasSittingEdgeFacingYaw;
	private boolean sittingEdgeFacingLocked;
	private boolean sittingEdgeApproachingDirectly;
	private boolean entityStatesInitialized;
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

	boolean canStartIdleStroll() {
		return level().getGameTime() >= nextIdleStrollGameTime;
	}

	void delayNextIdleStroll(int delayTicks) {
		nextIdleStrollGameTime = level().getGameTime() + delayTicks;
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

	private boolean hasSittingDimensions() {
		return isSittingOrFadingOut();
	}

	SittingGround getSittingGround() {
		if (!onGround() || isOnFire()) {
			return SittingGround.UNSAFE;
		}
		return getSittingGround(getBoundingBox());
	}

	private SittingGround getSittingGround(AABB bounds) {
		int minX = Mth.floor(bounds.minX + GROUND_CHECK_EPSILON);
		int maxX = Mth.floor(bounds.maxX - GROUND_CHECK_EPSILON);
		int minZ = Mth.floor(bounds.minZ + GROUND_CHECK_EPSILON);
		int maxZ = Mth.floor(bounds.maxZ - GROUND_CHECK_EPSILON);
		int supportY = Mth.floor(bounds.minY - GROUND_CHECK_EPSILON);
		double supportTop = Double.NaN;
		int supportedBlocks = 0;
		int unsupportedBlocks = 0;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				BlockPos supportPos = new BlockPos(x, supportY, z);
				BlockState supportState = level().getBlockState(supportPos);
				if (!supportState.getFluidState().isEmpty()
						|| supportState.is(BlockTags.FIRE)
						|| supportState.is(Blocks.LAVA)) {
					return SittingGround.UNSAFE;
				}
				VoxelShape shape = supportState.getCollisionShape(level(), supportPos);
				if (!supportState.isFaceSturdy(level(), supportPos, Direction.UP)) {
					if (!shape.isEmpty()) {
						return SittingGround.UNSAFE;
					}
					unsupportedBlocks++;
					continue;
				}
				double top = supportPos.getY() + shape.max(Direction.Axis.Y);
				if (Double.isNaN(supportTop)) {
					supportTop = top;
				} else if (Math.abs(supportTop - top) > GROUND_CHECK_EPSILON) {
					return SittingGround.UNSAFE;
				}
				supportedBlocks++;
			}
		}
		if (supportedBlocks == 0 || Math.abs(supportTop - bounds.minY) > GROUND_CHECK_EPSILON) {
			return SittingGround.UNSAFE;
		}
		for (BlockPos blockPos : BlockPos.betweenClosed(
				Mth.floor(bounds.minX), Mth.floor(bounds.minY), Mth.floor(bounds.minZ),
				Mth.floor(bounds.maxX - GROUND_CHECK_EPSILON),
				Mth.floor(bounds.maxY - GROUND_CHECK_EPSILON),
				Mth.floor(bounds.maxZ - GROUND_CHECK_EPSILON))) {
			BlockState state = level().getBlockState(blockPos);
			if (!state.getFluidState().isEmpty() || state.is(BlockTags.FIRE) || state.is(Blocks.LAVA)) {
				return SittingGround.UNSAFE;
			}
		}
		return unsupportedBlocks == 0 ? SittingGround.FLAT : SittingGround.EDGE;
	}

	private float getSittingEdgeFacingYaw() {
		AABB bounds = getBoundingBox();
		int minX = Mth.floor(bounds.minX + GROUND_CHECK_EPSILON);
		int maxX = Mth.floor(bounds.maxX - GROUND_CHECK_EPSILON);
		int minZ = Mth.floor(bounds.minZ + GROUND_CHECK_EPSILON);
		int maxZ = Mth.floor(bounds.maxZ - GROUND_CHECK_EPSILON);
		int supportY = Mth.floor(bounds.minY - GROUND_CHECK_EPSILON);
		double edgeDirectionX = 0.0D;
		double edgeDirectionZ = 0.0D;
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				BlockPos supportPos = new BlockPos(x, supportY, z);
				BlockState supportState = level().getBlockState(supportPos);
				if (supportState.getCollisionShape(level(), supportPos).isEmpty()) {
					edgeDirectionX += x + 0.5D - getX();
					edgeDirectionZ += z + 0.5D - getZ();
				}
			}
		}
		return edgeDirectionX == 0.0D && edgeDirectionZ == 0.0D
				? getYRot()
				: Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-edgeDirectionX, edgeDirectionZ)));
	}

	private void applySittingEdgeFacing() {
		setYRot(sittingEdgeFacingYaw);
		yBodyRot = sittingEdgeFacingYaw;
	}

	boolean isApproachingSittingEdge() {
		return sittingEdgeApproachTarget != null;
	}

	boolean tryApproachNearbySittingEdge(int searchRadius, float speedModifier) {
		AABB bounds = getBoundingBox();
		int supportY = Mth.floor(bounds.minY - GROUND_CHECK_EPSILON);
		double supportTop = bounds.minY;
		Map<BlockPos, SittingEdgeApproachTarget> edgeApproachTargets = new HashMap<>();
		for (int x = blockPosition().getX() - searchRadius; x <= blockPosition().getX() + searchRadius; x++) {
			for (int z = blockPosition().getZ() - searchRadius; z <= blockPosition().getZ() + searchRadius; z++) {
				double deltaX = getX() - (x + 0.5D);
				double deltaZ = getZ() - (z + 0.5D);
				if (deltaX * deltaX + deltaZ * deltaZ > searchRadius * searchRadius) {
					continue;
				}
				BlockPos supportPos = new BlockPos(x, supportY, z);
				BlockState supportState = level().getBlockState(supportPos);
				if (!supportState.isFaceSturdy(level(), supportPos, Direction.UP)
						|| !supportState.getFluidState().isEmpty()
						|| supportState.is(BlockTags.FIRE)
						|| supportState.is(Blocks.LAVA)
						|| Math.abs(supportPos.getY() + supportState.getCollisionShape(level(), supportPos)
						.max(Direction.Axis.Y) - supportTop) > GROUND_CHECK_EPSILON) {
					continue;
				}
				for (Direction direction : Direction.Plane.HORIZONTAL) {
					BlockPos neighborPos = supportPos.relative(direction);
					BlockState neighborState = level().getBlockState(neighborPos);
					if (!neighborState.getCollisionShape(level(), neighborPos).isEmpty()
							|| !neighborState.getFluidState().isEmpty()
							|| neighborState.is(BlockTags.FIRE)
							|| neighborState.is(Blocks.LAVA)) {
						continue;
					}
					double edgeX = x + 0.5D + direction.getStepX()
							* (0.5D - getBbWidth() * 0.5D + SITTING_EDGE_OVERHANG);
					double edgeZ = z + 0.5D + direction.getStepZ()
							* (0.5D - getBbWidth() * 0.5D + SITTING_EDGE_OVERHANG);
					AABB edgeBounds = bounds.move(edgeX - getX(), 0.0D, edgeZ - getZ());
					if (getSittingGround(edgeBounds) != SittingGround.EDGE || !level().noCollision(this, edgeBounds)) {
						continue;
					}
					BlockPos pathTarget = supportPos.above(Mth.floor(supportTop) - supportY);
					float facingYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(
							-direction.getStepX(), direction.getStepZ())));
					edgeApproachTargets.put(pathTarget,
							new SittingEdgeApproachTarget(new Vec3(edgeX, getY(), edgeZ), facingYaw));
				}
			}
		}
		if (edgeApproachTargets.isEmpty()) {
			return false;
		}
		Path path = getNavigation().createPath(edgeApproachTargets.keySet(), 0);
		if (path == null || !path.canReach()) {
			return false;
		}
		sittingEdgePathTarget = path.getTarget();
		SittingEdgeApproachTarget approachTarget = edgeApproachTargets.get(sittingEdgePathTarget);
		if (approachTarget == null) {
			sittingEdgePathTarget = null;
			return false;
		}
		sittingEdgeApproachTarget = approachTarget.position();
		sittingEdgeFacingYaw = approachTarget.facingYaw();
		hasSittingEdgeFacingYaw = true;
		sittingEdgeApproachSpeedModifier = speedModifier;
		getBrain().setMemory(MemoryModuleType.WALK_TARGET,
				new WalkTarget(sittingEdgePathTarget, speedModifier, 0));
		return true;
	}

	boolean tickSittingEdgeApproach() {
		if (sittingEdgeApproachTarget == null) {
			return false;
		}
		if (!sittingEdgeApproachingDirectly) {
			if (!getNavigation().isDone()) {
				return true;
			}
			Vec3 pathTargetPosition = Vec3.atBottomCenterOf(sittingEdgePathTarget);
			if (position().distanceToSqr(pathTargetPosition) > 0.25D) {
				if (getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
					return true;
				}
				cancelSittingEdgeApproach();
				return false;
			}
			getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			getNavigation().stop();
			sittingEdgeApproachingDirectly = true;
		}
		if (!onGround()) {
			cancelSittingEdgeApproach();
			return false;
		}
		getMoveControl().setWantedPosition(sittingEdgeApproachTarget.x, getY(), sittingEdgeApproachTarget.z,
				sittingEdgeApproachSpeedModifier);
		return true;
	}

	void cancelSittingEdgeApproach() {
		if (!isApproachingSittingEdge()) {
			return;
		}
		sittingEdgePathTarget = null;
		sittingEdgeApproachTarget = null;
		sittingEdgeApproachSpeedModifier = 0.0F;
		sittingEdgeApproachingDirectly = false;
		if (!sittingEdgeFacingLocked) {
			hasSittingEdgeFacingYaw = false;
		}
		getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		getNavigation().stop();
		getMoveControl().setWait();
	}

	void startSitting(int durationTicks, boolean onEdge) {
		boolean hasApproachFacingYaw = hasSittingEdgeFacingYaw;
		float approachFacingYaw = sittingEdgeFacingYaw;
		cancelSittingEdgeApproach();
		getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		getNavigation().stop();
		setDeltaMovement(Vec3.ZERO);
		sittingEndGameTime = level().getGameTime() + durationTicks;
		if (onEdge) {
			sittingEdgeFacingYaw = hasApproachFacingYaw ? approachFacingYaw : getSittingEdgeFacingYaw();
			hasSittingEdgeFacingYaw = true;
			sittingEdgeFacingLocked = true;
			getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
			applySittingEdgeFacing();
			addEntityState(TheQueenOfHatredStates.SITTING_EDGE);
		} else {
			sittingEdgeFacingLocked = false;
			hasSittingEdgeFacingYaw = false;
			addEntityState(TheQueenOfHatredStates.SITTING);
		}
	}

	boolean tickSitting(long gameTime, int fadeOutTicks) {
		if (isSitting() && gameTime >= sittingEndGameTime) {
			addEntityState(isSittingEdge()
					? TheQueenOfHatredStates.SITTING_EDGE_FADE_OUT
					: TheQueenOfHatredStates.SITTING_FADE_OUT);
			sittingFadeOutEndGameTime = gameTime + fadeOutTicks;
			return false;
		}
		if (isSittingFadeOut() && gameTime >= sittingFadeOutEndGameTime) {
			removeEntityState(TheQueenOfHatredStates.SITTING_FADE_OUT);
			removeEntityState(TheQueenOfHatredStates.SITTING_EDGE_FADE_OUT);
			sittingEndGameTime = 0;
			sittingFadeOutEndGameTime = 0;
			sittingEdgeFacingLocked = false;
			hasSittingEdgeFacingYaw = false;
			return true;
		}
		return false;
	}

	void cancelSitting() {
		if (!isSittingOrFadingOut()) {
			return;
		}
		removeEntityState(TheQueenOfHatredStates.SITTING);
		removeEntityState(TheQueenOfHatredStates.SITTING_EDGE);
		removeEntityState(TheQueenOfHatredStates.SITTING_FADE_OUT);
		removeEntityState(TheQueenOfHatredStates.SITTING_EDGE_FADE_OUT);
		sittingEndGameTime = 0;
		sittingFadeOutEndGameTime = 0;
		sittingEdgeFacingLocked = false;
		hasSittingEdgeFacingYaw = false;
		nextIdleActionGameTime = 0;
	}

	long nextIdleActionGameTime() {
		return nextIdleActionGameTime;
	}

	void scheduleNextIdleAction(int delayTicks) {
		nextIdleActionGameTime = level().getGameTime() + delayTicks;
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
			setYRot(sittingEdgeFacingYaw);
		}
		setYBodyRot(getYRot());
		clampHeadRotationToBody();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		getBrain().tick(level, this);
		TheQueenOfHatredAi.updateActivity(this);
		TheQueenOfHatredAi.tick(this);
		super.customServerAiStep(level);
		if (EntitySkillManager.isMovementLocked(this)) {
			restoreSkillFacing();
		}
		if (sittingEdgeFacingLocked) {
			getMoveControl().setWait();
			applySittingEdgeFacing();
		}
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new LcAnimationControllerBuilder<TheQueenOfHatred>("locomotion", 2, state -> {
			if (isSittingFadeOut()) {
				return PlayState.STOP;
			}
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
				.fadeOutTicks(TheQueenOfHatredAi.SITTING_FADE_OUT_TICKS)
				.rotationTransitionMode(LcRotationTransitionMode.SHORTEST_PATH)
				.build());

		controllers.add(new LcAnimationControllerBuilder<TheQueenOfHatred>("eyes", 1, state -> {
			if (tickCount % (20 * 2) != 0) {
				return PlayState.STOP;
			}
			return state.setAndContinue(TheQueenOfHatredAnim.EYES.getAnimation());
		}).build());

		controllers.add(new LcAnimationControllerBuilder<TheQueenOfHatred>("action", 2, state -> PlayState.STOP)
				.blendType(LcControllerBlendType.OVERRIDE)
				.rotationTransitionMode(LcRotationTransitionMode.SHORTEST_PATH)
				.triggerableAnim(TheQueenOfHatredAnim.DISPEL.name(), TheQueenOfHatredAnim.DISPEL.getAnimation())
				.build());
	}

	public void playActionAnimation(TheQueenOfHatredAnim animation) {
		if (level().isClientSide()) {
			triggerAnim("action", animation.name());
		}
	}

	public void stopActionAnimation() {
		if (level().isClientSide()) {
			stopTriggeredAnim("action", null);
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
