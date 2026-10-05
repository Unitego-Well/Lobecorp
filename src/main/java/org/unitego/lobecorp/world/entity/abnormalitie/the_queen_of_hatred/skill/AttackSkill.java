package org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.conductor.control.ConductorController;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatredAnim;
import org.unitego.lobecorp.world.entity.skill.EntitySkillRuntime;
import org.unitego.lobecorp.util.entity.EntityFacingUtil;
import org.unitego.lobecorp.world.entity.skill.MultiStageBasicSkill;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;
import org.unitego.lobecorp.world.hitbox.*;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 女皇两段普通攻击：由通用多段普通技能管理续段窗口和整套冷却。
public class AttackSkill extends MultiStageBasicSkill<TheQueenOfHatred> {
	/// 一套连击包含的攻击段数。
	private static final int COMBO_LENGTH = 2;
	/// 整套连击完成或续段超时后的冷却，为 20 tick（一秒）。
	private static final int COMBO_COOLDOWN_TICKS = TICKS_PER_SECOND;
	/// 普攻近战距离与女皇横扫一致，单位为格。
	private static final double ATTACK_RANGE = SweepSkill.MELEE_RANGE;
	/// 两段普通攻击共享的扇形判断框，伤害使用女皇自身攻击属性。
	private static final HitboxTemplate HITBOX = new HitboxTemplate(
			SweepSkill.meleeHitboxTemplate().initialSize(),
			SweepSkill.meleeHitboxTemplate().targetFilter(),
			context -> {
				if (!SweepSkill.meleeHitboxTemplate().effect().apply(context)) {
					return false;
				}
				EntitySkillRuntime<?> runtime = context.instance().skillRuntime();
				if (runtime != null) {
					runtime.markSuccessful();
				}
				return true;
			});

	public AttackSkill(Properties properties) {
		super(properties, COMBO_LENGTH, COMBO_COOLDOWN_TICKS);
	}

	@Override
	public boolean canUse(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (runtime.targetPosition() != null && ConductorController.isExplicitSkillCast(queen, this)) {
			runtime.setData(DIRECTION_CAST, true);
			return true;
		}
		LivingEntity target = getTarget(queen, runtime);
		if (target == null || !target.isAlive() || !queen.isHatedTarget(target)
				|| !queen.hasLineOfSight(target) || isOutsideAttackRange(queen, target)) {
			return false;
		}
		runtime.setTarget(target);
		return true;
	}

	@Override
	public boolean prepareAim(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return EntityFacingUtil.aim(queen, EntityFacingUtil.targetPosition(queen, runtime, false), false);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.lockSkillFacing();
		int combo = currentStage(runtime);
		queen.playActionAnimation(combo == 0 ? TheQueenOfHatredAnim.ATTACK : TheQueenOfHatredAnim.ATTACK_2);
		if (queen.level() instanceof ServerLevel level) {
			HitboxInstance hitbox = HitboxManager.create(HITBOX, level, queen.position(),
					windupTicks() + durationTicks() + recoveryTicks(), runtime);
			hitbox.follow(queen, new Vec3(0.0, queen.getBbHeight() / 2.0, 0.0), false);
			hitbox.setRotation(new Vec3(0.0, -queen.getYRot(), 0.0));
			hitbox.appendTargetFilter(candidate -> candidate instanceof LivingEntity living && queen.isHatedTarget(living));
			hitbox.setHitPolicy(HitboxHitPolicy.ONCE_PER_TARGET);
			hitbox.setLineOfSightMode(HitboxLineOfSightMode.UNRESTRICTED);
			runtime.setData(HITBOX_ID, hitbox.id());
		}
	}

	@Override
	public void onWindupTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
	}

	@Override
	public void onActivate(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		if (!(queen.level() instanceof ServerLevel level)) {
			return;
		}
		queen.restoreSkillFacing();
		LivingEntity target = getTarget(queen, runtime);
		if (!Boolean.TRUE.equals(runtime.getData(DIRECTION_CAST))
				&& (target == null || !target.isAlive() || !queen.isHatedTarget(target)
				|| isOutsideAttackRange(queen, target))) {
			EntitySkillUtil.cancelSkill(queen, this);
			return;
		}
		HitboxInstance hitbox = getHitbox(level, runtime);
		if (hitbox != null) {
			hitbox.activate();
		}
	}

	@Override
	public void onTick(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		queen.restoreSkillFacing();
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
		onRecoveryEnd(queen, runtime);
	}

	@Nullable
	private static LivingEntity getTarget(TheQueenOfHatred queen, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return runtime.target() instanceof LivingEntity target ? target
				: queen.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
	}

	private static boolean isOutsideAttackRange(TheQueenOfHatred queen, LivingEntity target) {
		return queen.distanceToSqr(target) > ATTACK_RANGE * ATTACK_RANGE;
	}

}
