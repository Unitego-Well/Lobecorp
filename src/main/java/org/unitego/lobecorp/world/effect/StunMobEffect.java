package org.unitego.lobecorp.world.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.NullMarked;
import org.unitego.lobecorp.registry.effect.LcMobEffects;
import org.unitego.lobecorp.util.entity.skill.EntitySkillUtil;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/// 眩晕降低冷却和攻击速度；三级以上立即打断技能、停止物品使用并禁止动作。
@NullMarked
public class StunMobEffect extends BasicMobEffect {
	/// 开始暂停生物 AI 的效果增幅等级，对应游戏内三级效果
	private static final int AI_DISABLE_AMPLIFIER = 2;
	/// 一级眩晕减少的冷却和攻击速度比例。
	private static final double FIRST_LEVEL_SPEED_REDUCTION = 0.25;
	/// 二级眩晕减少的冷却和攻击速度比例。
	private static final double SECOND_LEVEL_SPEED_REDUCTION = 0.5;
	/// 记录由眩晕主动暂停 AI 的生物，避免恢复原本就没有 AI 的生物
	private final Set<Mob> aiDisabledMobs = Collections.newSetFromMap(new WeakHashMap<>());

	public StunMobEffect(MobEffectCategory category, int color) {
		super(category, color);
	}

	/// 效果增幅从零开始，三级及更高等级的速度修饰为 -100%。
	public static double speedModifier(int amplifier) {
		return switch (amplifier) {
			case 0 -> -FIRST_LEVEL_SPEED_REDUCTION;
			case 1 -> -SECOND_LEVEL_SPEED_REDUCTION;
			default -> -1.0;
		};
	}

	/// 服务端技能与动作入口、客户端输入共用的三级眩晕判定。
	public static boolean preventsActions(LivingEntity entity) {
		MobEffectInstance effect = entity.getEffect(LcMobEffects.STUN);
		return effect != null && effect.getAmplifier() >= AI_DISABLE_AMPLIFIER;
	}

	@Override
	public void onEffectStarted(LivingEntity entity, int amplifier) {
		super.onEffectStarted(entity, amplifier);
		enforceRestrictions(entity);
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		enforceRestrictions(entity);
		return true;
	}

	/// 借用实体所属世界，仅判定服务端，不负责关闭世界。
	@SuppressWarnings("resource")
	private void enforceRestrictions(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel)) {
			return;
		}

		EntitySkillUtil.getCooldowns(entity);
		if (!preventsActions(entity)) {
			restoreAi(entity);
			return;
		}

		if (!EntitySkillUtil.getActiveSkills(entity).isEmpty()) {
			EntitySkillUtil.require(entity).cancelAll(true);
		}

		entity.stopUsingItem();
		if (entity instanceof Mob mob && !mob.isNoAi()) {
			aiDisabledMobs.add(mob);
			mob.setNoAi(true);
		}
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
		return true;
	}

	@Override
	public void onMobRemoved(ServerLevel level, LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
		if (entity instanceof Mob mob) {
			aiDisabledMobs.remove(mob);
		}
	}

	public void restoreAi(LivingEntity entity) {
		if (entity instanceof Mob mob && aiDisabledMobs.remove(mob)) {
			mob.setNoAi(false);
		}
	}

	public boolean isAiDisabledMob(Mob mob) {
		return aiDisabledMobs.contains(mob);
	}
}
