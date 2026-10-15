package org.unitego.lobecorp.world.item.animation;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.LcAnimationTransitionSettings;
import org.unitego.lobecorp.animation.LcControllerBlendType;
import org.unitego.lobecorp.animation.LcRotationTransitionMode;
import org.unitego.lobecorp.animation.LcTransitionMode;
import org.unitego.lobecorp.attachment.LcAttachmentBinding;

import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 只管理显式物品显示元数据；绑定、动画与物品对象各自保留原生命周期。
public class LcAttachedItems {
	private static final String RELEASED_BINDING = "Cannot register an item display for a released attachment";
	private static final String EMPTY_STACK = "Item attachment display stack must not be empty";
	private static final LcAnimationTransitionSettings IMMEDIATE = new LcAnimationTransitionSettings(0, 0,
			LcTransitionMode.OVERLAP, LcTransitionMode.OVERLAP, LcRotationTransitionMode.NORMAL,
			LcRotationTransitionMode.NORMAL, LcTransitionMode.OVERLAP, LcRotationTransitionMode.NORMAL,
			LcControllerBlendType.OVERRIDE);
	private static final Map<Level, Map<UUID, Entry>> LEVELS = new IdentityHashMap<>();

	public static synchronized void register(LcAttachmentBinding binding, ItemStack stack, ItemDisplayContext context,
	                                         @Nullable UUID animationId, @Nullable String bone) {
		register(binding, stack, context, animationId, bone, IMMEDIATE);
	}

	@SuppressWarnings("resource")
	public static synchronized void register(LcAttachmentBinding binding, ItemStack stack, ItemDisplayContext context,
	                                         @Nullable UUID animationId, @Nullable String bone,
	                                         LcAnimationTransitionSettings transitions) {
		register(binding, stack, context, animationId, bone, transitions, binding.target().level().getGameTime());
	}

	/// 未来开始时刻只影响显示与淡入；调用方负责将自己的动画时钟暂停到同一刻。
	public static synchronized void register(LcAttachmentBinding binding, ItemStack stack, ItemDisplayContext context,
	                                         @Nullable UUID animationId, @Nullable String bone,
	                                         LcAnimationTransitionSettings transitions, long startedTick) {
		if (binding.isReleased()) {
			throw new IllegalArgumentException(RELEASED_BINDING);
		}

		if (stack.isEmpty()) {
			throw new IllegalArgumentException(EMPTY_STACK);
		}

		var level = binding.target().level();
		LEVELS.computeIfAbsent(level, _ -> new LinkedHashMap<>()).put(binding.id(),
				new Entry(binding, stack.copy(), context, animationId, bone, transitions, startedTick));
	}

	public static synchronized List<LcAttachedItemSnapshot> snapshots(Level level) {
		var entries = LEVELS.get(level);
		if (entries == null) {
			return List.of();
		}

		entries.values().removeIf(entry -> entry.binding.isReleased() || !entry.binding.target().isAvailable());
		var result = entries.values().stream().map(entry -> entry.snapshot(level.getGameTime())).toList();
		if (entries.isEmpty()) {
			LEVELS.remove(level);
		}

		return result;
	}

	/// 调用方必须同时冻结自身动画，直到显示淡出结束后再释放绑定。
	public static synchronized void beginStopping(Level level, UUID bindingId) {
		var entries = LEVELS.get(level);
		var entry = entries == null ? null : entries.get(bindingId);
		if (entry != null && entry.stoppingTick < 0) {
			entry.stoppingTick = level.getGameTime();
		}
	}

	public static synchronized boolean fadeFinished(Level level, UUID bindingId) {
		var entries = LEVELS.get(level);
		var entry = entries == null ? null : entries.get(bindingId);
		return entry == null || entry.stoppingTick >= 0
				&& level.getGameTime() >= entry.stoppingTick + entry.transitions.fadeOutTicks();
	}

	public static synchronized void remove(UUID bindingId) {
		LEVELS.values().forEach(entries -> entries.remove(bindingId));
		LEVELS.values().removeIf(Map::isEmpty);
	}

	public static synchronized void clear(Level level) {
		LEVELS.remove(level);
	}

	/// 只清理失效显示描述，不推进动画、不释放仍有效的绑定。
	public static synchronized void tick(ServerLevel level) {
		var entries = LEVELS.get(level);
		if (entries == null) {
			return;
		}

		entries.values().removeIf(entry -> entry.binding.isReleased() || !entry.binding.target().isAvailable());
		if (entries.isEmpty()) {
			LEVELS.remove(level);
		}
	}

	public static synchronized void clear(MinecraftServer server) {
		LEVELS.keySet().removeIf(level -> level instanceof ServerLevel serverLevel && serverLevel.getServer() == server);
	}

	public static synchronized void prune(MinecraftServer server) {
		var active = new ArrayList<Level>();
		server.getAllLevels().forEach(active::add);
		LEVELS.keySet().removeIf(level -> level instanceof ServerLevel serverLevel
				&& serverLevel.getServer() == server && !active.contains(level));
	}

	public static synchronized void clearClientWorlds() {
		LEVELS.keySet().removeIf(Level::isClientSide);
	}

	private static class Entry {
		private final LcAttachmentBinding binding;
		private final ItemStack stack;
		private final ItemDisplayContext context;
		@Nullable
		private final UUID animationId;
		@Nullable
		private final String bone;
		private final LcAnimationTransitionSettings transitions;
		private final long startedTick;
		private long stoppingTick = -1;

		private Entry(LcAttachmentBinding binding, ItemStack stack, ItemDisplayContext context,
		              @Nullable UUID animationId, @Nullable String bone,
		              LcAnimationTransitionSettings transitions, long startedTick) {
			this.binding = binding;
			this.stack = stack;
			this.context = context;
			this.animationId = animationId;
			this.bone = bone;
			this.transitions = transitions;
			this.startedTick = startedTick;
		}

		private LcAttachedItemSnapshot snapshot(long serverTick) {
			return new LcAttachedItemSnapshot(binding.id(), stack, context, animationId, bone, transitions,
					startedTick, stoppingTick, serverTick);
		}
	}
}
