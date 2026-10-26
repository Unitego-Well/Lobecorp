package org.unitego.lobecorp.client.bench;

import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/// 集成服务端真实实体负载：
/// - 生成前强制加载全部目标区块、关闭 mob 自然生成，并核对 addFreshEntity 后 level.getEntity 立即可查；
/// - 每个客户端 tick 提交服务端审计：服务端缺失实体按同一 UUID/同样配置补生成（最多 5 次），
///   服务端仍在但客户端持续缺失时删除后补生成以强制重新追踪，AI 实体偏离锚点过远时拉回；
/// - teardown 删除实体、释放强制区块并恢复 gamerule。
/// 基准场景只保存 UUID 与重建工厂，不跨线程访问 Entity 状态。
final class BenchServerWorkload {
	private static final long CLEANUP_TIMEOUT_SECONDS = 5;
	private static final int MAX_SPAWN_ATTEMPTS = 5;
	private static final int MAX_RECREATE_ATTEMPTS = 5;
	private static final int CLIENT_MISSING_TICKS = 40;
	private static final int RECREATE_COOLDOWN_TICKS = 60;
	private static final int MAX_ISSUES = 200;
	private static final int MAX_ISSUE_LENGTH = 240;

	/// 生成规格：x/z 为方块坐标，y 由服务端按地面高度计算；factory 只负责实体类型与静态配置。
	record Spec(UUID uuid, int x, int z, double leashRadius, Function<ServerLevel, Entity> factory) {
	}

	private record Entry(Spec spec, double x, double y, double z) {
		double distanceSquared(Entity entity) {
			double dx = entity.getX() - x;
			double dy = entity.getY() - y;
			double dz = entity.getZ() - z;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	/// 环境 JSON 可写的只读汇总；不暴露 Entity 引用。
	record Report(int expected, int planned, int serverPresent, int clientSynced, int respawns, int spawnFailures,
	              int leashed, int recreated, String difficulty, String previousDifficulty, boolean gameruleDisabled,
	              Boolean previousSpawnMobs, Boolean previousSpawnMonsters, List<String> issues) {
	}

	private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
	private final List<UUID> order = new CopyOnWriteArrayList<>();
	private final List<ChunkPos> forcedChunks = new CopyOnWriteArrayList<>();
	private final Map<UUID, Integer> clientMissingTicks = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> pendingRecreate = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> recreateCooldowns = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> spawnFailures = new ConcurrentHashMap<>();
	private final Map<UUID, Long> lastSpawnGameTime = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> recreateAttempts = new ConcurrentHashMap<>();
	private final AtomicInteger respawns = new AtomicInteger();
	private final AtomicInteger spawnFailuresTotal = new AtomicInteger();
	private final AtomicInteger leashed = new AtomicInteger();
	private final AtomicInteger recreated = new AtomicInteger();
	private final List<String> issues = new CopyOnWriteArrayList<>();
	private volatile boolean submitted;
	private volatile boolean initialPassDone;
	private volatile Throwable fatalFailure;
	private volatile int serverPresent;
	private volatile boolean gameruleDisabled;
	private volatile Boolean previousSpawnMobs;
	private volatile Boolean previousSpawnMonsters;
	private volatile boolean gameruleRestored;
	private volatile Difficulty previousDifficulty;
	private volatile String serverDifficulty = "";
	private volatile boolean difficultyForced;
	private int waitTicks;

	/// 必须在客户端线程调用：服务端线程强制区块、关闭自然刷怪并完成首轮生成。
	void spawn(BenchClientContext context, List<Spec> specs) {
		if (submitted) {
			throw new IllegalStateException("基准服务端实体负载已提交过生成");
		}
		submitted = true;
		if (specs.isEmpty()) {
			initialPassDone = true;
			return;
		}
		var server = context.minecraft().getSingleplayerServer();
		if (server == null) {
			throw new IllegalStateException("基准场景需要集成服务端，但当前没有单人服务端");
		}
		var planned = List.copyOf(specs);
		server.execute(() -> {
			try {
				var level = server.overworld();
				// 和平难度会每 tick discard Monster（与 persistenceRequired 无关），先切普通并以服务端实际难度为准。
				previousDifficulty = level.getDifficulty();
				if (previousDifficulty == Difficulty.PEACEFUL) {
					level.getServer().setDifficulty(Difficulty.NORMAL, true);
					difficultyForced = true;
				}
				serverDifficulty = level.getDifficulty().name();
				disableSpawnRules(level);
				// 先加载全部目标区块，再统一生成，避免 addFreshEntity 落到未加载区块或半成品状态。
				for (var spec : planned) {
					forceChunk(level, spec.x(), spec.z());
				}
				for (var spec : planned) {
					var entry = new Entry(spec, spec.x() + 0.5,
							level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spec.x(), spec.z()),
							spec.z() + 0.5);
					entries.put(spec.uuid(), entry);
					order.add(spec.uuid());
					var stale = level.getEntity(spec.uuid());
					if (stale != null) {
						stale.discard();
						issue("stale_discarded uuid=" + spec.uuid() + " type=" + stale.getType());
					}
				}
				for (var spec : planned) {
					ensureSpawned(level, spec.uuid());
				}
				initialPassDone = true;
			} catch (Throwable failure) {
				fatalFailure = failure;
			}
		});
	}

	/// 每个 tick 调用一次：更新客户端缺失计数，并提交服务端审计（补生成/重新追踪/拉回偏离锚点）。
	void audit(BenchClientContext context) {
		if (!initialPassDone || fatalFailure != null) {
			return;
		}
		var missing = new ArrayList<UUID>();
		for (var uuid : order) {
			if (context.level().getEntity(uuid) != null) {
				clientMissingTicks.remove(uuid);
				continue;
			}
			if (clientMissingTicks.merge(uuid, 1, Integer::sum) >= CLIENT_MISSING_TICKS) {
				missing.add(uuid);
			}
		}
		var server = context.minecraft().getSingleplayerServer();
		if (server == null) {
			return;
		}
		server.execute(() -> auditOnServer(server.overworld(), missing));
	}

	private void auditOnServer(ServerLevel level, List<UUID> clientMissing) {
		if (fatalFailure != null || !initialPassDone) {
			return;
		}
		try {
			serverDifficulty = level.getDifficulty().name();
			for (var uuid : List.copyOf(pendingRecreate.keySet())) {
				var remaining = pendingRecreate.computeIfPresent(uuid, (_, value) -> value <= 1 ? null : value - 1);
				if (remaining == null) {
					ensureSpawned(level, uuid);
				}
			}
			for (var uuid : List.copyOf(recreateCooldowns.keySet())) {
				recreateCooldowns.computeIfPresent(uuid, (_, value) -> value <= 1 ? null : value - 1);
			}
			for (var uuid : clientMissing) {
				if (entries.containsKey(uuid) && !pendingRecreate.containsKey(uuid)
						&& recreateCooldowns.getOrDefault(uuid, 0) == 0) {
					requestRecreate(level, uuid);
				}
			}
			int present = 0;
			for (var uuid : order) {
				var entity = level.getEntity(uuid);
				if (entity == null || entity.isRemoved()) {
					entity = ensureSpawned(level, uuid);
				}
				if (entity == null) {
					continue;
				}
				present++;
				var entry = entries.get(uuid);
				if (entry == null) {
					continue;
				}
				double leash = entry.spec().leashRadius();
				if (leash > 0 && entry.distanceSquared(entity) > leash * leash) {
					entity.teleportTo(entry.x(), entry.y(), entry.z());
					entity.setDeltaMovement(Vec3.ZERO);
					leashed.incrementAndGet();
				}
			}
			serverPresent = present;
		} catch (Throwable failure) {
			fatalFailure = failure;
		}
	}

	/// 只在服务端线程调用：强制加载区块并验证区块可查。
	private void forceChunk(ServerLevel level, int x, int z) {
		var chunk = new ChunkPos(x >> 4, z >> 4);
		level.setChunkForced(chunk.x(), chunk.z(), true);
		if (!forcedChunks.contains(chunk)) {
			forcedChunks.add(chunk);
		}
		level.getChunk(chunk.x(), chunk.z());
		if (!level.getChunkSource().hasChunk(chunk.x(), chunk.z())) {
			throw new IllegalStateException("基准目标区块加载失败：" + chunk);
		}
	}

	/// 只在服务端线程调用：确保指定 UUID 的实体在服务端存在；失败保留到下一次审计重试。
	private Entity ensureSpawned(ServerLevel level, UUID uuid) {
		var entry = entries.get(uuid);
		if (entry == null) {
			return null;
		}
		var existing = level.getEntity(uuid);
		if (existing != null && !existing.isRemoved()) {
			return existing;
		}
		if (existing != null) {
			// 已经存在但被立即移除：记录移除原因/难度/区块状态，避免只看到“无明细”。
			issue("entity_removed uuid=" + uuid + " reason=" + existing.getRemovalReason()
					+ " difficulty=" + level.getDifficulty()
					+ " peaceful=" + (level.getDifficulty() == Difficulty.PEACEFUL)
					+ " chunkLoaded=" + chunkLoaded(level, entry)
					+ " pos=" + formatPosition(existing.getX(), existing.getY(), existing.getZ())
					+ " anchor=" + formatPosition(entry.x(), entry.y(), entry.z()));
		} else if (spawnFailures.getOrDefault(uuid, 0) > 0 || lastSpawnGameTime.containsKey(uuid)) {
			issue("entity_missing_after_spawn uuid=" + uuid + " difficulty=" + level.getDifficulty()
					+ " chunkLoaded=" + chunkLoaded(level, entry) + " anchor="
					+ formatPosition(entry.x(), entry.y(), entry.z())
					+ " lastGameTime=" + lastSpawnGameTime.getOrDefault(uuid, -1L));
		}
		boolean known = lastSpawnGameTime.containsKey(uuid);
		int failures = spawnFailures.merge(uuid, 1, Integer::sum);
		if (failures > MAX_SPAWN_ATTEMPTS) {
			fatalFailure = new IllegalStateException("基准服务端实体连续补生成失败超过 " + MAX_SPAWN_ATTEMPTS
					+ " 次：" + uuid + "，最近问题：" + issuesSummary());
			return null;
		}
		try {
			var stale = level.getEntity(uuid);
			if (stale != null) {
				stale.discard();
			}
			var entity = entry.spec().factory().apply(level);
			if (entity == null) {
				throw new IllegalStateException("实体工厂返回 null");
			}
			entity.setUUID(uuid);
			entity.setPos(entry.x(), entry.y(), entry.z());
			if (!level.addFreshEntity(entity)) {
				throw new IllegalStateException("addFreshEntity 返回 false（UUID 可能仍被旧实体占用）");
			}
			if (level.getEntity(uuid) != entity) {
				throw new IllegalStateException("addFreshEntity 成功但 level.getEntity(uuid) 不可查");
			}
			spawnFailures.remove(uuid);
			lastSpawnGameTime.put(uuid, level.getGameTime());
			if (known) {
				respawns.incrementAndGet();
			}
			return entity;
		} catch (Throwable failure) {
			spawnFailuresTotal.incrementAndGet();
			issue("spawn_failed uuid=" + uuid + " attempt=" + failures
					+ " pos=" + formatPosition(entry.x(), entry.y(), entry.z())
					+ " chunkLoaded=" + chunkLoaded(level, entry) + " difficulty=" + level.getDifficulty()
					+ " reason=" + failure);
			return null;
		}
	}

	private boolean chunkLoaded(ServerLevel level, Entry entry) {
		var chunk = new ChunkPos((int) Math.floor(entry.x()) >> 4, (int) Math.floor(entry.z()) >> 4);
		return level.getChunkSource().hasChunk(chunk.x(), chunk.z());
	}

	private static String formatPosition(double x, double y, double z) {
		return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", x, y, z);
	}

	private void requestRecreate(ServerLevel level, UUID uuid) {
		int attempts = recreateAttempts.merge(uuid, 1, Integer::sum);
		if (attempts > MAX_RECREATE_ATTEMPTS) {
			issue("client_missing_give_up uuid=" + uuid + " attempts=" + attempts);
			return;
		}
		var entity = level.getEntity(uuid);
		if (entity != null) {
			entity.discard();
		}
		pendingRecreate.put(uuid, 2);
		recreateCooldowns.put(uuid, RECREATE_COOLDOWN_TICKS);
		recreated.incrementAndGet();
		issue("client_missing_recreate uuid=" + uuid + " attempt=" + attempts
				+ " action=discard_then_respawn_same_uuid");
	}

	private void disableSpawnRules(ServerLevel level) {
		var rules = level.getGameRules();
		previousSpawnMobs = rules.get(GameRules.SPAWN_MOBS);
		previousSpawnMonsters = rules.get(GameRules.SPAWN_MONSTERS);
		rules.set(GameRules.SPAWN_MOBS, false, level.getServer());
		rules.set(GameRules.SPAWN_MONSTERS, false, level.getServer());
		gameruleDisabled = true;
	}

	List<UUID> uuids() {
		return List.copyOf(order);
	}

	int synced(BenchClientContext context) {
		int count = 0;
		for (var uuid : order) {
			if (context.level().getEntity(uuid) != null) {
				count++;
			}
		}
		return count;
	}

	int serverPresent() {
		return serverPresent;
	}

	/// 每个 tick 调用一次；在超时前返回 false，超时或服务端生成异常时抛出。
	boolean await(BenchClientContext context, int expected, int timeoutTicks, String label) {
		if (fatalFailure != null) {
			throw new AssertionError(label + " 服务端实体负载失败：" + fatalFailure + "；" + issuesSummary(), fatalFailure);
		}
		if (initialPassDone && order.size() == expected && serverPresent == expected && synced(context) == expected) {
			return true;
		}
		if (++waitTicks > timeoutTicks) {
			throw new AssertionError(label + " 等待实体同步超时：planned=" + order.size()
					+ ", serverPresent=" + serverPresent + ", clientSynced=" + synced(context)
					+ ", expected=" + expected + ", respawns=" + respawns.get()
					+ ", spawnFailures=" + spawnFailuresTotal.get() + ", recreated=" + recreated.get()
					+ ", leashed=" + leashed.get() + "；" + issuesSummary());
		}
		return false;
	}

	Report report(BenchClientContext context) {
		return new Report(order.size(), entries.size(), serverPresent, synced(context), respawns.get(),
				spawnFailuresTotal.get(), leashed.get(), recreated.get(), serverDifficulty,
				previousDifficulty == null ? "" : previousDifficulty.name(), gameruleDisabled,
				previousSpawnMobs, previousSpawnMonsters, List.copyOf(issues));
	}

	/// teardown 调用：阻塞等待服务端删除实体、释放强制区块并恢复 gamerule；超时或恢复失败作为清理失败上报。
	void discard(BenchClientContext context) {
		var server = context.minecraft().getSingleplayerServer();
		if (server == null) {
			order.clear();
			entries.clear();
			forcedChunks.clear();
			return;
		}
		var entities = List.copyOf(order);
		var chunks = List.copyOf(forcedChunks);
		var done = new CompletableFuture<Void>();
		server.execute(() -> {
			try {
				var level = server.overworld();
				for (var uuid : entities) {
					var entity = level.getEntity(uuid);
					if (entity != null) {
						entity.discard();
					}
				}
				for (var chunk : chunks) {
					level.setChunkForced(chunk.x(), chunk.z(), false);
				}
				restoreDifficulty(level);
				restoreSpawnRules(level);
				done.complete(null);
			} catch (Throwable failure) {
				done.completeExceptionally(failure);
			}
		});
		try {
			done.get(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (gameruleDisabled && !gameruleRestored) {
				throw new IllegalStateException("基准 gamerule 未恢复");
			}
		} catch (TimeoutException exception) {
			throw new IllegalStateException("基准服务端实体清理超时", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("基准服务端实体清理被中断", exception);
		} catch (ExecutionException exception) {
			throw new IllegalStateException("基准服务端实体清理失败", exception.getCause());
		} finally {
			order.clear();
			entries.clear();
			forcedChunks.clear();
			clientMissingTicks.clear();
			pendingRecreate.clear();
			recreateCooldowns.clear();
			spawnFailures.clear();
			lastSpawnGameTime.clear();
			recreateAttempts.clear();
		}
	}

	private void restoreDifficulty(ServerLevel level) {
		if (difficultyForced && previousDifficulty != null) {
			level.getServer().setDifficulty(previousDifficulty, true);
			difficultyForced = false;
		}
	}

	private void restoreSpawnRules(ServerLevel level) {
		if (!gameruleDisabled) {
			gameruleRestored = true;
			return;
		}
		var rules = level.getGameRules();
		if (previousSpawnMobs != null) {
			rules.set(GameRules.SPAWN_MOBS, previousSpawnMobs, level.getServer());
		}
		if (previousSpawnMonsters != null) {
			rules.set(GameRules.SPAWN_MONSTERS, previousSpawnMonsters, level.getServer());
		}
		gameruleRestored = true;
	}

	private void issue(String message) {
		issues.add(message.length() <= MAX_ISSUE_LENGTH ? message : message.substring(0, MAX_ISSUE_LENGTH));
		while (issues.size() > MAX_ISSUES) {
			issues.removeFirst();
		}
	}

	private String issuesSummary() {
		return issues.isEmpty() ? "无明细"
				: String.join(" | ", issues.subList(Math.max(0, issues.size() - 6), issues.size()));
	}
}
