package org.unitego.lobecorp.client.bench;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.client.photon.performance.PhotonPerformanceCapture;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import org.unitego.lobecorp.mixin.PhotonBenchMixinPlugin;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;

import org.unitego.lobecorp.client.photon.runtime.QueenOwnedLaserEffects;
import org.unitego.lobecorp.client.photon.runtime.QueenBeamEffects;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;
import org.unitego.lobecorp.registry.entity.skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.world.entity.skill.IEntitySkillAccess;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.LaserSkill;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.world.entity.LivingEntity;
import jdk.jfr.FlightRecorder;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/// 独立混合场景；可见世界render HEAD为零点，五秒生成，按场景自然采集二十五或四十五秒。
public class PhotonHybridMatrixCapture {
    public static final int SPAWN_SECONDS = 5;
    public static final int CAPTURE_SECONDS = PhotonHybridQueenScenario.ID.equals(System.getProperty("modBench.scenarios", "")) ? 25 : 45;
    public static final String SHADER_PROPERTY = "lobecorp.photon.hybrid.expectedShader";
    public static final String RUN_PROPERTY = "lobecorp.photon.hybrid.runId";
    private static volatile Session session;

    /// 原技能推进前后只读观察；不额外推进状态机，也不改写技能数据。
    public static void observeSkillTick(LivingEntity entity) {
        var value = session;
        if (!selected() || value == null || !value.laser || entity.level().isClientSide()
                || !(entity instanceof TheQueenOfHatred) || value.world == null || !value.world.isDone()
                || value.world.isCompletedExceptionally()) {
            return;
        }
        var spawned = value.spawn.getNow(null);
        if (spawned == null || !entity.getUUID().equals(spawned.uuid)) {
            return;
        }
        try {
            value.observeServer(value.world.join());
        } catch (RuntimeException | LinkageError problem) {
            value.fail(problem);
        }
    }

    public static boolean selected() {
        var id = System.getProperty("modBench.scenarios", "");
        return PhotonHybridQueenScenario.ID.equals(id) || PhotonHybridLaserScenario.ID.equals(id) || PhotonHybridBlankScenario.ID.equals(id);
    }

    private static Session current() {
        if (session == null) {
            session = new Session();
        }
        return session;
    }

    public static void loadingFrame(Minecraft minecraft) {
        if (!selected()) {
            return;
        }
        var value = current();
        try {
            value.loading(minecraft, true);
        } catch (RuntimeException | LinkageError problem) {
            value.fail(problem);
        }
    }

    public static void beforeRender(Minecraft minecraft) {
        if (!selected()) {
            return;
        }
        var value = current();
        try {
            value.beforeRender(minecraft);
        } catch (RuntimeException | LinkageError problem) {
            value.fail(problem);
        }
    }

    public static void afterFrame(Minecraft minecraft) {
        if (!selected() || session == null) {
            return;
        }
        try {
            session.afterFrame(minecraft);
        } catch (RuntimeException | LinkageError problem) {
            session.fail(problem);
        }
    }

    public static void setup(BenchClientContext context, boolean queen) {
        var value = current();
        value.require(queen == value.queen, "entry_variant_mismatch");
        value.require(PhotonBenchMixinPlugin.preservedWindowApplied(), "entry_window_mixin_not_loaded");
        value.require(PhotonRuntimeFactory.mode() == PhotonRuntimeFactory.Mode.HYBRID, "hybrid_requires_hybrid_creation");
        value.require(QueenOwnedLaserEffects.enabled(), "hybrid_requires_owned_laser");
        value.setupNs = System.nanoTime();
        if (value.spark == null) {
            value.spark = PhotonBenchSpark.start(context.minecraft(), value.label);
        }
    }

    public static BenchClientStepResult step(BenchClientContext context) throws Exception {
        var value = current();
        try {
            value.afterFrame(context.minecraft());
            // 业务门禁失败仍保留自然的完整采集窗口。
            if (value.armed && !value.finished && PhotonFrameCapture.running()) {
                return BenchClientStepResult.CONTINUE;
            }
            if (value.failure == null && value.finished) {
                value.cleanup();
                if (!value.cleanupSettled()) {
                    return BenchClientStepResult.CONTINUE;
                }
                value.require(value.cleanupDone(), "entry_cleanup_failed");
                if (value.spark == null || !value.spark.poll()) {
                    return BenchClientStepResult.CONTINUE;
                }
                if (!value.spark.complete()) {
                    if (!value.sparkStopping) {
                        value.spark.stop();
                        value.sparkStopping = true;
                    }
                    return BenchClientStepResult.CONTINUE;
                }
                var profile = PhotonPerformanceCapture.status();
                if (profile.get("exporting").getAsBoolean()) {
                    return BenchClientStepResult.CONTINUE;
                }
                value.validate(profile);
                value.verified = true;
                value.archive(context, profile);
                return BenchClientStepResult.COMPLETE;
            }
        } catch (Exception | LinkageError problem) {
            value.fail(problem);
        }
        if (value.failure == null) {
            return BenchClientStepResult.CONTINUE;
        }
        if (value.armed && !value.finished && PhotonFrameCapture.running()) {
            return BenchClientStepResult.CONTINUE;
        }
        value.cleanup();
        if (!value.cleanupSettled() || PhotonPerformanceCapture.status().get("exporting").getAsBoolean()) {
            return BenchClientStepResult.CONTINUE;
        }
        try {
            if (value.spark != null && !value.spark.complete()) {
                if (value.spark.poll() && !value.sparkStopping) {
                    value.spark.stop();
                    value.sparkStopping = true;
                }
                return BenchClientStepResult.CONTINUE;
            }
        } catch (RuntimeException | LinkageError problem) {
            value.rawArchiveError = problem.toString();
            value.fail(problem);
        }
        value.archiveFailure(context);
        throw new IllegalStateException("entry25_failed", value.failure);
    }

    public static void verify(BenchClientContext context, boolean queen) {
        var value = current();
        value.require(value.archived && value.verified && value.queen == queen, "entry_not_verified");
        value.validate(PhotonPerformanceCapture.status());
    }

    public static void teardown(BenchClientContext context) {
        var value = current();
        if (PhotonPerformanceCapture.active() && value.armed) {
            PhotonPerformanceCapture.cancel();
        }
        value.cleanup();
        if (value.scheduler != null) {
            value.scheduler.shutdownNow();
        }
        if (value.spark != null) {
            value.spark.close();
        }
    }

    private record World(MinecraftServer server, ServerLevel level, UUID player, Vec3 origin) {
    }

    private record Spawn(boolean present, UUID uuid, int id, long requestedNs, long createNs, long addedNs) {
    }

    private static class Session {
        private static final int MAX_LOADING_FRAMES = 32768;
        /// 50ms 服务端 tick 的五倍；只限制调度，不限制实体构造耗时。
        private static final long MAX_SPAWN_DELAY_NS = TimeUnit.MILLISECONDS.toNanos(250);
        private final boolean laser = PhotonHybridLaserScenario.ID.equals(System.getProperty("modBench.scenarios", ""));
        private final List<CompletableFuture<JsonObject>> casts = new CopyOnWriteArrayList<>();
        private final JsonArray serverSamples = new JsonArray();
        private final QueenOwnedLaserEffects.Statistics ownedBefore = QueenOwnedLaserEffects.statistics();
        private QueenOwnedLaserEffects.Statistics ownedAfter;
        private volatile int serverMaxActiveLaser;
        private volatile int completedCasts;
        private volatile boolean serverHadLaser;
        private volatile boolean serverQuiescent = true;
        private volatile boolean clientQuiescent = true;
        private volatile long clientObservedNs;
        private long allTailsFinishedNs;
        private final JsonArray readinessSamples = new JsonArray();
        private final String runId = System.getProperty(RUN_PROPERTY, "");
        private final boolean queen = !PhotonHybridBlankScenario.ID.equals(System.getProperty("modBench.scenarios", ""));
        private final boolean expectedShader = Boolean.parseBoolean(System.getProperty(SHADER_PROPERTY, "false"));
        private final String label = "hybrid_matrix_" + runId.toLowerCase() + "_" + UUID.randomUUID().toString().replace("-", "");
        private final long installedNs = System.nanoTime();
        private final long installedEpochMs = System.currentTimeMillis();
        private final long jvmStartEpochMs = ManagementFactory.getRuntimeMXBean().getStartTime();
        private final long[] loadingHeads = new long[MAX_LOADING_FRAMES];
        private int loadingCount;
        private int loadingDropped;
        private long windowReadyNs;
        private long maximizeRequestedNs;
        private long maximizedNs;
        private long levelAvailableNs;
        private long playerAvailableNs;
        private long visibleDetectedNs;
        private long startNs;
        private long setupNs;
        private long fiveSecondFrameNs;
        private long stopObservedNs;
        private long clientSeenNs;
        private long clientLastSeenNs;
        private long clientLostNs;
        private volatile long spawnRequestedNs;
        private volatile long createStartedNs;
        private volatile long entityAddedNs;
        private volatile long cleanupRequestedNs;
        private volatile long cleanupStartedNs;
        private volatile long cleanupQueuedNs;
        private volatile long cleanupDispatchStartedNs;
        private volatile long entityRemovedNs;
        private volatile boolean aliveBeforeCleanup;
        private volatile String cleanupSource;
        private String rawArchiveError;
        private volatile boolean verified;
        private boolean rawArchived;
        private final JsonArray rawManifest = new JsonArray();
        private long lastSampleNs;
        private long exportNs;
        private long commonConfiguredNs;
        private long blankMarkerNs;
        private int spawnAttempts;
        private int maxQueenCount;
        private boolean armed;
        private boolean finished;
        private boolean archived;
        private boolean sparkStopping;
        private final AtomicBoolean cleanupRequested = new AtomicBoolean();
        private volatile Throwable failure;
        private ClientLevel level;
        private Vec3 origin;
        private CompletableFuture<World> world;
        private final CompletableFuture<Spawn> spawn = new CompletableFuture<>();
        private final CompletableFuture<Boolean> cleanupFuture = new CompletableFuture<>();
        private ScheduledExecutorService scheduler;
        private ScheduledFuture<?> scheduled;
        private final AtomicBoolean spawnClaimed = new AtomicBoolean();
        private Spawn outcome;
        private PhotonBenchSpark spark;
        private JsonObject frames;
        private final JsonArray samples = new JsonArray();

        private void loading(Minecraft minecraft, boolean recordHead) {
            if (windowReadyNs == 0) {
                require(runId.matches("[A-Za-z0-9_]{1,32}"), "entry_bad_run_id");
                require(minecraft.gameDirectory.toPath().toAbsolutePath().normalize()
                        .endsWith(Path.of("build", "modBench", "runs", "default", "client")), "entry_wrong_game_directory");
                windowReadyNs = System.nanoTime();
            }
            var window = minecraft.getWindow();
            if (window != null && window.handle() != 0) {
                if (GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE) {
                    if (maximizedNs == 0) {
                        maximizedNs = System.nanoTime();
                    }
                } else if (maximizeRequestedNs == 0) {
                    maximizeRequestedNs = System.nanoTime();
                    GLFW.glfwMaximizeWindow(window.handle());
                }
            }
            if (recordHead && startNs == 0 && !armed) {
                if (loadingCount < MAX_LOADING_FRAMES) {
                    loadingHeads[loadingCount++] = System.nanoTime();
                } else {
                    loadingDropped++;
                }
            }
            if (minecraft.level != null && levelAvailableNs == 0) {
                levelAvailableNs = System.nanoTime();
                level = minecraft.level;
            }
            if (minecraft.player != null && playerAvailableNs == 0) {
                playerAvailableNs = System.nanoTime();
            }
            if (world == null && minecraft.level != null && minecraft.player != null
                    && minecraft.getSingleplayerServer() != null) {
                origin = new Vec3(0.5, minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0), 0.5);
                var server = minecraft.getSingleplayerServer();
                var playerId = minecraft.player.getUUID();
                world = CompletableFuture.supplyAsync(() -> configure(server, playerId, origin), server);
            }
            pollSpark();
        }

        private World configure(MinecraftServer server, UUID playerId, Vec3 position) {
            var target = server.overworld();
            var expected = System.getProperty("modBench.client.worldId", "") + "-flat";
            require(server.getWorldData().getLevelName().equals(expected), "entry_world_identity");
            require(System.getProperty("modBench.client.worldPreset", "").equals("flat"), "entry_requires_flat_preset");
            var clock = target.dimensionType().defaultClock().orElseThrow();
            require(server.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.NOON), "entry_noon");
            target.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
            target.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
            target.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.setWeatherParameters(0, 0, false, false);
            server.setDifficulty(Difficulty.NORMAL, true);
            var player = server.getPlayerList().getPlayer(playerId);
            require(player != null, "entry_player_missing");
            player.teleportTo(target, position.x, position.y, position.z - 10,
                    Set.of(), 0, 0, false);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            commonConfiguredNs = System.nanoTime();
            return new World(server, target, playerId, position);
        }

        private void beforeRender(Minecraft minecraft) {
            loading(minecraft, false);
            holdCamera(minecraft);
            if (armed || failure != null || minecraft.level == null || minecraft.player == null
                    || minecraft.screen != null || minecraft.getOverlay() != null) {
                return;
            }
            require(maximizedNs != 0, "entry_not_maximized_before_world");
            require(world != null, "entry_no_world_setup");
            require(IrisCompat.isUsingShaderPack() == expectedShader, "entry_shader_mismatch");
            require(!PhotonFrameCapture.running() && !PhotonPerformanceCapture.active(), "entry_capture_owned_elsewhere");
            visibleDetectedNs = System.nanoTime();
            PhotonPerformanceCapture.start(label, CAPTURE_SECONDS, PhotonPerformanceTraceUtil.Mode.GPU);
            PhotonFrameCapture.phase("world_entry", 0, this::entered);
            PhotonFrameCapture.phase("five_seconds", SPAWN_SECONDS, () -> fiveSecondFrameNs = System.nanoTime());
            armed = true;
        }

        private void entered() {
            startNs = PhotonFrameCapture.liveStatus().get("started_ns").getAsLong();
            scheduler = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("photon-hybrid-" + runId).factory());
            scheduler.schedule(() -> {
                try {
                    cleanup("deadline");
                } catch (RuntimeException | LinkageError problem) {
                    fail(problem);
                    cleanupFuture.completeExceptionally(problem);
                } finally {
                    scheduler.shutdown();
                }
            }, Math.max(0, deadlineNs() - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (laser) {
                for (int index = 0; index < 3; index++) {
                    final int ordinal = index;
                    long target = startNs + TimeUnit.SECONDS.toNanos(10L + index * 10L);
                    var result = new CompletableFuture<JsonObject>();
                    casts.add(result);
                    scheduler.schedule(() -> requestCast(ordinal, target, result),
                            Math.max(0, target - System.nanoTime()), TimeUnit.NANOSECONDS);
                }
            }
            scheduled = scheduler.schedule(() -> {
                if (!spawnClaimed.compareAndSet(false, true)) {
                    return;
                }
                spawnRequestedNs = System.nanoTime();
                checkSpawnDelay(spawnRequestedNs, "entry_scheduler_late");
                world.thenCompose(value -> CompletableFuture.supplyAsync(() -> add(value, spawnRequestedNs), value.server))
                        .whenComplete((result, problem) -> {
                            if (problem == null) {
                                spawn.complete(result);
                            } else {
                                fail(problem);
                                spawn.completeExceptionally(problem);
                            }
                        });
            }, Math.max(0, spawnTargetNs() - System.nanoTime()), TimeUnit.NANOSECONDS);
            Lobecorp.LOGGER.info("HYBRID_MATRIX runId={} phase=world_entry pid={} t0Ns={} queen={} shader={}",
                    runId, ProcessHandle.current().pid(), startNs, queen, expectedShader);
        }

        private void requestCast(int ordinal, long target, CompletableFuture<JsonObject> result) {
            long requested = System.nanoTime();
            Minecraft.getInstance().execute(() -> {
                try {
                    observeClient();
                    require(failure == null && !cleanupRequested.get(), "hybrid_cast_after_failure");
                    require(requested >= target && requested - target <= MAX_SPAWN_DELAY_NS, "hybrid_cast_scheduler_late");
                    require(clientQuiescent, "hybrid_previous_client_tail_alive");
                    var stats = QueenOwnedLaserEffects.statistics();
                    require(stats.started() - ownedBefore.started() == ordinal, "hybrid_previous_cast_not_observed");
                    require(stats.fallbacks() == ownedBefore.fallbacks() && stats.errors() == ownedBefore.errors(), "hybrid_fallback");
                    var key = new PhotonOwnedEffectResources.SourceKey(PhotonOwnedLaser.ID,
                            PhotonOwnedEffectResources.SourceKind.EXPORTED_FX, PhotonOwnedEffectResources.resourceRevision());
                    require(PhotonOwnedEffectResources.lookup(key).readiness() == PhotonOwnedEffectResources.Readiness.READY,
                            "hybrid_resources_not_ready_at_cast");
                    world.thenCompose(value -> CompletableFuture.supplyAsync(() -> cast(value, ordinal, target, requested), value.server))
                            .whenComplete((cast, problem) -> {
                                if (problem == null) {
                                    result.complete(cast);
                                } else {
                                    fail(problem);
                                    result.completeExceptionally(problem);
                                }
                            });
                } catch (RuntimeException | LinkageError problem) {
                    fail(problem);
                    result.completeExceptionally(problem);
                }
            });
        }

        private JsonObject cast(World value, int ordinal, long target, long requested) {
            long actual = System.nanoTime();
            require(actual >= target && actual - target <= MAX_SPAWN_DELAY_NS, "hybrid_cast_server_late");
            var spawned = spawn.getNow(null);
            require(spawned != null && spawned.present, "hybrid_cast_queen_missing");
            var entity = value.level.getEntity(spawned.uuid);
            require(entity instanceof TheQueenOfHatred && entity.isAlive(), "hybrid_cast_queen_missing");
            var queenEntity = (TheQueenOfHatred) entity;
            var skills = IEntitySkillAccess.get(queenEntity);
            require(skills != null && !skills.hasActiveSkills(), "hybrid_previous_skill_alive");
            require(completedCasts == ordinal, "hybrid_previous_skill_not_completed");
            var position = value.origin.add(0, 0, 16);
            var outcome = skills.cast(TheQueenOfHatredSkills.LASER.get(), null, position);
            var item = new JsonObject();
            item.addProperty("ordinal", ordinal + 1);
            item.addProperty("target_ns", target);
            item.addProperty("requested_ns", requested);
            item.addProperty("started_ns", actual);
            item.addProperty("server_dispatch_ns", actual);
            item.addProperty("confirmed_ns", System.nanoTime());
            item.addProperty("timing_scope", "started_ns is server call entry; confirmed_ns is STARTED result return");
            item.add("position_target", new Gson().toJsonTree(List.of(position.x, position.y, position.z)));
            item.addProperty("status", outcome.status().name());
            item.addProperty("started", outcome.started());
            item.addProperty("queen_uuid", queenEntity.getUUID().toString());
            require(outcome.started() && outcome.status().name().equals("STARTED"), "hybrid_cast_not_started:" + outcome.status());
            serverHadLaser = true;
            serverQuiescent = false;
            observeServer(value);
            return item;
        }

        private void observeServer(World value) {
            var spawned = spawn.getNow(null);
            if (spawned == null || !spawned.present || cleanupRequested.get()) {
                return;
            }
            var entity = value.level.getEntity(spawned.uuid);
            require(entity instanceof TheQueenOfHatred && entity.isAlive(), "hybrid_server_queen_lost");
            var skills = IEntitySkillAccess.get((TheQueenOfHatred) entity);
            require(skills != null, "hybrid_server_skills_missing");
            int active = (int) skills.activeSkills().stream().filter(runtime -> runtime.skill() instanceof LaserSkill).count();
            serverMaxActiveLaser = Math.max(serverMaxActiveLaser, active);
            require(active <= 1, "hybrid_server_laser_overlap");
            if (active == 0 && serverHadLaser) {
                completedCasts++;
                serverHadLaser = false;
            }
            serverQuiescent = active == 0;
            var sample = new JsonObject();
            sample.addProperty("ns", System.nanoTime());
            sample.addProperty("game_tick", value.level.getGameTime());
            sample.addProperty("active_laser", active);
            sample.addProperty("completed_casts", completedCasts);
            synchronized (serverSamples) {
                serverSamples.add(sample);
            }
        }

        private void observeClient() {
            ownedAfter = QueenOwnedLaserEffects.statistics();
            var legacy = QueenBeamEffects.retentionStatistics();
            clientQuiescent = ownedAfter.playing() == 0 && ownedAfter.tails() == 0 && legacy.playing() == 0;
            clientObservedNs = System.nanoTime();
            if (laser && ownedAfter.started() - ownedBefore.started() == 3 && clientQuiescent && allTailsFinishedNs == 0) {
                allTailsFinishedNs = clientObservedNs;
            }
        }

        private long spawnTargetNs() {
            return startNs + TimeUnit.SECONDS.toNanos(SPAWN_SECONDS);
        }

        private long deadlineNs() {
            return startNs + TimeUnit.SECONDS.toNanos(CAPTURE_SECONDS);
        }

        private void checkSpawnDelay(long actual, String reason) {
            long delay = actual - spawnTargetNs();
            if (delay < 0 || delay > MAX_SPAWN_DELAY_NS) {
                fail(new IllegalStateException(reason + ": delay_ns=" + delay + ", max_ns=" + MAX_SPAWN_DELAY_NS));
            }
        }

        private Spawn add(World value, long requested) {
            long created = System.nanoTime();
            createStartedNs = created;
            checkSpawnDelay(created, "entry_server_start_late");
            if (!queen) {
                blankMarkerNs = created;
                return new Spawn(false, null, -1, requested, created, System.nanoTime());
            }
            spawnAttempts++;
            require(value.level.getEntitiesOfClass(TheQueenOfHatred.class,
                    new AABB(value.origin, value.origin).inflate(64)).isEmpty(), "entry_queen_already_exists");
            var entity = new TheQueenOfHatred(value.level);
            entity.setPos(value.origin);
            entity.setNoAi(true);
            entity.setInvulnerable(true);
            entity.setPersistenceRequired();
            entity.setYRot(laser ? 0 : 180);
            entity.setYHeadRot(laser ? 0 : 180);
            entity.yBodyRot = laser ? 0 : 180;
            require(value.level.addFreshEntity(entity), "entry_spawn_rejected");
            long added = System.nanoTime();
            entityAddedNs = added;
            Lobecorp.LOGGER.info("HYBRID_MATRIX runId={} phase=queen_added uuid={} addedNs={} requestedNs={}", runId, entity.getUUID(), added, requested);
            return new Spawn(true, entity.getUUID(), entity.getId(), requested, created, added);
        }

        private void holdCamera(Minecraft minecraft) {
            if (origin == null || minecraft.player == null || finished) {
                return;
            }
            minecraft.player.getAbilities().flying = true;
            minecraft.player.setPos(origin.x, origin.y, origin.z - 10);
            minecraft.player.setYRot(0);
            minecraft.player.setXRot(0);
            minecraft.player.setDeltaMovement(Vec3.ZERO);
        }

        private void afterFrame(Minecraft minecraft) {
            try {
                pollSpark();
            } catch (RuntimeException | LinkageError problem) {
                fail(problem);
            }
            if (spawn.isDone() && !spawn.isCompletedExceptionally() && outcome == null) {
                outcome = spawn.join();
            }
            if (!armed || finished) {
                return;
            }
            observeClient();
            long now = System.nanoTime();
            var currentLevel = minecraft.level;
            var player = minecraft.player;
            if (currentLevel != level || currentLevel == null || player == null) {
                fail(new IllegalStateException("entry_world_changed"));
            } else {
                if (outcome != null && outcome.present) {
                    var entity = currentLevel.getEntity(outcome.id);
                    boolean present = entity instanceof TheQueenOfHatred
                            && entity.getUUID().equals(outcome.uuid) && entity.isAlive();
                    if (present) {
                        if (clientSeenNs == 0) {
                            clientSeenNs = now;
                        }
                        clientLastSeenNs = now;
                    } else if (clientSeenNs != 0 && now < deadlineNs() && clientLostNs == 0) {
                        clientLostNs = now;
                        fail(new IllegalStateException("entry_client_entity_lost_before_deadline"));
                    }
                }
                if (startNs != 0 && now - lastSampleNs >= TimeUnit.MILLISECONDS.toNanos(100)) {
                    lastSampleNs = now;
                    int count = currentLevel.getEntitiesOfClass(TheQueenOfHatred.class,
                            player.getBoundingBox().inflate(64)).size();
                    maxQueenCount = Math.max(maxQueenCount, count);
                    if (count > (queen ? 1 : 0)) {
                        fail(new IllegalStateException("entry_client_queen_count"));
                    }
                    var sample = new JsonObject();
                    sample.addProperty("ns", now);
                    sample.addProperty("offset_seconds", (now - startNs) / 1e9);
                    sample.addProperty("queen_count", count);
                    samples.add(sample);
                    var key = new PhotonOwnedEffectResources.SourceKey(PhotonOwnedLaser.ID,
                            PhotonOwnedEffectResources.SourceKind.EXPORTED_FX, PhotonOwnedEffectResources.resourceRevision());
                    var ready = new JsonObject();
                    ready.addProperty("ns", now);
                    ready.addProperty("revision", key.revision());
                    ready.addProperty("readiness", PhotonOwnedEffectResources.lookup(key).readiness().name());
                    readinessSamples.add(ready);
                }
            }
            if (!PhotonFrameCapture.running()) {
                frames = PhotonFrameCapture.snapshot();
                stopObservedNs = now;
                finished = true;
                if (!frames.has("label") || !frames.get("label").getAsString().equals(label)) {
                    fail(new IllegalStateException("entry_frame_owner"));
                }
                if (!frames.has("state") || !"DURATION".equals(frames.get("state").getAsString())) {
                    fail(new IllegalStateException("entry_frame_ended_early"));
                }
                cleanup();
                Lobecorp.LOGGER.info("HYBRID_MATRIX runId={} phase=finished t0Ns={} stopObservedNs={} observationDelayNs={}",
                        runId, startNs, stopObservedNs, stopObservedNs - deadlineNs());
            }
        }

        private void pollSpark() {
            if (spark != null) {
                spark.poll();
            }
        }

        private void cleanup() {
            cleanup("render_or_teardown");
        }

        private void cleanup(String source) {
            if (!cleanupRequested.compareAndSet(false, true)) {
                return;
            }
            cleanupRequestedNs = System.nanoTime();
            cleanupSource = source;
            if (spawnClaimed.compareAndSet(false, true)) {
                if (scheduled != null) {
                    scheduled.cancel(false);
                }
                spawn.complete(null);
            }
            if (world == null) {
                cleanupFuture.complete(true);
                return;
            }
            world.thenCompose(value -> {
                cleanupQueuedNs = System.nanoTime();
                // 期限任务先独立进入服务端队列；未完成的构造只延后移除，不阻塞 scheduler。
                return CompletableFuture.supplyAsync(() -> {
                            cleanupDispatchStartedNs = System.nanoTime();
                            return true;
                        }, value.server).thenCompose(_ -> spawn.handle((result, _) -> result))
                        .thenCompose(result -> CompletableFuture.supplyAsync(() -> {
                            cleanupStartedNs = System.nanoTime();
                            if (result == null || !result.present) {
                                return true;
                            }
                            var entity = value.level.getEntity(result.uuid);
                            aliveBeforeCleanup = entity instanceof TheQueenOfHatred && entity.isAlive();
                            if (!aliveBeforeCleanup) {
                                fail(new IllegalStateException("entry_server_entity_not_alive_before_cleanup"));
                            }
                            if (entity != null) {
                                var skills = IEntitySkillAccess.get((TheQueenOfHatred) entity);
                                if (skills != null) {
                                    skills.cancelAll(true);
                                }
                                entity.discard();
                                entityRemovedNs = System.nanoTime();
                            }
                            return value.level.getEntity(result.uuid) == null;
                        }, value.server));
            }).whenComplete((result, problem) -> {
                if (problem == null) {
                    cleanupFuture.complete(result);
                } else {
                    fail(problem);
                    cleanupFuture.completeExceptionally(problem);
                }
            });
        }

        private boolean cleanupSettled() {
            return cleanupFuture.isDone();
        }

        private boolean cleanupDone() {
            return cleanupSettled() && !cleanupFuture.isCompletedExceptionally() && cleanupFuture.join();
        }

        private synchronized void fail(Throwable problem) {
            verified = false;
            if (failure == null) {
                failure = problem;
                Lobecorp.LOGGER.error("HYBRID_MATRIX runId={} phase=failed", runId, problem);
            }
        }

        private void require(boolean valid, String message) {
            if (!valid) {
                throw new IllegalStateException(message);
            }
        }

        private void validate(JsonObject profile) {
            validateHybrid();
            require(finished && failure == null && startNs != 0, "entry_not_complete");
            require(spawnAttempts == (queen ? 1 : 0) && outcome != null && outcome.present == queen,
                    "entry_spawn_outcome");
            require(spawnRequestedNs >= spawnTargetNs()
                    && spawnRequestedNs - spawnTargetNs() <= MAX_SPAWN_DELAY_NS, "entry_scheduler_late");
            require(createStartedNs >= spawnTargetNs()
                    && createStartedNs - spawnTargetNs() <= MAX_SPAWN_DELAY_NS, "entry_server_start_late");
            require(!queen || entityAddedNs >= createStartedNs && entityAddedNs < deadlineNs(),
                    "entry_entity_added_outside_window");
            require(!queen || clientSeenNs >= entityAddedNs && clientSeenNs < deadlineNs()
                    && clientLostNs == 0 && aliveBeforeCleanup, "entry_entity_lifetime_unconfirmed");
            require(maxQueenCount == (queen ? 1 : 0), "entry_client_queen_count");
            require(cleanupDone() && cleanupRequestedNs >= deadlineNs(), "entry_cleanup_not_complete");
            require(frames != null && label.equals(frames.get("label").getAsString())
                    && frames.get("started_ns").getAsLong() == startNs
                    && frames.get("requested_seconds").getAsDouble() == CAPTURE_SECONDS
                    && "DURATION".equals(frames.get("state").getAsString()), "entry_window_not_natural_25s");
            require(frames.get("frame_count").getAsInt() > 0 && frames.get("error").isJsonNull(), "entry_frame_error");
            require(frames.get("gpu_errors").getAsLong() == 0 && frames.get("gpu_available").getAsBoolean(),
                    "entry_frame_gpu_invalid");
            require(!profile.get("active").getAsBoolean() && !profile.get("exporting").getAsBoolean()
                    && profile.get("error").isJsonNull(), "entry_profile_export_error");
            require(profile.has("output") && !profile.get("output").isJsonNull(), "entry_profile_output_missing");
            require(label.equals(profile.getAsJsonObject("frame_summary").get("label").getAsString()),
                    "entry_profile_owner");
            require(profile.has("incomplete") && profile.get("incomplete").getAsLong() == 0
                    && profile.get("collector_error").isJsonNull()
                    && profile.get("dropped").getAsLong() == 0, "entry_collector_invalid");
            var quality = profile.getAsJsonObject("collector_quality");
            require(quality.get("deniedNodeRequests").getAsLong() == 0
                    && quality.get("deniedAggregateRequests").getAsLong() == 0
                    && quality.get("deniedCounterRequests").getAsLong() == 0
                    && quality.get("droppedImportantMarks").getAsLong() == 0, "entry_aggregate_truncated");
            var gpu = profile.getAsJsonObject("gpu_query_status");
            require(gpu.get("requested").getAsBoolean() && gpu.get("supported").getAsBoolean()
                            && gpu.get("error").isJsonNull() && gpu.get("dropped_aggregates").getAsLong() == 0,
                    "entry_profile_gpu_invalid");
        }

        private void validateHybrid() {
            require(ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                    .anyMatch(value -> value.startsWith("-XX:StartFlightRecording=")), "hybrid_startup_jfr_not_requested");
            require(PhotonRuntimeFactory.mode() == PhotonRuntimeFactory.Mode.HYBRID && QueenOwnedLaserEffects.enabled(), "hybrid_mode_changed");
            require(IrisCompat.isUsingShaderPack() == expectedShader, "hybrid_shader_changed");
            if (!laser) {
                require(ownedAfter != null && ownedAfter.started() == ownedBefore.started()
                        && ownedAfter.fallbacks() == ownedBefore.fallbacks(), "hybrid_unexpected_laser");
                return;
            }
            require(casts.size() == 3 && casts.stream().allMatch(value -> value.isDone() && !value.isCompletedExceptionally()), "hybrid_cast_results_missing");
            require(completedCasts == 3 && serverQuiescent && serverMaxActiveLaser == 1, "hybrid_skills_not_completed");
            require(allTailsFinishedNs > 0 && allTailsFinishedNs <= deadlineNs() && clientQuiescent, "hybrid_tail_outside_capture");
            require(ownedAfter.started() - ownedBefore.started() == 3 && ownedAfter.fallbacks() == ownedBefore.fallbacks()
                    && ownedAfter.errors() == ownedBefore.errors() && ownedAfter.overlaps() == ownedBefore.overlaps()
                    && ownedAfter.maximumConcurrent() == 1, "hybrid_owned_usage_invalid");
            var components = ownedAfter.components().stream().filter(value -> value.ordinal() > ownedBefore.started()).toList();
            require(components.size() == 3 && components.stream().allMatch(value -> value.created().containsAll(PhotonOwnedLaser.COMPONENTS)
                            && value.activeObserved().containsAll(PhotonOwnedLaser.COMPONENTS)),
                    "hybrid_fifteen_components_not_observed");
        }

        private JsonObject report() {
            var result = new JsonObject();
            result.addProperty("variant", laser ? "laser3" : queen ? "queen25" : "blank25");
            result.addProperty("baseline25_seconds", 25);
            var windows = new JsonObject();
            windows.addProperty("baseline25", 25);
            windows.addProperty("full", CAPTURE_SECONDS);
            result.add("windows", windows);
            result.addProperty("trace_events_per_thread_capacity", PhotonPerformanceTraceUtil.EVENTS_PER_THREAD);
            result.addProperty("trace_max_threads", PhotonPerformanceTraceUtil.MAX_THREADS);
            result.addProperty("trace_effective_nodes", PhotonPerformanceTraceUtil.EFFECTIVE_MAX_NODES);
            result.addProperty("trace_effective_aggregates", PhotonPerformanceTraceUtil.EFFECTIVE_MAX_AGGREGATES_PER_THREAD);
            result.add("jfr_recordings_at_report", new Gson().toJsonTree(FlightRecorder.isInitialized()
                    ? FlightRecorder.getFlightRecorder().getRecordings().stream()
                    .map(value -> value.getName() + ":" + value.getState()).toList() : List.of()));
            result.addProperty("actual_used", ownedAfter != null && ownedAfter.started() > ownedBefore.started());
            result.addProperty("all_tails_finished_ns", allTailsFinishedNs);
            result.addProperty("completed_casts", completedCasts);
            result.addProperty("server_max_active_laser", serverMaxActiveLaser);
            result.addProperty("server_activity_scope", "production EntitySkillUtil.tick HEAD/RETURN and actual cast boundaries; no extra skill ticks");
            result.add("owned_before", new Gson().toJsonTree(ownedBefore));
            result.add("owned_after", new Gson().toJsonTree(ownedAfter));
            result.add("readiness_samples", readinessSamples);
            synchronized (serverSamples) {
                result.add("server_samples", serverSamples.deepCopy());
            }
            var castResults = new JsonArray();
            for (var cast : casts) {
                if (cast.isDone() && !cast.isCompletedExceptionally()) {
                    castResults.add(cast.join());
                }
            }
            result.add("casts", castResults);
            result.addProperty("schema", "photon-hybrid-v2");
            result.addProperty("run_id", runId);
            result.addProperty("label", label);
            result.addProperty("pid", ProcessHandle.current().pid());
            result.addProperty("queen_variant", queen);
            result.addProperty("expected_shader", expectedShader);
            result.addProperty("shader_in_use", IrisCompat.isUsingShaderPack());
            result.addProperty("creation_mode", PhotonRuntimeFactory.mode().name());
            result.addProperty("jvm_started_epoch_ms", jvmStartEpochMs);
            result.addProperty("observer_installed_ns", installedNs);
            result.addProperty("observer_installed_epoch_ms", installedEpochMs);
            result.addProperty("window_ready_ns", windowReadyNs);
            result.addProperty("maximize_requested_ns", maximizeRequestedNs);
            result.addProperty("maximized_ns", maximizedNs);
            result.addProperty("level_available_ns", levelAvailableNs);
            result.addProperty("player_available_ns", playerAvailableNs);
            result.addProperty("world_visible_detected_ns", visibleDetectedNs);
            result.addProperty("entry_t0_ns", startNs);
            result.addProperty("setup_called_ns", setupNs);
            result.addProperty("world_configured_ns", commonConfiguredNs);
            result.addProperty("spawn_target_ns", startNs == 0 ? 0 : startNs + TimeUnit.SECONDS.toNanos(SPAWN_SECONDS));
            result.addProperty("five_second_client_frame_ns", fiveSecondFrameNs);
            result.addProperty("blank_marker_ns", blankMarkerNs);
            result.addProperty("stop_observed_ns", stopObservedNs);
            result.addProperty("capture_seconds", CAPTURE_SECONDS);
            result.addProperty("spawn_seconds", SPAWN_SECONDS);
            result.addProperty("spawn_attempts", spawnAttempts);
            result.addProperty("client_seen_ns", clientSeenNs);
            result.addProperty("max_queen_count", maxQueenCount);
            result.addProperty("startup_frame_count", loadingCount);
            result.addProperty("startup_frame_dropped", loadingDropped);
            result.addProperty("startup_frame_scope", "observer-installed Minecraft.renderFrame HEADs through world-entry arming, including entry frame HEAD; launch JFR covers earlier loading");
            result.addProperty("failure", failure == null ? null : failure.toString());
            result.addProperty("finished", finished);
            result.addProperty("cleanup_complete", cleanupDone());
            result.addProperty("cleanup_failed", cleanupFuture.isCompletedExceptionally());
            result.addProperty("verified", verified);
            result.addProperty("raw_archived", rawArchived);
            result.addProperty("raw_archive_error", rawArchiveError);
            result.addProperty("max_spawn_dispatch_delay_ns", MAX_SPAWN_DELAY_NS);
            result.addProperty("server_spawn_requested_ns", spawnRequestedNs);
            result.addProperty("server_create_started_ns", createStartedNs);
            result.addProperty("server_entity_added_ns", entityAddedNs);
            result.addProperty("scheduler_delay_ns", spawnRequestedNs == 0 ? null : spawnRequestedNs - spawnTargetNs());
            result.addProperty("server_start_delay_ns", createStartedNs == 0 ? null : createStartedNs - spawnTargetNs());
            result.addProperty("entity_creation_ns", entityAddedNs == 0 ? null : entityAddedNs - createStartedNs);
            result.addProperty("client_appearance_delay_ns", clientSeenNs == 0 || entityAddedNs == 0
                    ? null : clientSeenNs - entityAddedNs);
            result.addProperty("client_last_seen_ns", clientLastSeenNs);
            result.addProperty("client_lost_before_deadline_ns", clientLostNs);
            result.addProperty("client_observed_span_ns", clientSeenNs == 0 ? null : clientLastSeenNs - clientSeenNs);
            result.addProperty("cleanup_target_ns", startNs == 0 ? 0 : deadlineNs());
            result.addProperty("cleanup_requested_ns", cleanupRequestedNs);
            result.addProperty("cleanup_started_ns", cleanupStartedNs);
            result.addProperty("cleanup_queued_ns", cleanupQueuedNs);
            result.addProperty("cleanup_dispatch_started_ns", cleanupDispatchStartedNs);
            result.addProperty("cleanup_source", cleanupSource);
            result.addProperty("server_alive_before_cleanup", aliveBeforeCleanup);
            result.addProperty("server_entity_removed_ns", entityRemovedNs);
            result.addProperty("server_entity_lifetime_ns", entityRemovedNs == 0 || entityAddedNs == 0
                    ? null : entityRemovedNs - entityAddedNs);
            result.addProperty("cleanup_request_delay_ns", cleanupRequestedNs == 0 || startNs == 0
                    ? null : cleanupRequestedNs - deadlineNs());
            result.addProperty("cleanup_server_delay_ns", cleanupStartedNs == 0 || startNs == 0
                    ? null : cleanupStartedNs - deadlineNs());
            result.addProperty("stop_observation_delay_ns", stopObservedNs == 0 || startNs == 0
                    ? null : stopObservedNs - deadlineNs());
            result.addProperty("lifetime_scope", "server added-to-discard wall time; client observed span is not proof of 20s");
            var profile = PhotonPerformanceCapture.status();
            result.add("profile_status", profile);
            result.addProperty("raw_trace_truncated", profile.has("overwritten")
                    && profile.get("overwritten").getAsLong() > 0);
            result.addProperty("raw_trace_scope", "bounded per-thread trace; aggregate and full frame intervals validated separately");
            if (outcome != null) {
                result.addProperty("queen_uuid", outcome.uuid == null ? null : outcome.uuid.toString());
                result.addProperty("queen_entity_id", outcome.id);
            }
            result.add("frames", frames);
            result.add("entity_samples", samples);
            result.add("vm_arguments", new Gson().toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments()));
            if (spark != null) {
                result.add("spark", spark.snapshot());
            }
            return result;
        }

        private void archive(BenchClientContext context, JsonObject profile) throws Exception {
            if (archived) {
                return;
            }
            validate(profile);
            long begun = System.nanoTime();
            archiveRaw(context, profile);
            require(!rawManifest.isEmpty(), "entry_raw_artifacts_missing");
            spark.archive(context);
            exportNs = System.nanoTime() - begun;
            var result = report();
            result.addProperty("export_ns", exportNs);
            result.addProperty("archived", true);
            result.addProperty("scope", "world-visible render HEAD zero; 5s scheduled server spawn; independent variant deadline cleanup; natural frame DURATION");
            result.add("frame_artifacts", rawManifest.deepCopy());
            context.artifacts().write(label + "_hybrid18.json", "application/json", result.toString());
            archived = true;
            Lobecorp.LOGGER.info("HYBRID_MATRIX runId={} phase=archived label={} verified={}", runId, label, verified);
        }

        private void archiveRaw(BenchClientContext context, JsonObject profile) throws Exception {
            if (rawArchived) {
                return;
            }
            var output = profile.get("output");
            if (output != null && !output.isJsonNull()) {
                var summary = profile.getAsJsonObject("frame_summary");
                require(summary != null && summary.has("label") && label.equals(summary.get("label").getAsString()),
                        "entry_raw_profile_owner");
                var directory = Path.of(output.getAsString()).toAbsolutePath().normalize();
                var game = context.minecraft().gameDirectory.toPath().toAbsolutePath().normalize();
                require(directory.startsWith(game.resolve("performance")), "entry_export_outside_owner");
                try (var paths = Files.list(directory)) {
                    for (var path : paths.filter(Files::isRegularFile).toList()) {
                        var bytes = Files.readAllBytes(path);
                        var target = context.artifacts().write(label + "_" + path.getFileName(), "application/octet-stream", bytes);
                        var item = new JsonObject();
                        item.addProperty("source", path.toString());
                        item.addProperty("artifact", target.toString());
                        item.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                        rawManifest.add(item);
                    }
                }
            }
            var loading = new StringBuilder("frame_id,head_ns,offset_from_observer_ns,interval_ns\n");
            for (int i = 0; i < loadingCount; i++) {
                loading.append(i).append(',').append(loadingHeads[i]).append(',').append(loadingHeads[i] - installedNs)
                        .append(',').append(i == 0 ? "" : Long.toString(loadingHeads[i] - loadingHeads[i - 1])).append('\n');
            }
            context.artifacts().write(label + "_loading_frames.csv", "text/csv", loading.toString());
            rawArchived = true;
        }

        private void archiveFailure(BenchClientContext context) throws IOException {
            verified = false;
            try {
                archiveRaw(context, PhotonPerformanceCapture.status());
            } catch (Exception | LinkageError problem) {
                rawArchiveError = problem.toString();
                Lobecorp.LOGGER.error("HYBRID_MATRIX runId={} phase=raw_archive_failed", runId, problem);
            }
            try {
                if (spark != null && spark.complete()) {
                    spark.archive(context);
                }
            } catch (Exception | LinkageError problem) {
                rawArchiveError = rawArchiveError == null ? problem.toString() : rawArchiveError + "; " + problem;
            }
            var result = report();
            result.add("frame_artifacts", rawManifest.deepCopy());
            context.artifacts().write(label + "_failure.json", "application/json", result.toString());
        }
    }
}
