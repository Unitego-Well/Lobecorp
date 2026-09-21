package org.unitego.lobecorp.entity.entity_skill.abnormalitie;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.unitego.lobecorp.entity.abnormalitie.TheQueenOfHatred;
import org.unitego.lobecorp.entity.entity_skill.EntitySkillRuntime;
import org.unitego.lobecorp.entity.entity_skill.IEntitySkill;
import org.unitego.lobecorp.entity.util.EntitySkillManager;
import org.unitego.lobecorp.registry.tag.LcEntitySkillTags;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/// 柔光：原地持续恢复生命，受到实际伤害时中断。
public class TheQueenOfHatredHealSkill extends TheQueenOfHatredSkill {
	/// 柔光的持续治疗时间。
	public static final int DURATION_TICKS = 5 * TICKS_PER_SECOND;
	/// 客户端生成柔光治疗粒子的实体事件编号。
	private static final byte HEALING_PARTICLE_EVENT = 61;
	/// 柔光尘埃的淡粉色 ARGB 颜色。
	private static final int HEALING_DUST_COLOR = 0xFFFFB6C1;
	/// 柔光尘埃粒子尺寸。
	private static final float HEALING_DUST_SCALE = 1.0F;
	/// 每次治疗生成的尘埃粒子数量。
	private static final int HEALING_DUST_COUNT = 2;
	/// AI 开始治疗的生命比例。
	private static final float USE_HEALTH_RATIO = 0.5F;
	/// 一次完整治疗恢复的最大生命比例。
	private static final float TOTAL_HEAL_RATIO = 0.15F;
	/// 每 tick 恢复的最大生命比例。
	private static final float HEAL_RATIO_PER_TICK = TOTAL_HEAL_RATIO / DURATION_TICKS;

	/// @param properties 柔光的基础技能配置
	public TheQueenOfHatredHealSkill(Properties properties) {
		super(properties);
	}

	@Override
	public TheQueenOfHatredAttackMode attackMode() {
		return TheQueenOfHatredAttackMode.DEFENSIVE_RETREAT;
	}

	@Override
	public double minimumPlanningRange() {
		return 0.0;
	}

	@Override
	public double maximumPlanningRange() {
		return Double.POSITIVE_INFINITY;
	}

	@Override
	public boolean canUse(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		return entity.getHealth() < entity.getMaxHealth() * USE_HEALTH_RATIO;
	}

	@Override
	public boolean canBeOverridden(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime,
			IEntitySkill<?> replacement) {
		return replacement.is(LcEntitySkillTags.MOVEMENT);
	}

	@Override
	public void onWindupStart(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onActivate(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onTick(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		float previousHealth = entity.getHealth();
		float maximumHealth = (float) entity.getAttributeValue(Attributes.MAX_HEALTH);
		entity.heal(maximumHealth * HEAL_RATIO_PER_TICK);
		if (entity.getHealth() > previousHealth) {
			broadcastHealingParticles(entity);
		}
		if (entity.getHealth() >= entity.getMaxHealth()) {
			EntitySkillManager.endSkill(entity, this);
		}
	}

	@Override
	public void onEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onRecoveryEnd(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
	}

	@Override
	public void onCancel(TheQueenOfHatred entity, EntitySkillRuntime<TheQueenOfHatred> runtime) {
		onRecoveryEnd(entity, runtime);
	}

	/// 处理柔光技能的客户端粒子事件。
	///
	/// @return 事件属于柔光技能时返回 {@code true}
	public static boolean handleEntityEvent(TheQueenOfHatred entity, byte id) {
		if (id != HEALING_PARTICLE_EVENT) {
			return false;
		}
		entity.level().addParticle(ParticleTypes.HEART,
				entity.getRandomX(0.5), entity.getRandomY() + 0.25, entity.getRandomZ(0.5),
				0.0, 0.05, 0.0);
		DustParticleOptions dust = new DustParticleOptions(HEALING_DUST_COLOR, HEALING_DUST_SCALE);
		for (int index = 0; index < HEALING_DUST_COUNT; index++) {
			entity.level().addParticle(dust,
					entity.getRandomX(0.6), entity.getRandomY(), entity.getRandomZ(0.6),
					0.0, 0.04, 0.0);
		}
		return true;
	}

	/// 通知观察客户端在实体周围生成一次柔光治疗粒子。
	private static void broadcastHealingParticles(TheQueenOfHatred entity) {
		if (!entity.level().isClientSide()) {
			entity.level().broadcastEntityEvent(entity, HEALING_PARTICLE_EVENT);
		}
	}
}
