package org.unitego.lobecorp.entity_skill.skill.abnormalitie.the_queen_of_hatred;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.projectile.MagicStarProjectile;
import org.unitego.lobecorp.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity_skill.TargetedEntitySkill;
import org.unitego.lobecorp.hitbox.*;
import org.unitego.lobecorp.registry.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.util.TypedDataKey;

/// 憎恶女皇向前横扫并发射扇形星星。
public class SweepSkill extends TargetedEntitySkill<TheQueenOfHatred> {
	public static final double MELEE_RANGE = 4.0;
	private static final double HALF_SWEEP_ANGLE_DEGREES = 45.0;
	private static final double MELEE_HEIGHT = 2.0;
	private static final double MELEE_KNOCKBACK = 1.5;
	private static final int PHASE_ONE_STAR_COUNT = 3;
	private static final int PHASE_TWO_STAR_COUNT = 6;
	private static final double STAR_SPAWN_OFFSET = 0.8;
	private static final double STAR_SPAWN_HEIGHT = 1.2;

	private static final TypedDataKey<Integer> HITBOX_ID = TypedDataKey.create();
	private static final HitboxTemplate HITBOX = new HitboxTemplate(
			new SectorCylinderSize(MELEE_RANGE, MELEE_HEIGHT * 2.0, HALF_SWEEP_ANGLE_DEGREES * 2.0),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof TheQueenOfHatred queen)
						|| !(context.target() instanceof LivingEntity target) || !queen.isHatedTarget(target) || !queen.hasLineOfSight(target)
						|| Math.abs(target.getBoundingBox().getCenter().y - queen.getBoundingBox().getCenter().y) > MELEE_HEIGHT)
					return false;
				float damage = (float) queen.getAttributeValue(Attributes.ATTACK_DAMAGE);
				if (!target.hurtServer(context.level(), queen.damageSources().mobAttack(queen), damage)) return false;
				Vec3 offset = target.position().subtract(queen.position()).multiply(1.0, 0.0, 1.0);
				Vec3 direction = offset.lengthSqr() > 0.0 ? offset.normalize() : Vec3.directionFromRotation(0.0F, queen.getYRot());
				target.setDeltaMovement(target.getDeltaMovement().add(direction.scale(MELEE_KNOCKBACK)));
				target.hurtMarked = true;
				return true;
			});

	public SweepSkill(Properties properties) {
		super(properties);
	}

	public static double conductorAngle() {
		return HALF_SWEEP_ANGLE_DEGREES * 2.0;
	}

	private static void removeHitbox(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Integer id = runtime.removeData(HITBOX_ID);
		if (id != null && queen.level() instanceof ServerLevel level) HitboxManager.remove(level, id);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.playActionAnimation(TheQueenOfHatredAnim.SWEEP);
		Vec3 target = runtime.targetPosition();
		if (target != null) {
			double dx = target.x - queen.getX();
			double dz = target.z - queen.getZ();
			if (dx * dx + dz * dz > 0.0D) {
				float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
				queen.setYRot(yaw);
				queen.setYHeadRot(yaw);
				queen.yBodyRot = yaw;
			}
		}
		queen.lockSkillFacing();
		if (queen.level() instanceof ServerLevel level) {
			HitboxInstance hitbox = HitboxManager.create(HITBOX, level, queen.position(),
					windupTicks() + durationTicks() + recoveryTicks() + HitboxManager.CURRENT_TICK_DURATION, runtime);
			hitbox.follow(queen, new Vec3(0.0, queen.getBbHeight() / 2.0, 0.0), false);
			hitbox.setRotation(new Vec3(0.0, -queen.getYRot(), 0.0));
			hitbox.setLineOfSightMode(HitboxLineOfSightMode.UNRESTRICTED);
			runtime.setData(HITBOX_ID, hitbox.id());
		}
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 forward = Vec3.directionFromRotation(0.0F, queen.getYRot());
		Integer id = runtime.getData(HITBOX_ID);
		HitboxInstance hitbox = id == null ? null : HitboxManager.get(level, id);
		if (hitbox != null) {
			hitbox.setRemainingTicks(HitboxManager.CURRENT_TICK_DURATION);
			hitbox.activate();
		}
		int count = queen.isPhaseTwo()
				? PHASE_TWO_STAR_COUNT : PHASE_ONE_STAR_COUNT;
		for (int index = 0; index < count; index++) {
			double angle = Math.toRadians(-HALF_SWEEP_ANGLE_DEGREES
					+ index * 2.0 * HALF_SWEEP_ANGLE_DEGREES / (count - 1));
			Vec3 direction = forward.yRot((float) angle);
			Vec3 origin = queen.position().add(0.0, STAR_SPAWN_HEIGHT, 0.0)
					.add(direction.scale(STAR_SPAWN_OFFSET));
			MagicStarProjectile star = new MagicStarProjectile(AbnormalitieEntityTypes.MAGIC_NORMAL_STAR.get(), level);
			star.configure(queen, MagicStarProjectile.StarSize.TINY, MagicStarProjectile.StarVariant.A,
					origin, direction);
			level.addFreshEntity(star);
		}
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		removeHitbox(queen, runtime);
		queen.stopActionAnimation();
	}

	@Override
	public void onCancel(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		removeHitbox(queen, runtime);
		queen.stopActionAnimation();
	}

}
