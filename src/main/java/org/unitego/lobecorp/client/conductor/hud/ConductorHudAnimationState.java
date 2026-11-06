package org.unitego.lobecorp.client.conductor.hud;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.unitego.lobecorp.client.conductor.hud.ConductorHudTheme.ANIMATION_FRAME_MS;

/// 指挥家 HUD 的纯动画状态；推进方法显式接收当帧时间，读取方法不修改状态。
public class ConductorHudAnimationState {
	private final Map<UUID, Float> healthRatios = new HashMap<>();
	private final Map<UUID, Float> previousHealthRatios = new HashMap<>();
	private final Map<UUID, Long> healthChangedAt = new HashMap<>();
	private final Map<UUID, Float> shownHealth = new HashMap<>();
	private final Map<UUID, Float> sampledHealth = new HashMap<>();
	private final Map<String, Integer> lastCooldowns = new HashMap<>();
	private final Map<String, Long> cooldownFlashes = new HashMap<>();
	private final Map<UUID, RemovedCard> removedCards = new HashMap<>();
	private final Map<UUID, Long> removedAt = new HashMap<>();

	/// 采样成员生命值；只有比例变化时更新插值起点。
	public void sampleHealth(UUID member, float ratio, boolean reduceMotion, long now) {
		Float previous = healthRatios.put(member, ratio);

		if (previous != null && previous != ratio) {
			previousHealthRatios.put(member, previous);
			healthChangedAt.put(member, now);
		}

		float shown = ratio;

		if (!reduceMotion && now - healthChangedAt.getOrDefault(member, 0L) < ANIMATION_FRAME_MS) {
			shown = (previousHealthRatios.getOrDefault(member, ratio) + ratio) / 2;
		}

		shownHealth.put(member, shown);
		sampledHealth.put(member, ratio);
	}

	/// 读取最近一次采样得到的生命值，不推进插值。
	public float healthShown(UUID member, float fallback) {
		return shownHealth.getOrDefault(member, fallback);
	}

	/// 读取最近一次采样得到的生命比例，不重新读取实体。
	public float healthRatio(UUID member, float fallback) {
		return sampledHealth.getOrDefault(member, fallback);
	}

	/// 采样技能冷却；从有剩余变为零时记录闪烁起点。
	public void sampleCooldown(String key, int remaining, long now) {
		Integer previous = lastCooldowns.put(key, remaining);

		if (previous != null && previous > 0 && remaining == 0) {
			cooldownFlashes.put(key, now);
		}
	}

	public long cooldownFlash(String key) {
		return cooldownFlashes.getOrDefault(key, 0L);
	}

	/// 读取最近一次采样得到的冷却剩余，不推进状态。
	public int cooldownRemaining(String key, int fallback) {
		return lastCooldowns.getOrDefault(key, fallback);
	}

	/// 移除到期卡片；只在准备阶段调用。
	public void tickRemovals(long now) {
		removedAt.entrySet().removeIf(entry -> now - entry.getValue() >= ANIMATION_FRAME_MS);
		removedCards.keySet().retainAll(removedAt.keySet());
	}

	public void addRemovedCard(UUID member, int x, int y, Component name, boolean reduceMotion, long now) {
		if (reduceMotion) {
			return;
		}

		removedCards.put(member, new RemovedCard(x, y, name));
		removedAt.put(member, now);
	}

	public Map<UUID, RemovedCard> removedCards() {
		return Collections.unmodifiableMap(removedCards);
	}

	/// 按存续规则清理插值状态，保持旧实现只剔除已不存在成员的语义。
	public void pruneHealth(java.util.function.Predicate<UUID> keep) {
		healthRatios.keySet().removeIf(member -> !keep.test(member));
		previousHealthRatios.keySet().retainAll(healthRatios.keySet());
		healthChangedAt.keySet().retainAll(healthRatios.keySet());
		shownHealth.keySet().retainAll(healthRatios.keySet());
		sampledHealth.keySet().retainAll(healthRatios.keySet());
	}

	public void clear() {
		healthRatios.clear();
		previousHealthRatios.clear();
		healthChangedAt.clear();
		shownHealth.clear();
		sampledHealth.clear();
		lastCooldowns.clear();
		cooldownFlashes.clear();
		removedCards.clear();
		removedAt.clear();
	}

	public record RemovedCard(int x, int y, Component name) {
	}
}
