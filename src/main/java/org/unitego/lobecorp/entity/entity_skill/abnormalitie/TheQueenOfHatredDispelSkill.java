package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatredAnim;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.hitbox.HitboxHitMode;
import org.unitego.lobecorp.hitbox.HitboxHitPolicy;
import org.unitego.lobecorp.hitbox.HitboxInstance;
import org.unitego.lobecorp.hitbox.HitboxManager;
import org.unitego.lobecorp.hitbox.HitboxTemplate;
import org.unitego.lobecorp.hitbox.CylinderSize;
import org.unitego.lobecorp.hitbox.SectorCylinderSize;
import org.unitego.lobecorp.util.TypedDataKey;

import static org.unitego.lobecorp.hitbox.HitboxManager.CURRENT_TICK_DURATION;
import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 退散：持续攻击前方区域，并在第四十四 tick 产生大范围伤害和击退。
public class TheQueenOfHatredDispelSkill extends TheQueenOfHatredSkill {
	/// 退散冷却时间。
	public static final int COOLDOWN_TICKS = 5 * TICKS_PER_SECOND;
	/// AI 允许施放退散的目标距离。
	private static final double USE_RANGE = 4.0;
	/// 退散前摇寻找安全落地点的水平半径。
	private static final double LANDING_SEARCH_RADIUS = 5.0;
	/// 落地候选点数量。
	private static final int LANDING_ATTEMPTS = 16;
	/// 退散持续攻击的前方距离。
	private static final double CHANNEL_RANGE = 3.0;
	/// 退散持续攻击的完整扇形角度。
	private static final double CHANNEL_ANGLE_DEGREES = 90.0;
	/// 退散持续攻击的判定高度。
	private static final double CHANNEL_HEIGHT = 3.0;
	/// 退散持续攻击的每 tick 伤害倍率。
	private static final float CHANNEL_DAMAGE_MULTIPLIER = 0.25F;
	/// 持续攻击从技能开始计算的起始 tick。
	private static final int CHANNEL_START_SKILL_TICK = 18;
	/// 持续攻击从技能开始计算的结束 tick。
	private static final int CHANNEL_END_SKILL_TICK = 36;
	/// 在持续攻击结束后的下一 tick 创建最终爆发判断框。
	private static final int BURST_CREATE_SKILL_TICK = CHANNEL_END_SKILL_TICK + CURRENT_TICK_DURATION;
	/// 退散最终爆发圆柱半径。
	private static final double BURST_RANGE = 5.0;
	/// 退散最终爆发圆柱高度。
	private static final double BURST_HEIGHT = 1.0;
	/// 退散最终爆发伤害倍率。
	private static final float BURST_DAMAGE_MULTIPLIER = 2.0F;
	/// 退散最终爆发的水平击退强度。
	private static final double BURST_KNOCKBACK = 2.0;
	/// 退散最终爆发提供的最低垂直速度。
	private static final double BURST_VERTICAL_LAUNCH_SPEED = 1.2;
	/// 最终爆发从技能开始计算的 tick。
	private static final int BURST_SKILL_TICK = 44;
	/// 本次施放的持续攻击判断框编号。
	private static final TypedDataKey<Integer> CHANNEL_HITBOX_ID = TypedDataKey.create();
	/// 本次施放的最终爆发判断框编号。
	private static final TypedDataKey<Integer> BURST_HITBOX_ID = TypedDataKey.create();
	/// 退散持续攻击判断框模板。
	private static final HitboxTemplate CHANNEL_HITBOX_TEMPLATE = new HitboxTemplate(
			new SectorCylinderSize(CHANNEL_RANGE, CHANNEL_HEIGHT, CHANNEL_ANGLE_DEGREES),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof TheQueenOfHatred entity)) {
					return false;
				}
				if (!(context.target() instanceof LivingEntity target)) {
					return false;
				}
				target.invulnerableTime = 0;
				boolean hurt = target.hurtServer(context.level(), entity.damageSources().mobAttack(entity),
						(float) entity.getAttributeValue(Attributes.ATTACK_DAMAGE) * CHANNEL_DAMAGE_MULTIPLIER);
				return hurt;
			}
	);
	/// 退散最终爆发判断框模板。
	private static final HitboxTemplate BURST_HITBOX_TEMPLATE = new HitboxTemplate(
			new CylinderSize(BURST_RANGE, BURST_HEIGHT),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof TheQueenOfHatred entity)) {
					return false;
				}
				if (!(context.target() instanceof LivingEntity target)) {
					return false;
				}
				boolean hurt = target.hurtServer(context.level(), entity.damageSources().mobAttack(entity),
						(float) entity.getAttributeValue(Attributes.ATTACK_DAMAGE) * BURST_DAMAGE_MULTIPLIER);
				if (!hurt) {
					return false;
				}
				target.knockback(BURST_KNOCKBACK, entity.getX() - target.getX(), entity.getZ() - target.getZ());
				Vec3 movement = target.getDeltaMovement();
				target.setDeltaMovement(movement.x, Math.max(movement.y, BURST_VERTICAL_LAUNCH_SPEED), movement.z);
				return true;
			}
	);

	/// @param properties 退散的基础技能配置
	public TheQueenOfHatredDispelSkill(Properties properties) {
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
	public boolean prefersDenseTarget() {
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
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		AABB bounds = entity.getBoundingBox().inflate(USE_RANGE);
		return entity.level().getEntitiesOfClass(LivingEntity.class, bounds, entity::isValidTarget).stream()
				.anyMatch(target -> target.distanceToSqr(entity) <= USE_RANGE * USE_RANGE);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		runtime.setTarget(entity.getAttackTarget());
		entity.playActionAnimation(TheQueenOfHatredAnim.DISPEL);
		entity.startDispelLanding(findLandingPosition(entity));
		if (entity.level() instanceof ServerLevel level) {
			int lifetime = windupTicks() + durationTicks() + recoveryTicks() + CURRENT_TICK_DURATION;
			HitboxInstance channel = HitboxManager.create(CHANNEL_HITBOX_TEMPLATE, level, entity.position(), lifetime);
			channel.follow(entity, new Vec3(0.0, CHANNEL_HEIGHT / 2.0, 0.0), true);
			channel.appendTargetFilter(entity::isValidTarget);
			channel.setHitPolicy(new HitboxHitPolicy(HitboxHitMode.EVERY_TICK, 0, -1, -1));
			runtime.setData(CHANNEL_HITBOX_ID, channel.id());
		}
	}

	@Override
	public void onWindupTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		faceLockedTarget(entity, runtime);
		if (runtime.ticksLeft() <= 1 && !entity.onGround()) {
			EntitySkillManager.forceCancelSkill(entity, this);
		}
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		int skillTick = runtime.elapsedTicks();
		HitboxInstance channel = getHitbox(level, runtime, CHANNEL_HITBOX_ID);
		if (skillTick == CHANNEL_START_SKILL_TICK && channel != null) {
			channel.activate();
		}
		if (skillTick == BURST_CREATE_SKILL_TICK) {
			removeHitbox(entity, runtime, CHANNEL_HITBOX_ID);
			int lifetime = BURST_SKILL_TICK - BURST_CREATE_SKILL_TICK + CURRENT_TICK_DURATION;
			HitboxInstance burst = HitboxManager.create(BURST_HITBOX_TEMPLATE, level, entity.position(), lifetime);
			burst.follow(entity, new Vec3(0.0, BURST_HEIGHT / 2.0, 0.0), false);
			burst.appendTargetFilter(entity::isValidTarget);
			runtime.setData(BURST_HITBOX_ID, burst.id());
		}
		if (skillTick == BURST_SKILL_TICK) {
			HitboxInstance burst = getHitbox(level, runtime, BURST_HITBOX_ID);
			if (burst != null) {
				burst.activate();
				burst.setRemainingTicks(CURRENT_TICK_DURATION);
			}
		}
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.finishDispelLanding();
		entity.stopActionAnimation();
		removeHitbox(entity, runtime, CHANNEL_HITBOX_ID);
	}

	@Override
	public void onCancel(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		entity.finishDispelLanding();
		entity.stopActionAnimation();
		removeHitbox(entity, runtime, CHANNEL_HITBOX_ID);
		removeHitbox(entity, runtime, BURST_HITBOX_ID);
	}

	@Nullable
	private Vec3 findLandingPosition(TheQueenOfHatred entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return null;
		}
		Vec3 best = null;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (int index = 0; index < LANDING_ATTEMPTS; index++) {
			double angle = Mth.TWO_PI * index / LANDING_ATTEMPTS;
			double distance = LANDING_SEARCH_RADIUS * index / LANDING_ATTEMPTS;
			int x = Mth.floor(entity.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(entity.getZ() + Math.sin(angle) * distance);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			Vec3 candidate = new Vec3(x + 0.5, y, z + 0.5);
			Vec3 offset = candidate.subtract(entity.position());
			if (level.noCollision(entity, entity.getBoundingBox().move(offset))
					&& offset.lengthSqr() < bestDistance) {
				best = candidate;
				bestDistance = offset.lengthSqr();
			}
		}
		return best;
	}

	private void faceLockedTarget(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target != null && target.isAlive()) {
			entity.faceTarget(target);
		}
	}

	private HitboxInstance getHitbox(ServerLevel level, EntitySkillRuntime<TheQueenOfHatred> runtime,
			TypedDataKey<Integer> key) {
		Integer id = runtime.getData(key);
		if (id == null) {
			return null;
		}
		return HitboxManager.get(level, id);
	}

	private void removeHitbox(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime,
			TypedDataKey<Integer> key) {
		Integer id = runtime.removeData(key);
		if (id == null) {
			return;
		}
		if (entity.level() instanceof ServerLevel level) {
			HitboxManager.remove(level, id);
		}
	}
}
