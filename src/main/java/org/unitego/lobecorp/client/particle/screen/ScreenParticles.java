package org.unitego.lobecorp.client.particle.screen;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.unitego.lobecorp.config.client.DamageParticleConfig;
import org.unitego.lobecorp.network.tc.damage.DamageParticlePayload;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/// 客户端线程拥有的短寿命屏幕粒子；实例壳留在私有池中，内容与渲染资源不参与池化。
public class ScreenParticles {

	/// 伤害数字最多保留两位小数，不使用分组符或客户端地区格式。
	private static final String DAMAGE_NUMBER_PATTERN = "0.##";
	private static final String INVALID_ANCHOR = "屏幕粒子的世界锚点必须为有限坐标";
	private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat(DAMAGE_NUMBER_PATTERN,
			DecimalFormatSymbols.getInstance(Locale.ROOT));
	private static final ArrayDeque<Entry> ACTIVE = new ArrayDeque<>();
	private static final ArrayDeque<Entry> POOL = new ArrayDeque<>();
	private static final Map<MergeKey, List<Entry>> MERGED = new HashMap<>();
	private static ClientLevel level;
	private static long clientTick;

	/// 本人近战仅首次从屏幕中心定位，随后固定世界锚点；合并始终比较实际命中位置。
	public static void receive(DamageParticlePayload payload) {
		Objects.requireNonNull(payload);
		if (levelUnavailable() || !DamageParticleConfig.ENABLED.get()
				|| !level.dimension().equals(payload.dimension())
				|| !Float.isFinite(payload.amount()) || payload.amount() <= 0
				|| !payload.position().isFinite()) {
			return;
		}

		var player = Minecraft.getInstance().player;
		boolean centered = payload.melee() && player != null
				&& payload.attacker().filter(player.getUUID()::equals).isPresent();
		var key = new MergeKey(payload.attacker(), payload.target(), payload.damageType());
		int lifetime = DamageParticleConfig.LIFETIME_TICKS.get();
		boolean mergeEnabled = DamageParticleConfig.MERGE_ENABLED.get();
		if (!mergeEnabled) {
			clearMergeIndex();
		} else if (tryMerge(key, payload, lifetime)) {
			return;
		}

		var entry = acquire(payload.position(), centered, lifetime);
		entry.amount = payload.amount();
		entry.lastHitTick = clientTick;
		updateDamageRatio(entry, payload.maxHealth());
		updateDamageContent(entry);
		if (mergeEnabled) {
			entry.mergeKey = key;
			MERGED.computeIfAbsent(key, _ -> new ArrayList<>()).add(entry);
		}
	}

	/// 自定义内容以自身中心为原点；居中模式仅首次定位，仍要求提供有限世界锚点。
	public static void spawn(IScreenParticleContent content, Vec3 worldAnchor, boolean centered) {
		Objects.requireNonNull(content);
		Objects.requireNonNull(worldAnchor);
		if (!worldAnchor.isFinite()) {
			throw new IllegalArgumentException(INVALID_ANCHOR);
		}

		if (levelUnavailable() || !DamageParticleConfig.ENABLED.get()) {
			return;
		}

		var entry = acquire(worldAnchor, centered, DamageParticleConfig.LIFETIME_TICKS.get());
		entry.content = content;
	}

	public static void tick() {
		if (levelUnavailable()) {
			return;
		}

		if (Minecraft.getInstance().isPaused()) {
			return;
		}

		int poolCapacity = DamageParticleConfig.POOL_CAPACITY.get();
		if (!DamageParticleConfig.ENABLED.get()) {
			while (!ACTIVE.isEmpty()) {
				release(ACTIVE.removeFirst(), poolCapacity);
			}

			trimPool(poolCapacity);
			return;
		}

		clientTick++;
		if (!DamageParticleConfig.MERGE_ENABLED.get()) {
			clearMergeIndex();
		}

		int outlineColor = DamageParticleConfig.OUTLINE_COLOR.get();
		var iterator = ACTIVE.iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			if (entry.expiresTick <= clientTick) {
				iterator.remove();
				release(entry, poolCapacity);
				continue;
			}

			if (entry.formattedAmount != null && entry.outlineColor != outlineColor) {
				entry.outlineColor = outlineColor;
				entry.content = new ScreenTextContent(Component.literal(entry.formattedAmount), outlineColor);
			}
		}

		trimActive(DamageParticleConfig.MAX_ACTIVE.get(), poolCapacity);
		trimPool(poolCapacity);
	}

	/// 提取阶段只把原始值与稳定内容交给渲染器，延迟渲染不得捕获可回收的实例壳。
	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (levelUnavailable() || !DamageParticleConfig.ENABLED.get() || ACTIVE.isEmpty()
				|| Minecraft.getInstance().options.hideGui) {
			return;
		}

		ScreenParticleRenderer.beginFrame(graphics);
		// 使用本地客户端时钟的部分刻；该接口仍会在暂停时返回固定的暂停残余值。
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
		for (var entry : ACTIVE) {
			float age = clientTick - entry.bornTick + partialTick;
			float remaining = entry.expiresTick - clientTick - partialTick;
			if (remaining <= 0) {
				continue;
			}

			if (entry.renderAnchor == null) {
				entry.renderAnchor = entry.centered ? ScreenParticleRenderer.centerAnchor(entry.position) : entry.position;
				if (entry.renderAnchor == null) {
					continue;
				}
			}

			float impactAge = clientTick - entry.lastHitTick + partialTick;
			ScreenParticleRenderer.render(graphics, entry.content, entry.renderAnchor, age, remaining,
					entry.damageRatio, impactAge);
		}
	}

	/// 登录、退出及切换世界时丢弃活跃与空闲内容引用，不关闭字体或纹理管理器拥有的资源。
	public static void clear() {
		MERGED.clear();
		for (var entry : ACTIVE) {
			entry.reset();
		}

		ACTIVE.clear();
		POOL.clear();
		level = null;
		clientTick = 0;
	}

	private static boolean levelUnavailable() {
		var currentLevel = Minecraft.getInstance().level;
		if (level != currentLevel) {
			clear();
			level = currentLevel;
		}

		return level == null;
	}

	private static boolean tryMerge(MergeKey key, DamageParticlePayload payload, int lifetime) {
		var candidates = MERGED.get(key);
		if (candidates == null) {
			return false;
		}

		double radius = DamageParticleConfig.MERGE_RADIUS.get();
		double nearestDistance = radius * radius;
		Entry entry = null;
		for (var candidate : candidates) {
			if (candidate.expiresTick <= clientTick) {
				continue;
			}

			double distance = candidate.position.distanceToSqr(payload.position());
			if (distance <= nearestDistance && (entry == null || distance < nearestDistance)) {
				entry = candidate;
				nearestDistance = distance;
			}
		}

		if (entry == null) {
			return false;
		}

		entry.amount += payload.amount();
		entry.lastHitTick = clientTick;
		entry.expiresTick = clientTick + lifetime;
		updateDamageRatio(entry, payload.maxHealth());
		updateDamageContent(entry);
		return true;
	}

	/// 使用最近命中的生命上限快照；无效分母只停用冲击强度，不丢弃已结算数字。
	private static void updateDamageRatio(Entry entry, float maxHealth) {
		entry.damageRatio = Float.isFinite(maxHealth) && maxHealth > 0 ? (float) (entry.amount / maxHealth) : 0;
	}

	private static void updateDamageContent(Entry entry) {
		String formatted = DAMAGE_FORMAT.format(entry.amount);
		int outlineColor = DamageParticleConfig.OUTLINE_COLOR.get();
		if (formatted.equals(entry.formattedAmount) && entry.outlineColor == outlineColor) {
			return;
		}

		entry.formattedAmount = formatted;
		entry.outlineColor = outlineColor;
		entry.content = new ScreenTextContent(Component.literal(formatted), outlineColor);
	}

	private static Entry acquire(Vec3 position, boolean centered, int lifetime) {
		int poolCapacity = DamageParticleConfig.POOL_CAPACITY.get();
		trimActive(DamageParticleConfig.MAX_ACTIVE.get() - 1, poolCapacity);
		trimPool(poolCapacity);
		var entry = POOL.pollFirst();
		if (entry == null) {
			entry = new Entry();
		}

		entry.position = position;
		entry.centered = centered;
		entry.bornTick = clientTick;
		entry.expiresTick = clientTick + lifetime;
		ACTIVE.addLast(entry);
		return entry;
	}

	private static void trimActive(int capacity, int poolCapacity) {
		while (ACTIVE.size() > capacity) {
			release(ACTIVE.removeFirst(), poolCapacity);
		}
	}

	private static void trimPool(int capacity) {
		while (POOL.size() > capacity) {
			POOL.removeLast();
		}
	}

	private static void clearMergeIndex() {
		if (MERGED.isEmpty()) {
			return;
		}

		MERGED.clear();
		for (var entry : ACTIVE) {
			entry.mergeKey = null;
		}
	}

	private static void release(Entry entry, int poolCapacity) {
		if (entry.mergeKey != null) {
			var candidates = MERGED.get(entry.mergeKey);
			if (candidates != null) {
				candidates.remove(entry);
				if (candidates.isEmpty()) {
					MERGED.remove(entry.mergeKey);
				}
			}
		}

		entry.reset();
		if (POOL.size() < poolCapacity) {
			POOL.addLast(entry);
		}
	}

	private record MergeKey(Optional<UUID> attacker, UUID target, Identifier damageType) {
	}

	private static class Entry {

		private IScreenParticleContent content;
		private Vec3 position;
		private Vec3 renderAnchor;
		private boolean centered;
		private long bornTick;
		private long expiresTick;
		private long lastHitTick;
		private double amount;
		/// NaN 表示非伤害内容，使用普通尺寸且不施加伤害震动。
		private float damageRatio = Float.NaN;
		private String formattedAmount;
		private int outlineColor;
		private MergeKey mergeKey;

		private void reset() {
			content = null;
			position = null;
			renderAnchor = null;
			centered = false;
			bornTick = 0;
			expiresTick = 0;
			lastHitTick = 0;
			amount = 0;
			damageRatio = Float.NaN;
			formattedAmount = null;
			outlineColor = 0;
			mergeKey = null;
		}
	}
}
