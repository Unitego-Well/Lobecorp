package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredMagicStar;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.hitbox.SectorCylinderSize;
import org.unitego.lobecorp.util.TypedDataKey;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 横扫：近身扇形攻击，并在第十八至二十 tick 依次发射三颗魔法星星。
public class TheQueenOfHatredSweepSkill extends TheQueenOfHatredSkill {
	/// 横扫冷却时间。
	public static final int COOLDOWN_TICKS = 2 * TICKS_PER_SECOND;
	/// AI 允许施放横扫的目标距离。
	private static final double USE_RANGE = 7.0;
	/// 横扫近身判定距离。
	private static final double MELEE_RANGE = 4.0;
	/// 横扫近身扇形的完整角度。
	private static final double MELEE_ANGLE_DEGREES = 120.0;
	/// 横扫近身判定高度。
	private static final double MELEE_HEIGHT = 3.0;
	/// 横扫近身伤害倍率。
	private static final float MELEE_DAMAGE_MULTIPLIER = 1.0F;
	/// 横扫近身击退强度。
	private static final double MELEE_KNOCKBACK = 0.8;
	/// 魔法星星初速度。
	private static final float STAR_SPEED = TheQueenOfHatredMagicStar.launchSpeed();
	/// 魔法星星发射散布。
	private static final float STAR_INACCURACY = 0.0F;
	/// 三颗魔法星星相对视线的水平偏角。
	private static final float[] STAR_ANGLES = {-20.0F, 0.0F, 20.0F};
	/// 本次施放创建的近身判断框编号。
	private static final TypedDataKey<Integer> HITBOX_ID = TypedDataKey.create();
	/// 横扫近身判断框共享模板。
	private static final HitboxTemplate HITBOX_TEMPLATE = new HitboxTemplate(
			new SectorCylinderSize(MELEE_RANGE, MELEE_HEIGHT, MELEE_ANGLE_DEGREES),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof TheQueenOfHatred entity)) {
					return false;
				}
				if (!(context.target() instanceof LivingEntity target)) {
					return false;
				}
				boolean hurt = target.hurtServer(context.level(), entity.damageSources().mobAttack(entity),
						(float) entity.getAttributeValue(Attributes.ATTACK_DAMAGE) * MELEE_DAMAGE_MULTIPLIER);
				if (hurt) {
					target.knockback(MELEE_KNOCKBACK, entity.getX() - target.getX(),
							entity.getZ() - target.getZ());
				}
				return hurt;
			}
	);

	/// @param properties 横扫的基础技能配置
	public TheQueenOfHatredSweepSkill(Properties properties) {
		super(properties);
	}

	@Override
	public TheQueenOfHatredAttackMode attackMode() {
		return TheQueenOfHatredAttackMode.CLOSE_PRESSURE;
	}

	@Override
	public double minimumPlanningRange() {
		return 0.0;
	}

	@Override
	public double maximumPlanningRange() {
		return USE_RANGE;
	}

	@Override
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = entity.getAttackTarget();
		if (target == null) {
			return false;
		}
		if (!entity.isValidTarget(target)) {
			return false;
		}
		if (entity.distanceToSqr(target) > USE_RANGE * USE_RANGE) {
			return false;
		}
		runtime.setTarget(target);
		return true;
	}

	@Override
	public boolean canBeOverridden(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime,
			IEntitySkill<?> replacement) {
		return false;
	}

	@Override
	public boolean canBeCancelled(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return false;
	}

	@Override
	public void onWindupStart(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.playActionAnimation(TheQueenOfHatredAnim.SWEEP);
		if (entity.level() instanceof ServerLevel level) {
			int lifetime = windupTicks() + durationTicks() + recoveryTicks();
			HitboxInstance hitbox = HitboxManager.create(HITBOX_TEMPLATE, level, entity.position(), lifetime);
			hitbox.follow(entity, new Vec3(0.0, MELEE_HEIGHT / 2.0, 0.0), true);
			hitbox.appendTargetFilter(entity::isValidTarget);
			runtime.setData(HITBOX_ID, hitbox.id());
		}
	}

	@Override
	public void onWindupTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		faceLockedTarget(entity, runtime);
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		faceLockedTarget(entity, runtime);
		int starIndex = runtime.activeTicks() - 1;
		if (starIndex == 0) {
			HitboxInstance hitbox = getHitbox(entity, runtime);
			if (hitbox != null) {
				hitbox.activate();
			}
		}
		if (starIndex >= 0 && starIndex < STAR_ANGLES.length && entity.level() instanceof ServerLevel level) {
			shootStar(level, entity, STAR_ANGLES[starIndex]);
			if (entity.isSecondPhase()) {
				shootStar(level, entity, STAR_ANGLES[starIndex]);
			}
		}
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		HitboxInstance hitbox = getHitbox(entity, runtime);
		if (hitbox != null) {
			hitbox.deactivate();
		}
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.stopActionAnimation();
		removeHitbox(entity, runtime);
	}

	@Override
	public void onCancel(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		onRecoveryEnd(entity, runtime);
	}

	private void faceLockedTarget(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target != null && target.isAlive()) {
			entity.facePosition(entity.predictTargetPosition(target, entity.getEyePosition(), STAR_SPEED));
		}
	}

	private HitboxInstance getHitbox(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Integer id = runtime.getData(HITBOX_ID);
		if (id == null) {
			return null;
		}
		if (!(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		return HitboxManager.get(level, id);
	}

	private void removeHitbox(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Integer id = runtime.removeData(HITBOX_ID);
		if (id != null && entity.level() instanceof ServerLevel level) {
			HitboxManager.remove(level, id);
		}
	}

	private void shootStar(ServerLevel level, TheQueenOfHatred entity, float angle) {
		LivingEntity target = entity.getAttackTarget();
		Vec3 direction = target == null
				? entity.getLookAngle()
				: entity.predictTargetPosition(target, entity.getEyePosition(), STAR_SPEED)
						.subtract(entity.getEyePosition()).normalize();
		direction = direction.yRot(angle * Mth.DEG_TO_RAD);
		TheQueenOfHatredMagicStar star = new TheQueenOfHatredMagicStar(level, entity);
		star.shoot(direction.x, direction.y, direction.z, STAR_SPEED, STAR_INACCURACY);
		level.addFreshEntity(star);
	}
}
