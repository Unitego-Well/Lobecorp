package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredMagicStar;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredTrackingMagicStar;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.hitbox.CylinderSize;
import org.unitego.lobecorp.hitbox.HitboxHitMode;
import org.unitego.lobecorp.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.util.TypedDataKey;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 转圈：旋转攻击近身敌人，并连续发射十五颗弱追踪星星。
public class TheQueenOfHatredSpinSkill extends TheQueenOfHatredSkill {
	/// 转圈冷却时间。
	public static final int COOLDOWN_TICKS = 4 * TICKS_PER_SECOND;
	/// AI 允许施放转圈的目标距离。
	private static final double USE_RANGE = 10.0;
	/// 近身圆柱半径。
	private static final double MELEE_RADIUS = 4.0;
	/// 近身圆柱高度。
	private static final double MELEE_HEIGHT = 3.0;
	/// 每 tick 近身伤害倍率。
	private static final float MELEE_DAMAGE_MULTIPLIER = 0.25F;
	/// 成功伤害后的击退强度。
	private static final double MELEE_KNOCKBACK = 0.8;
	/// 第一颗星星相对锁定朝向的偏角。
	private static final double START_ANGLE_DEGREES = -135.0;
	/// 两圈后从起始偏角回到正前方的总旋转角。
	private static final double TOTAL_ROTATION_DEGREES = 2.0 * 360.0 - START_ANGLE_DEGREES;
	/// 追踪星星数量。
	private static final int STAR_COUNT = 15;
	/// 投射物初始位置相对身体中心的前向距离。
	private static final double STAR_SPAWN_OFFSET = 1.0;
	/// 投射物散布。
	private static final float STAR_INACCURACY = 0.0F;
	/// 本次施放锁定的正前方向。
	private static final TypedDataKey<Vec3> LOCKED_FORWARD = TypedDataKey.create();
	/// 本次施放的近身判断框编号。
	private static final TypedDataKey<Integer> HITBOX_ID = TypedDataKey.create();
	/// 转圈近身判断框模板。
	private static final HitboxTemplate HITBOX_TEMPLATE = new HitboxTemplate(
			new CylinderSize(MELEE_RADIUS, MELEE_HEIGHT),
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
					target.knockback(MELEE_KNOCKBACK, entity.getX() - target.getX(), entity.getZ() - target.getZ());
				}
				return hurt;
			});

	/// @param properties 转圈的基础技能配置
	public TheQueenOfHatredSpinSkill(Properties properties) {
		super(properties);
	}

	@Override
	public TheQueenOfHatredAttackMode attackMode() {
		return TheQueenOfHatredAttackMode.MID_RANGE_STRAFE;
	}

	@Override
	public double minimumPlanningRange() {
		return 4.0;
	}

	@Override
	public double maximumPlanningRange() {
		return USE_RANGE;
	}

	@Override
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = entity.getAttackTarget();
		return target != null && entity.isValidTarget(target)
				&& entity.distanceToSqr(target) <= USE_RANGE * USE_RANGE;
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
		runtime.setTarget(entity.getAttackTarget());
		entity.playActionAnimation(TheQueenOfHatredAnim.SPIN);
		if (entity.level() instanceof ServerLevel level) {
			int lifetime = windupTicks() + durationTicks() + recoveryTicks();
			HitboxInstance hitbox = HitboxManager.create(HITBOX_TEMPLATE, level, entity.position(), lifetime);
			hitbox.follow(entity, new Vec3(0.0, MELEE_HEIGHT / 2.0, 0.0), false);
			hitbox.appendTargetFilter(entity::isValidTarget);
			hitbox.setHitPolicy(new HitboxHitPolicy(HitboxHitMode.EVERY_TICK, 0, -1, -1));
			runtime.setData(HITBOX_ID, hitbox.id());
		}
	}

	@Override
	public void onWindupTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target != null && target.isAlive()) {
			entity.facePosition(entity.predictTargetPosition(target, entity.getEyePosition(),
					TheQueenOfHatredMagicStar.launchSpeed()));
		}
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		runtime.setData(LOCKED_FORWARD, entity.getLookAngle().normalize());
		HitboxInstance hitbox = getHitbox(entity, runtime);
		if (hitbox != null) {
			hitbox.activate();
		}
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		int starIndex = runtime.activeTicks() - 1;
		if (starIndex < 0 || starIndex >= STAR_COUNT || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 forward = runtime.getData(LOCKED_FORWARD);
		if (forward == null) {
			return;
		}
		double progress = (double) starIndex / (STAR_COUNT - 1);
		double angle = START_ANGLE_DEGREES + TOTAL_ROTATION_DEGREES * progress;
		Vec3 direction = forward.yRot((float) (angle * Mth.DEG_TO_RAD));
		shootStar(level, entity, direction);
		if (entity.isSecondPhase()) {
			shootStar(level, entity, direction);
		}
	}

	private void shootStar(ServerLevel level, TheQueenOfHatred entity, Vec3 direction) {
		TheQueenOfHatredTrackingMagicStar star = new TheQueenOfHatredTrackingMagicStar(level, entity);
		Vec3 spawn = entity.getBoundingBox().getCenter().add(direction.normalize().scale(STAR_SPAWN_OFFSET));
		star.setPos(spawn);
		star.shoot(direction.x, direction.y, direction.z, TheQueenOfHatredMagicStar.launchSpeed(), STAR_INACCURACY);
		level.addFreshEntity(star);
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

	private HitboxInstance getHitbox(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		Integer id = runtime.getData(HITBOX_ID);
		if (id == null || !(entity.level() instanceof ServerLevel level)) {
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
}
