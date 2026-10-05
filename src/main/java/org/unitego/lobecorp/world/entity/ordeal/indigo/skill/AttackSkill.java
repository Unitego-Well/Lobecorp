package org.unitego.lobecorp.world.entity.ordeal.indigo.skill;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.world.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.world.entity.ordeal.indigo.SweeperAnim;
import org.unitego.lobecorp.util.entity.EntityUtil;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.entity.EntityFacingUtil;
import org.unitego.lobecorp.world.entity.skill.MultiStageBasicSkill;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.registry.entity.state.SweeperStates;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;
import org.unitego.lobecorp.util.TypedDataKey;
import org.unitego.lobecorp.world.hitbox.*;
import org.unitego.lobecorp.world.hitbox.geometry.SectorCylinderSize;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 清道夫 3 段攻击技能：attack → attack2 → attack3 → attack 循环。
/// <p>
/// 每段由前摇 + 后摇组成（共 20 tick，与 1s 动画对齐）。
/// 每段后摇结束后开放默认续段窗口，整套完成或超时后进入冷却。
public class AttackSkill extends MultiStageBasicSkill<Sweeper> {
	/// 一套连击（3 段）完成或续段超时后的冷却，为 20 tick（一秒）
	private static final int COMBO_COOLDOWN = TICKS_PER_SECOND;
	/// 普通攻击的总连击段数
	private static final int COMBO_LENGTH = 3;
	/// 第一段攻击相对基础攻击伤害增加的倍率
	private static final float FIRST_ATTACK_DAMAGE_MODIFIER = 1.0F;
	/// 第二段攻击相对基础攻击伤害增加的倍率
	private static final float SECOND_ATTACK_DAMAGE_MODIFIER = 1.0F;
	/// 第三段攻击相对基础攻击伤害增加的倍率
	private static final float THIRD_ATTACK_DAMAGE_MODIFIER = 1.5F;
	/// 普通攻击距离，同时作为扇形判断框半径
	private static final double ATTACK_RANGE = 2.0;
	/// 普通攻击扇形的完整角度，与女皇横扫一致
	private static final double ATTACK_ANGLE_DEGREES = 120.0;
	/// 普通攻击扇形的高度，与女皇横扫一致
	private static final double ATTACK_HEIGHT = 3.0;
	/// 每段普通攻击最多命中的目标数量
	private static final int MAXIMUM_TARGET_COUNT = 2;
	/// 本次攻击锁定的水平朝向，单位为度
	private static final TypedDataKey<Float> LOCKED_YAW = TypedDataKey.create();
	/// 普通攻击扇形判断框共享模板
	private static final HitboxTemplate HITBOX_TEMPLATE = new HitboxTemplate(
			new SectorCylinderSize(ATTACK_RANGE, ATTACK_HEIGHT, ATTACK_ANGLE_DEGREES),
			target -> target instanceof LivingEntity,
			context -> {
				if (!(context.source() instanceof Sweeper entity)) {
					return false;
				}
				if (!(context.target() instanceof LivingEntity target)) {
					return false;
				}
				EntitySkillRuntime<?> runtime = context.instance().skillRuntime();
				if (runtime == null || !entity.doHurtTarget(context.level(), target, getAttackDamageModifier(runtime.sequenceStage()))) {
					return false;
				}
				runtime.markSuccessful();
				SimpleParticleType strikeParticle = getStrikeParticle(runtime.sequenceStage());
				EntityUtil.getHitPosOnAABB(entity, target).ifPresent(hitPos ->
						context.level().sendParticles(strikeParticle, hitPos.x, hitPos.y, hitPos.z,
								1, 0, 0, 0, 0));
				return true;
			}
	);

	public AttackSkill(Properties properties) {
		super(properties, COMBO_LENGTH, COMBO_COOLDOWN);
	}

	public static Vec3 direction(Mob mob, Vec3 position) {
		return EntityFacingUtil.horizontalDirection(mob, position);
	}

	public static double conductorAngle() {
		return ATTACK_ANGLE_DEGREES;
	}

	public static double conductorRange() {
		return ATTACK_RANGE;
	}

	private static void lockFacing(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		Float yaw = runtime.getData(LOCKED_YAW);
		if (yaw == null)
			return;
		EntityFacingUtil.turn(entity, yaw);
	}

	private static SimpleParticleType getStrikeParticle(int combo) {
		return switch (combo) {
			case 0 -> LcParticleTypes.SIMPLE_SHORT_SLASH.get();
			case 1 -> LcParticleTypes.SIMPLE_LONG_SLASH.get();
			case 2 -> LcParticleTypes.SIMPLE_DOUBLE_SLASH.get();
			default -> throw new IllegalStateException();
		};
	}

	private static float getAttackDamageModifier(int combo) {
		return switch (combo) {
			case 0 -> FIRST_ATTACK_DAMAGE_MODIFIER;
			case 1 -> SECOND_ATTACK_DAMAGE_MODIFIER;
			case 2 -> THIRD_ATTACK_DAMAGE_MODIFIER;
			default -> throw new IllegalStateException();
		};
	}

	private static boolean isWithinAttackRange(Sweeper entity, LivingEntity target) {
		return entity.distanceToSqr(target) <= ATTACK_RANGE * ATTACK_RANGE;
	}

	@Override
	public boolean canUse(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		if (runtime.targetPosition() != null && ConductorController.isExplicitSkillCast(entity, this)) {
			runtime.setData(DIRECTION_CAST, true);
			return true;
		}
		LivingEntity target = runtime.target(LivingEntity.class);
		if (target == null) {
			target = entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		}
		if (target == null) {
			return false;
		}
		if (!target.isAlive()) {
			return false;
		}
		if (!entity.isValidTarget(target)) {
			return false;
		}
		if (!entity.hasLineOfSight(target)) {
			return false;
		}
		if (!isWithinAttackRange(entity, target)) {
			return false;
		}
		runtime.setTarget(target);
		return true;
	}

	@Override
	public boolean prepareAim(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		return EntityFacingUtil.aim(entity, EntityFacingUtil.targetPosition(entity, runtime, false), false);
	}

	@Override
	public void onWindupStart(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		float yaw = entity.getYRot();
		runtime.setData(LOCKED_YAW, yaw);
		lockFacing(entity, runtime);
		entity.addEntityState(SweeperStates.ATTACK);
		int combo = currentStage(runtime);
		entity.playActionAnimation(switch (combo) {
			case 0 -> SweeperAnim.ATTACK1;
			case 1 -> SweeperAnim.ATTACK2;
			case 2 -> SweeperAnim.ATTACK3;
			default -> throw new IllegalStateException();
		});
		if (entity.level() instanceof ServerLevel level) {
			int lifetime = windupTicks() + durationTicks() + recoveryTicks();
			HitboxInstance hitbox = HitboxManager.create(HITBOX_TEMPLATE, level, entity.position(), lifetime, runtime);
			hitbox.follow(entity, new Vec3(0.0, ATTACK_HEIGHT / 2.0, 0.0), false);
			hitbox.setRotation(new Vec3(0.0, -yaw, 0.0));
			hitbox.appendTargetFilter(entity::isValidTarget);
			hitbox.setHitPolicy(new HitboxHitPolicy(HitboxHitMode.ONCE, 0, 1, MAXIMUM_TARGET_COUNT));
			runtime.setData(HITBOX_ID, hitbox.id());
		}
	}

	@Override
	public void onActivate(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		lockFacing(entity, runtime);
		LivingEntity target = runtime.target(LivingEntity.class);
		if (!Boolean.TRUE.equals(runtime.getData(DIRECTION_CAST))
				&& (target == null || !target.isAlive() || !entity.isValidTarget(target)
				|| !isWithinAttackRange(entity, target))) {
			EntitySkillUtil.cancelSkill(entity, this);
			return;
		}

		entity.swing(InteractionHand.MAIN_HAND);
		HitboxInstance hitbox = getHitbox(level, runtime);
		if (hitbox != null) {
			hitbox.activate();
		}
	}

	@Override
	public void onTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		lockFacing(entity, runtime);
	}

	@Override
	public void onWindupTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		lockFacing(entity, runtime);
	}

	@Override
	public void onEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {

	}

	@Override
	public void onRecoveryEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.removeEntityState(SweeperStates.ATTACK);
		removeHitbox(entity, runtime);
	}

	@Override
	public void onCancel(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.removeEntityState(SweeperStates.ATTACK);
		entity.stopActionAnimation();
		removeHitbox(entity, runtime);
	}

}
