package org.unitego.lobecorp.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/// 眩晕效果：高等级眩晕会暂停非玩家生物的 AI。
@NullMarked
public class StunMobEffect extends MobEffect {
	/// 开始暂停生物 AI 的效果增幅等级，对应游戏内三级效果
	private static final int AI_DISABLE_AMPLIFIER = 2;
	/// 记录由眩晕主动暂停 AI 的生物，避免恢复原本就没有 AI 的生物
	private final Set<Mob> aiDisabledMobs = Collections.newSetFromMap(new WeakHashMap<>());

	public StunMobEffect(MobEffectCategory category, int color) {
		super(category, color);
	}

	/// 状态效果属性仅在效果存续期间生效，不写入实体的永久属性数据。
	@Override
	public void addAttributeModifiers(AttributeMap attributes, int amplifier) {
		createModifiers(amplifier, (attribute, modifier) -> {
			AttributeInstance instance = attributes.getInstance(attribute);
			if (instance != null) {
				instance.removeModifier(modifier.id());
				instance.addTransientModifier(modifier);
			}
		});
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		if (amplifier >= AI_DISABLE_AMPLIFIER && entity instanceof Mob mob && !mob.isNoAi()) {
			aiDisabledMobs.add(mob);
			mob.setNoAi(true);
		}
		return true;
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
		return amplifier >= AI_DISABLE_AMPLIFIER;
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
}
