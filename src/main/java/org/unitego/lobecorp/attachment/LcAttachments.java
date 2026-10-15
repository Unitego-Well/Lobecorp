package org.unitego.lobecorp.attachment;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 同一入口可在服务端或客户端显式绑定；两侧的 Level、写入权和生命周期相互独立。
public class LcAttachments {
	private static final String ALREADY_DRIVEN = "Attachment target already has an active spatial driver";
	private static final Map<Level, State> LEVELS = new IdentityHashMap<>();

	public static synchronized LcAttachmentBinding bind(ILcTransformTarget target, ILcAttachmentAnchor anchor) {
		return new LcAttachmentBinding(target, anchor);
	}

	public static synchronized @Nullable LcAttachmentBinding get(Level level, UUID id) {
		var state = LEVELS.get(level);
		return state == null ? null : state.bindings.get(id);
	}

	public static synchronized List<LcAttachmentBinding> bindings(Level level) {
		var state = LEVELS.get(level);
		return state == null ? List.of() : state.bindings.values().stream()
				.filter(binding -> !binding.isReleased()).toList();
	}

	public static synchronized List<LcAttachmentSnapshot> snapshots(Level level) {
		return bindings(level).stream().map(LcAttachmentBinding::snapshot).toList();
	}

	/// 根运动等其他显式驱动共享同一写入权，避免两个系统同时修改同一目标。
	public static synchronized void claim(Level level, Object identity, Object writer) {
		var state = LEVELS.computeIfAbsent(level, _ -> new State());
		var previous = state.writers.putIfAbsent(identity, writer);
		if (previous != null && previous != writer) {
			throw new IllegalArgumentException(ALREADY_DRIVEN);
		}
	}

	public static synchronized void releaseWriter(Level level, Object identity, Object writer) {
		var state = LEVELS.get(level);
		if (state == null) {
			return;
		}

		state.writers.remove(identity, writer);
		discardEmpty(level, state);
	}

	public static synchronized void register(LcAttachmentBinding binding) {
		claim(binding.target().level(), binding.target().identity(), binding);
		LEVELS.get(binding.target().level()).bindings.put(binding.id(), binding);
	}

	public static synchronized void unregister(LcAttachmentBinding binding) {
		var state = LEVELS.get(binding.target().level());
		if (state == null) {
			return;
		}

		state.bindings.remove(binding.id(), binding);
		releaseWriter(binding.target().level(), binding.target().identity(), binding);
	}

	public static synchronized void tick(Level level) {
		Throwable failure = null;
		for (var binding : bindings(level)) {
			try {
				binding.update();
			} catch (RuntimeException | Error exception) {
				binding.release();
				if (failure == null) {
					failure = exception;
				} else {
					failure.addSuppressed(exception);
				}
			}
		}

		if (failure instanceof RuntimeException exception) {
			throw exception;
		}

		if (failure instanceof Error error) {
			throw error;
		}
	}

	public static synchronized void clear(Level level) {
		var state = LEVELS.remove(level);
		if (state == null) {
			return;
		}

		new ArrayList<>(state.bindings.values()).forEach(LcAttachmentBinding::release);
		state.writers.clear();
	}

	public static synchronized List<ServerLevel> prune(MinecraftServer server) {
		var active = new ArrayList<ServerLevel>();
		server.getAllLevels().forEach(active::add);
		var unloaded = LEVELS.keySet().stream().filter(level -> level instanceof ServerLevel serverLevel
						&& serverLevel.getServer() == server && !active.contains(serverLevel))
				.map(ServerLevel.class::cast).toList();
		unloaded.forEach(LcAttachments::clear);
		return unloaded;
	}

	public static synchronized void clear(MinecraftServer server) {
		LEVELS.keySet().stream().filter(level -> level instanceof ServerLevel serverLevel
				&& serverLevel.getServer() == server).toList().forEach(LcAttachments::clear);
	}

	public static synchronized void clearClientWorlds() {
		LEVELS.keySet().stream().filter(Level::isClientSide).toList().forEach(LcAttachments::clear);
	}

	private static void discardEmpty(Level level, State state) {
		if (state.bindings.isEmpty() && state.writers.isEmpty()) {
			LEVELS.remove(level, state);
		}
	}

	private static class State {
		private final Map<UUID, LcAttachmentBinding> bindings = new LinkedHashMap<>();
		private final Map<Object, Object> writers = new IdentityHashMap<>();
	}
}
