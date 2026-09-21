package org.unitego.lobecorp.entity.entity_skill.sweeper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.ordeal.indigo.Sweeper;
import org.unitego.lobecorp.entity.ordeal.indigo.SweeperAnim;
import org.unitego.lobecorp.entity.util.EntityUtil;
import org.unitego.lobecorp.registry.entity_state.SweeperStates;
import org.unitego.lobecorp.registry.particle.LcParticleTypes;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 清道夫 3 段攻击技能：attack → attack2 → attack3 → attack 循环。
/// <p>
/// 每段由前摇 + 后摇组成（共 20 tick，与 1s 动画对齐）。
/// 连段自动衔接：每段后摇结束由 {@link Sweeper#getAttackCombo()} 进位，
/// 战斗行为持续施放即可自动打出下一段。
public class SweeperAttackSkill extends SweeperSkill {
	/// 一套连击（3 段）完成后进入的冷却
	private static final int COMBO_COOLDOWN = 2 * TICKS_PER_SECOND;
	/// 普通攻击的总连击段数
	private static final int COMBO_LENGTH = 3;
	/// 第一段攻击相对基础攻击伤害增加的倍率
	private static final float FIRST_ATTACK_DAMAGE_MODIFIER = 1.0F;
	/// 第二段攻击相对基础攻击伤害增加的倍率
	private static final float SECOND_ATTACK_DAMAGE_MODIFIER = 1.0F;
	/// 第三段攻击相对基础攻击伤害增加的倍率
	private static final float THIRD_ATTACK_DAMAGE_MODIFIER = 1.5F;

	public SweeperAttackSkill(Properties properties) {
		super(properties);
	}

	@Override
	public boolean canUse(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		LivingEntity target = entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
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
		if (!entity.isWithinMeleeAttackRange(target)) {
			return false;
		}
		runtime.setTarget(target);
		return true;
	}

	@Override
	public void onWindupStart(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.addEntityState(SweeperStates.ATTACK);
		int combo = entity.getAttackCombo() % 3;
		SweeperAnim animation = SweeperAnim.values()[SweeperAnim.ATTACK1.ordinal() + combo];
		entity.playActionAnimation(animation);
	}

	@Override
	public void onActivate(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		LivingEntity target = getTarget(runtime);
		if (target == null) {
			EntitySkillManager.cancelSkill(entity, this);
			return;
		}
		if (!target.isAlive()) {
			EntitySkillManager.cancelSkill(entity, this);
			return;
		}
		if (!entity.isValidTarget(target)) {
			EntitySkillManager.cancelSkill(entity, this);
			return;
		}
		if (!entity.isWithinMeleeAttackRange(target)) {
			EntitySkillManager.cancelSkill(entity, this);
			return;
		}

		entity.swing(InteractionHand.MAIN_HAND);
		int combo = Math.floorMod(entity.getAttackCombo(), COMBO_LENGTH);
		boolean hit = entity.doHurtTarget(level, target, getAttackDamageModifier(combo));
		if (!hit) {
			return;
		}
		runtime.markSuccessful();
		SimpleParticleType strikeParticle = getStrikeParticle(combo);
		EntityUtil.getHitPosOnAABB(entity, target).ifPresent(hitPos ->
				level.sendParticles(strikeParticle, hitPos.x, hitPos.y, hitPos.z, 1, 0, 0, 0, 0));
	}

	@Override
	public void onTick(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {

	}

	@Override
	public void onEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {

	}

	@Override
	public void onRecoveryEnd(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.stopActionAnimation();
		entity.removeEntityState(SweeperStates.ATTACK);
		if (!runtime.isSuccessful()) {
			return;
		}
		int combo = (entity.getAttackCombo() + 1) % 3;
		entity.setAttackCombo(combo);
		if (combo == 0) {
			EntitySkillManager.setCooldown(entity, this, COMBO_COOLDOWN);
		}
	}

	@Override
	public void onCancel(Sweeper entity, EntitySkillRuntime<Sweeper> runtime) {
		entity.stopActionAnimation();
		entity.removeEntityState(SweeperStates.ATTACK);
	}

	private SimpleParticleType getStrikeParticle(int combo) {
		return switch (combo) {
			case 0 -> LcParticleTypes.SIMPLE_SHORT_SLASH.get();
			case 1 -> LcParticleTypes.SIMPLE_LONG_SLASH.get();
			case 2 -> LcParticleTypes.SIMPLE_DOUBLE_SLASH.get();
			default -> throw new IllegalStateException();
		};
	}

	private float getAttackDamageModifier(int combo) {
		return switch (combo) {
			case 0 -> FIRST_ATTACK_DAMAGE_MODIFIER;
			case 1 -> SECOND_ATTACK_DAMAGE_MODIFIER;
			case 2 -> THIRD_ATTACK_DAMAGE_MODIFIER;
			default -> throw new IllegalStateException();
		};
	}
}
