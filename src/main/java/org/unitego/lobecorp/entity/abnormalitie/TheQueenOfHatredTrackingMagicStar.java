package org.unitego.lobecorp.entity.abnormalitie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.hitbox.EllipsoidSize;
import org.unitego.lobecorp.hitbox.HitboxHitMode;
import org.unitego.lobecorp.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxLineOfSightMode;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxPurpose;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 转圈技能发射的弱追踪魔法星星。
public class TheQueenOfHatredTrackingMagicStar extends TheQueenOfHatredMagicStar {
	/// 追踪判断椭球的基础半径。
	private static final double SEARCH_RADIUS = 10.0;
	/// 每 tick 最大转向角度。
	private static final double MAXIMUM_TURN_DEGREES = 10.0;
	/// 搜索判断框重新检查目标的间隔 tick。
	private static final int TARGET_CHECK_INTERVAL_TICKS = 5;
	/// 追踪目标搜索判断框。
	private static final HitboxTemplate SEARCH_TEMPLATE = new HitboxTemplate(
			new EllipsoidSize(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof TheQueenOfHatredTrackingMagicStar star)) {
					return false;
				}
				if (!(context.target() instanceof LivingEntity target)) {
					return false;
				}
				return star.selectTarget(target);
			}, HitboxPurpose.DETECTION);

	@Nullable
	private LivingEntity trackedTarget;
	private int searchHitboxId = -1;

	/// 创建由实体注册器或客户端同步数据生成的追踪星星。
	/// @param type 追踪星星的实体类型
	/// @param level 星星所在世界
	public TheQueenOfHatredTrackingMagicStar(EntityType<? extends TheQueenOfHatredTrackingMagicStar> type,
			Level level) {
		super(type, level);
	}

	/// 创建由憎恶皇后发射的追踪星星。
	/// @param level 星星所在服务端世界
	/// @param owner 发射星星的憎恶皇后
	public TheQueenOfHatredTrackingMagicStar(ServerLevel level, TheQueenOfHatred owner) {
		super(AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED_TRACKING_MAGIC_STAR.get(), level);
		setOwner(owner);
	}

	@Override
	public void tick() {
		if (level() instanceof ServerLevel level) {
			ensureSearchHitbox(level);
			updateSearchHitbox(level);
			applyHoming();
		}
		super.tick();
	}

	private void updateSearchHitbox(ServerLevel level) {
		HitboxInstance hitbox = HitboxManager.get(level, searchHitboxId);
		if (hitbox == null) {
			return;
		}
		double predictionDistance = getDeltaMovement().length();
		hitbox.setSize(new EllipsoidSize(SEARCH_RADIUS, SEARCH_RADIUS,
				SEARCH_RADIUS + predictionDistance));
		if (tickCount % TARGET_CHECK_INTERVAL_TICKS == 0) {
			hitbox.activate();
		} else {
			hitbox.deactivate();
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel level && searchHitboxId >= 0) {
			HitboxManager.remove(level, searchHitboxId);
		}
		super.remove(reason);
	}

	private void ensureSearchHitbox(ServerLevel level) {
		if (searchHitboxId >= 0) {
			return;
		}
		HitboxInstance hitbox = HitboxManager.create(SEARCH_TEMPLATE, level, position(), 3 * TICKS_PER_SECOND);
		hitbox.follow(this, Vec3.ZERO, true);
		hitbox.appendTargetFilter(target -> getOwner() instanceof TheQueenOfHatred queen
				&& queen.isValidTarget(target));
		hitbox.setLineOfSightMode(HitboxLineOfSightMode.CENTER_TO_ENTITY);
		hitbox.setHitPolicy(new HitboxHitPolicy(HitboxHitMode.EVERY_TICK, 0, -1, -1));
		searchHitboxId = hitbox.id();
	}

	private boolean selectTarget(LivingEntity target) {
		if (isTrackedTargetValid()) {
			return false;
		}
		trackedTarget = target;
		return true;
	}

	private void applyHoming() {
		Vec3 movement = getDeltaMovement();
		double speed = movement.length();
		if (speed <= 0.0) {
			return;
		}
		LivingEntity target = trackedTarget;
		if (target == null || !isTrackedTargetValid()) {
			trackedTarget = null;
			setDeltaMovement(movement.normalize().scale(speed));
			return;
		}
		Vec3 current = movement.normalize();
		Vec3 targetPosition = getOwner() instanceof TheQueenOfHatred queen
				? queen.predictTargetPosition(target, position(), speed)
				: target.getBoundingBox().getCenter();
		Vec3 desired = targetPosition.subtract(position()).normalize();
		double angle = Math.acos(Mth.clamp(current.dot(desired), -1.0, 1.0));
		double maximumTurn = Math.toRadians(MAXIMUM_TURN_DEGREES);
		double blend = angle <= maximumTurn ? 1.0 : maximumTurn / angle;
		Vec3 direction = current.scale(1.0 - blend).add(desired.scale(blend)).normalize();
		setDeltaMovement(direction.scale(speed));
	}

	private boolean isTrackedTargetValid() {
		return trackedTarget != null && getOwner() instanceof TheQueenOfHatred queen
				&& queen.isValidTarget(trackedTarget);
	}
}
