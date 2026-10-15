package org.unitego.lobecorp.world.entity.skill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.unitego.lobecorp.registry.LcRegistrys;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/// 冷却速度的持久计时基准；到期时间仍由原冷却附件持久化并同步。
public class EntitySkillCooldownClock {
	public static final Codec<EntitySkillCooldownClock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.fieldOf("last_tick").forGetter(clock -> clock.lastTick),
			Codec.DOUBLE.fieldOf("last_speed").forGetter(clock -> clock.lastSpeed),
			Codec.unboundedMap(LcRegistrys.ENTITY_SKILL.byNameCodec(), Codec.DOUBLE)
					.fieldOf("fractions").forGetter(clock -> clock.fractions)
	).apply(instance, EntitySkillCooldownClock::new));

	/// 尚未建立计时基准时使用的标记，兼容只有旧冷却附件的存档。
	private static final long UNINITIALIZED_TICK = -1L;
	private long lastTick = UNINITIALIZED_TICK;
	/// 前一计时间隔的速度，1 表示每 tick 推进 1 tick 冷却。
	private double lastSpeed = 1.0;
	/// 到期时间向上取整产生的余量，范围为 (-1, 0]。
	private final Map<IEntitySkill<?>, Double> fractions = new HashMap<>();

	public EntitySkillCooldownClock() {
	}

	private EntitySkillCooldownClock(long lastTick, double lastSpeed, Map<IEntitySkill<?>, Double> fractions) {
		this.lastTick = lastTick;
		this.lastSpeed = lastSpeed;
		this.fractions.putAll(fractions);
	}

	/// 使用上一时刻的速度推进，随后记录当前速度；同一 tick 的重复查询不重复扣除冷却。
	public Map<IEntitySkill<?>, Long> advance(Map<IEntitySkill<?>, Long> cooldowns, long now, double speed) {
		if (lastTick == UNINITIALIZED_TICK) {
			lastTick = now;
			lastSpeed = speed;
			return cooldowns;
		}

		if (now == lastTick) {
			lastSpeed = speed;
			return cooldowns;
		}

		long elapsed = Math.max(0L, now - lastTick);
		Map<IEntitySkill<?>, Long> next = new LinkedHashMap<>(cooldowns);
		for (var entry : cooldowns.entrySet()) {
			IEntitySkill<?> skill = entry.getKey();
			if (entry.getValue() <= lastTick) {
				fractions.remove(skill);
				if (now < lastTick) {
					next.put(skill, Math.min(entry.getValue(), now));
				}

				continue;
			}

			double remaining = entry.getValue() - lastTick + fractions.getOrDefault(skill, 0.0)
					- elapsed * lastSpeed;
			long rounded = (long) Math.ceil(Math.max(0.0, remaining));
			next.put(skill, now + rounded);
			if (rounded == 0L) {
				fractions.remove(skill);
			} else {
				fractions.put(skill, remaining - rounded);
			}
		}

		fractions.keySet().retainAll(cooldowns.keySet());
		lastTick = now;
		lastSpeed = speed;
		return next.equals(cooldowns) ? cooldowns : Map.copyOf(next);
	}

	/// 重新设置某技能冷却时，仅清除该技能的旧小数进度。
	public void reset(IEntitySkill<?> skill) {
		fractions.remove(skill);
	}
}
