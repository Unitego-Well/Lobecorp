package org.unitego.lobecorp.animation.runtime;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.unitego.lobecorp.attachment.ILcTransformTarget;
import org.unitego.lobecorp.attachment.LcAttachments;
import org.unitego.lobecorp.attachment.adapter.EntityAnimationTarget;
import org.unitego.lobecorp.animation.player.LcAnimationTermination;
import org.unitego.lobecorp.animation.resource.LcAnimationResources;
import org.unitego.lobecorp.registry.animation.LcAnimationDefinitions;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/// 世界只持有播放与空间绑定；目标对象仍由自身系统管理，卸载时显式释放世界引用。
public class LcServerAnimations {
	private static final String LEVEL_CLEARING = "Animation level is being cleared";
	private static final Map<ServerLevel, LcServerAnimations> LEVELS = new IdentityHashMap<>();
	private static final Map<ServerLevel, Boolean> CLEARING_LEVELS = new IdentityHashMap<>();
	private static final Map<MinecraftServer, Boolean> CLEARING_SERVERS = new IdentityHashMap<>();
	private final Map<UUID, LcAnimationInstance> instances = new LinkedHashMap<>();
	private final Map<UUID, LcAnimationSnapshot> terminalSnapshots = new LinkedHashMap<>();

	public static LcAnimationInstance play(Entity entity, Identifier definitionId) {
		var owner = new EntityAnimationTarget(entity, Set.of(EntityAnimationTarget.Channel.POSITION));
		return play(entity, owner, definitionId);
	}

	public static LcAnimationInstance play(Entity entity, ILcTransformTarget owner, Identifier definitionId) {
		var level = (ServerLevel) entity.level();
		requirePlayable(level);
		var definition = LcAnimationDefinitions.resolve(definitionId);
		var content = LcAnimationResources.get(level.getServer()).resolve(definition.modelId(), definition.animationId());
		var instance = new LcAnimationInstance(level, entity, owner, definitionId, definition, content);
		add(level, instance);
		return instance;
	}

	public static LcAnimationInstance play(ServerLevel level, ILcTransformTarget owner, Identifier definitionId) {
		requirePlayable(level);
		var definition = LcAnimationDefinitions.resolve(definitionId);
		var content = LcAnimationResources.get(level.getServer()).resolve(definition.modelId(), definition.animationId());
		var instance = new LcAnimationInstance(level, null, owner, definitionId, definition, content);
		add(level, instance);
		return instance;
	}

	public static void tick(Entity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		LcServerAnimations runtime = LEVELS.get(level);

		if (runtime != null) {
			new ArrayList<>(runtime.instances.values()).stream()
					.filter(instance -> instance.sourceEntity() == entity).forEach(LcAnimationInstance::advance);
		}
	}

	public static void finishTick(ServerLevel level) {
		LcServerAnimations runtime = LEVELS.get(level);

		if (runtime == null) {
			return;
		}

		var current = new ArrayList<>(runtime.instances.values());
		current.forEach(LcAnimationInstance::advance);
		current.stream().filter(LcAnimationInstance::isStopped).forEach(instance -> {
			if (instance.playerPolicy() != null) {
				var snapshot = instance.snapshot();
				if (snapshot.termination().allowsFade()) {
					runtime.terminalSnapshots.putIfAbsent(instance.id(), snapshot);
				}
			}
		});
		runtime.instances.values().removeIf(LcAnimationInstance::isStopped);
		runtime.terminalSnapshots.values().removeIf(snapshot -> terminalExpired(snapshot, level.getGameTime()));

		if (runtime.instances.isEmpty() && runtime.terminalSnapshots.isEmpty()) {
			LEVELS.remove(level, runtime);
		}
	}

	private static boolean terminalExpired(LcAnimationSnapshot snapshot, long tick) {
		var policy = snapshot.playerPolicy();
		return policy == null || tick > snapshot.stoppedTick() + Math.max(1, policy.transitions().fadeOutTicks());
	}

	public static List<LcAnimationSnapshot> snapshots(Entity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return List.of();
		}

		return snapshots(level).stream().filter(snapshot -> snapshot.ownerEntityId() == entity.getId()).toList();
	}

	public static List<LcAnimationSnapshot> snapshots(ServerLevel level) {
		LcServerAnimations runtime = LEVELS.get(level);
		if (runtime == null) {
			return List.of();
		}

		// 帧末结算之后停止的玩家播放也要立即发送终止帧，避免先被当作失效来源硬移除。
		var snapshots = new ArrayList<>(runtime.instances.values().stream()
				.filter(instance -> !instance.isStopped() || instance.playerPolicy() != null)
				.map(LcAnimationInstance::snapshot)
				.filter(snapshot -> !snapshot.stopped() || snapshot.termination().allowsFade()).toList());
		runtime.terminalSnapshots.values().forEach(snapshot -> snapshots.add(snapshot.atServerTick(level.getGameTime())));
		return List.copyOf(snapshots);
	}

	public static void clear(Entity entity) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}

		LcServerAnimations runtime = LEVELS.get(level);

		if (runtime != null) {
			runtime.terminalSnapshots.values().removeIf(snapshot -> snapshot.ownerEntityId() == entity.getId());
			cleanAll(new ArrayList<>(runtime.instances.values()).stream()
							.filter(instance -> instance.sourceEntity() == entity).toList(),
					instance -> instance.stop(LcAnimationTermination.UNAVAILABLE));
		}
	}

	public static void clear(ServerLevel level) {
		if (CLEARING_LEVELS.put(level, true) != null) {
			return;
		}

		try {
			LcServerAnimations runtime = LEVELS.remove(level);

			if (runtime != null) {
				cleanAll(new ArrayList<>(runtime.instances.values()),
						instance -> instance.stop(LcAnimationTermination.WORLD_CLEAR));
			}
		} finally {
			CLEARING_LEVELS.remove(level);
		}
	}

	public static List<ServerLevel> prune(MinecraftServer server) {
		var active = new ArrayList<ServerLevel>();
		server.getAllLevels().forEach(active::add);
		var unloaded = LEVELS.keySet().stream().filter(level -> level.getServer() == server && !active.contains(level)).toList();
		cleanAll(unloaded, LcServerAnimations::clear);
		return unloaded;
	}

	public static void clear(MinecraftServer server) {
		if (CLEARING_SERVERS.put(server, true) != null) {
			return;
		}

		try {
			cleanAll(LEVELS.keySet().stream().filter(level -> level.getServer() == server).toList(), LcServerAnimations::clear);
		} finally {
			CLEARING_SERVERS.remove(server);
		}
	}

	private static void requirePlayable(ServerLevel level) {
		if (CLEARING_LEVELS.containsKey(level) || CLEARING_SERVERS.containsKey(level.getServer())) {
			throw new IllegalStateException(LEVEL_CLEARING);
		}
	}

	private static void add(ServerLevel level, LcAnimationInstance instance) {
		requirePlayable(level);

		if (instance.definition().rootMotion() != null) {
			LcAttachments.claim(level, instance.ownerTarget().identity(), instance);
		}

		var runtime = LEVELS.computeIfAbsent(level, _ -> new LcServerAnimations());
		runtime.instances.put(instance.id(), instance);

		try {
			instance.startActions();
		} catch (RuntimeException | Error exception) {
			runtime.instances.remove(instance.id());

			try {
				instance.stop();
			} catch (RuntimeException | Error cleanup) {
				exception.addSuppressed(cleanup);
			}

			if (runtime.instances.isEmpty() && runtime.terminalSnapshots.isEmpty()) {
				LEVELS.remove(level, runtime);
			}

			throw exception;
		}
	}

	/// 一个用户回调失败时仍释放其余实例，保留首个异常与后续清理异常。
	private static <T> void cleanAll(List<T> values, Consumer<T> cleanup) {
		Throwable failure = null;

		for (T value : values) {
			try {
				cleanup.accept(value);
			} catch (RuntimeException | Error exception) {
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
}
