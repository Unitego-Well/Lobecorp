package org.unitego.lobecorp.client.bench;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.minecraft.world.Difficulty;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.performance.PhotonBenchConditions;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.client.photon.performance.PhotonPerformanceCapture;
import org.unitego.lobecorp.client.photon.performance.PhotonSubmissionDiagnostics;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedEffectResources;
import org.unitego.lobecorp.client.photon.runtime.PhotonOwnedLaser;
import org.unitego.lobecorp.client.photon.runtime.creation.PhotonRuntimeFactory;
import org.unitego.lobecorp.client.photon.runtime.QueenBeamEffects;
import org.unitego.lobecorp.client.photon.runtime.QueenOwnedLaserEffects;
import org.unitego.lobecorp.bench.plugin.BenchMixinPlugin;
import org.unitego.lobecorp.registry.world.entity.AbnormalitieEntityTypes;
import org.unitego.lobecorp.registry.world.entity.skill.TheQueenOfHatredSkills;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.TheQueenOfHatred;
import org.unitego.lobecorp.world.entity.abnormalitie.the_queen_of_hatred.skill.LaserSkill;
import org.unitego.lobecorp.world.entity.skill.IEntitySkillAccess;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/// 安静模式矩阵：可见世界 HEAD 为零点，五秒只生成一次目标，激光在十、二十、三十秒真实施法。
/// 预测量只允许不包含待测实体／特效的公共世界与截图稳定阶段；其原始帧和耗时独立归档。
public class PhotonQuietMatrixCapture {
    public static final String RUN_PROPERTY = "lobecorp.photon.quiet.runId";
    public static final String SHADER_PROPERTY = "lobecorp.photon.quiet.expectedShader";
    public static final String SNAPSHOT_READY_FILE_PROPERTY = "lobecorp.photon.quiet.snapshotReadyFile";
    public static final String SNAPSHOT_ACK_FILE_PROPERTY = "lobecorp.photon.quiet.snapshotAckFile";
    public static final String SNAPSHOT_IMAGE_PROPERTY = "lobecorp.photon.quiet.snapshotImage";
    public static final String VISUAL_SNAPSHOT_READY_FILE_PROPERTY = "lobecorp.photon.quiet.visualSnapshotReadyFile";
    public static final String VISUAL_SNAPSHOT_ACK_FILE_PROPERTY = "lobecorp.photon.quiet.visualSnapshotAckFile";
    public static final String VISUAL_SNAPSHOT_IMAGE_PROPERTY = "lobecorp.photon.quiet.visualSnapshotImage";
    public static final String SNAPSHOT_SCHEMA = "photon-quiet-snapshot-ack/1";
    public static final String BATCH_PROPERTY = "lobecorp.photon.quiet.batchId";
    public static final String SOURCE_COMMIT_PROPERTY = "lobecorp.photon.quiet.sourceCommit";
    public static final String SOURCE_VARIANT_PROPERTY = "lobecorp.photon.quiet.sourceVariant";
    public static final String EXPECTED_DIMENSIONS_PROPERTY = "lobecorp.photon.quiet.expectedDimensions";
    public static final int SPAWN_SECONDS = 5;
    public static final int LASER_SECONDS = 45;
    public static final int SHORT_SECONDS = 25;
    /// 服务端调度与真实构造允许的最大延迟；不因安静模式放宽。
    private static final long MAX_DISPATCH_DELAY_NS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final int MAX_PREMEASURE_FRAMES = 32768;
    private static volatile Session session;

    public enum Variant {
        COW25("lobecorp.photon-10-quiet-cow25", "cow25", SHORT_SECONDS, Target.COW, false),
        QUEEN25("lobecorp.photon-10-quiet-queen25", "queen25", SHORT_SECONDS, Target.QUEEN, false),
        LASER3("lobecorp.photon-10-quiet-laser3", "laser3", LASER_SECONDS, Target.QUEEN, true),
        BLANK45("lobecorp.photon-10-quiet-blank45", "blank45", LASER_SECONDS, Target.BLANK, false);

        private final String id;
        private final String label;
        private final int seconds;
        private final Target target;
        private final boolean laser;

        Variant(String id, String label, int seconds, Target target, boolean laser) {
            this.id = id;
            this.label = label;
            this.seconds = seconds;
            this.target = target;
            this.laser = laser;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public int seconds() {
            return seconds;
        }

        public boolean laser() {
            return laser;
        }

        public static Variant selected() {
            var id = System.getProperty("modBench.scenarios", "");
            for (var value : values()) {
                if (value.id.equals(id)) {
                    return value;
                }
            }
            return null;
        }
    }

    private enum Target {
        BLANK, COW, QUEEN
    }

    /// 视觉诊断复用真实激光执行链，但不进入性能矩阵统计。
    private static boolean visualDiagnostic() {
        return VISUAL_ID.equals(System.getProperty("modBench.scenarios", ""));
    }

    public static final String VISUAL_ID = "lobecorp.photon-10-quiet-laser3-visual";

    /// 正式 runId 的前缀决定场景与光影；attempt 仅区分三次独立 JVM，不改变负载。
    private static boolean formalShaderRun() {
        return System.getProperty(RUN_PROPERTY, "").startsWith("QS");
    }

    private record World(MinecraftServer server, ServerLevel level, UUID player, Vec3 origin) {
    }

    private record Spawn(boolean present, UUID uuid, int id, long requestedNs, long createdNs, long addedNs) {
    }

    private record PreMeasure(int frameCount, long[] heads, long startedNs, long stoppedNs,
                              JsonObject summary) {
    }

    private record SnapshotAck(Path path, Path image, String dimensions) {
    }

    public static boolean selected() {
        return Variant.selected() != null || visualDiagnostic();
    }

    public static void observeSkillTick(LivingEntity entity) {
        var value = session;
        if (value == null || !value.variant.laser || entity.level().isClientSide()
                || !(entity instanceof TheQueenOfHatred) || value.world == null
                || !value.world.isDone() || value.world.isCompletedExceptionally()) {
            return;
        }
        var spawned = value.spawn.getNow(null);
        if (spawned == null || !entity.getUUID().equals(spawned.uuid())) {
            return;
        }
        try {
            value.observeServer(value.world.join());
        } catch (RuntimeException | LinkageError problem) {
            value.fail(problem);
        }
    }

    public static void loadingFrame(Minecraft minecraft) {
        if (!selected()) {
            return;
        }
        var value = current();
        try {
            value.premeasureHead(minecraft);
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

    public static void observeSubmission(Minecraft minecraft) {
        if (!selected() || session == null) {
            return;
        }
        try {
            session.observeSubmission(minecraft);
        } catch (RuntimeException | LinkageError problem) {
            session.fail(problem);
        }
    }

    /// 只统计渲染器实际提交的已生成目标；不把存在于世界等同于看见。
    public static void observeEntitySubmit(EntityRenderState state) {
        var value = session;
        if (value == null || !value.armed || state == null || state.entityType == null) {
            return;
        }
        try {
            value.observeEntitySubmit(state.entityType);
        } catch (RuntimeException | LinkageError problem) {
            value.fail(problem);
        }
    }

    public static void setup(BenchClientContext context, Variant variant) {
        var value = current();
        requireArtifactSet();
        value.require(visualDiagnostic() || variant == value.variant, "quiet_variant_mismatch");
        value.require(BenchMixinPlugin.preservedWindowApplied(), "quiet_window_mixin_not_loaded");
        value.require(PhotonRuntimeFactory.mode() == PhotonRuntimeFactory.Mode.HYBRID, "quiet_requires_hybrid");
        if (variant.laser || visualDiagnostic()) {
            value.require(QueenOwnedLaserEffects.enabled(), "quiet_requires_owned_laser");
        }
        value.setupNs = System.nanoTime();
    }

    /// 只校验本场景实际加载的 Sodium/Iris 制品来源，不依赖 VM 自报的 expectedShader。
    private static void requireArtifactSet() {
        var mods = net.neoforged.fml.ModList.get();
        var sodium = mods.getModFileById("sodium");
        var iris = mods.getModFileById("iris");
        if (sodium == null || iris == null) {
            throw new IllegalStateException("quiet_requires_sodium_iris");
        }
        requireArtifact(sodium, "sodium", "0.9.2+mc26.1.2", "sodium-neoforge-0.9.2+mc26.1.2.jar");
        requireArtifact(iris, "iris", "1.11.4+mc26.1.2", "iris-neoforge-1.11.4+mc26.1.2.jar");
    }

    /// JiJ 内层文件不是外层制品；沿 discoveryAttributes.parent 回溯后再核对真实根 lib 中的同一文件。
    private static void requireArtifact(IModFileInfo fileInfo, String modId, String version, String fileName) {
        var mod = fileInfo.getMods().stream()
                .filter(value -> value.getModId().equals(modId))
                .findFirst()
                .orElse(null);
        if (mod == null || !mod.getVersion().toString().equals(version)) {
            throw new IllegalStateException("quiet_unexpected_sodium_iris_artifact: " + modId + " version");
        }
        var artifact = outermostArtifact(fileInfo.getFile());
        var actual = artifact.getFilePath();
        if (!artifact.getFileName().equals(fileName)) {
            throw new IllegalStateException("quiet_unexpected_sodium_iris_artifact: " + actual);
        }
        var expected = projectRoot().resolve("lib").resolve(fileName);
        try {
            if (!Files.isSameFile(actual, expected)) {
                throw new IllegalStateException("quiet_unexpected_sodium_iris_artifact: " + actual);
            }
        } catch (java.io.IOException problem) {
            throw new IllegalStateException("quiet_unexpected_sodium_iris_artifact: " + actual, problem);
        }
    }

    private static IModFile outermostArtifact(IModFile file) {
        var current = file;
        while (true) {
            var parent = current.getDiscoveryAttributes().parent();
            if (parent == null) {
                return current;
            }
            current = parent;
        }
    }

    private static Path projectRoot() {
        try {
            var location = PhotonQuietMatrixCapture.class.getProtectionDomain().getCodeSource().getLocation();
            var path = Path.of(location.toURI());
            if (Files.isRegularFile(path)) {
                path = path.getParent();
            }
            while (path != null) {
                if (Files.isRegularFile(path.resolve("build.gradle.kts")) && Files.isDirectory(path.resolve("lib"))) {
                    return path;
                }
                path = path.getParent();
            }
        } catch (Exception problem) {
            throw new IllegalStateException("quiet_unexpected_sodium_iris_artifact: project root", problem);
        }
        throw new IllegalStateException("quiet_unexpected_sodium_iris_artifact: project root");
    }

    public static BenchClientStepResult step(BenchClientContext context) throws Exception {
        var value = current();
        try {
            value.afterFrame(context.minecraft());
            if (value.armed && !value.finished && PhotonPerformanceCapture.active()) {
                return BenchClientStepResult.CONTINUE;
            }
            if (value.failure == null && !value.armed && value.spark != null && !value.spark.running()) {
                value.armAfterSparkReady();
            }
            if (value.failure == null && value.finished) {
                value.cleanup();
                if (!value.cleanupSettled()) {
                    return BenchClientStepResult.CONTINUE;
                }
                value.require(value.cleanupDone(), "quiet_cleanup_failed");
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
                value.archive(context);
                return BenchClientStepResult.COMPLETE;
            }
        } catch (Exception | LinkageError problem) {
            value.fail(problem);
        }
        if (value.failure == null) {
            return BenchClientStepResult.CONTINUE;
        }
        value.archiveFailure(context);
        throw value.failure instanceof RuntimeException runtime
                ? runtime : new IllegalStateException(value.failure);
    }

    public static void verify(BenchClientContext context) throws Exception {
        var value = current();
        value.require(value.archived && value.finished && value.failure == null, "quiet_not_archived");
        value.validate();
    }

    /// 视觉诊断仍走真实三次激光，但不把视觉局计入性能 A/B 门槛。
    public static void verifyVisual(BenchClientContext context) throws Exception {
        var value = current();
        value.validateVisual();
        value.require(value.archived, "quiet_visual_not_archived");
    }

    /// 正式 A/B 只选择父批准的 7 条件；第 8 个 shader-cow 模板仅保留诊断能力。
    public static List<String> selectedRunIds() {
        return List.of(
                "QCW01", "QCW02", "QCW03",
                "QQN01", "QQN02", "QQN03",
                "QLA01", "QLA02", "QLA03",
                "QBK01", "QBK02", "QBK03",
                "QSQN01", "QSQN02", "QSQN03",
                "QSLA01", "QSLA02", "QSLA03",
                "QSBK01", "QSBK02", "QSBK03");
    }

    /// 额外诊断不进入正式 21 局配对，也不得被分析器当作性能样本。
    public static List<String> diagnosticRunIds() {
        return List.of("QSCW01", "QSCW02", "QSCW03");
    }

    public static boolean acceptedRunId(String runId) {
        return selectedRunIds().contains(runId) || diagnosticRunIds().contains(runId);
    }

    public static void teardown(BenchClientContext context) {
        var value = session;
        if (value != null) {
            value.teardown(context);
            session = null;
        }
    }

    private static Session current() {
        if (session == null) {
            session = new Session();
        }
        return session;
    }

    private static final class Session {
        private final Variant variant = visualDiagnostic() ? Variant.LASER3 : Variant.selected();
        private final String runId = System.getProperty(RUN_PROPERTY, "");
        private final String batchId = System.getProperty(BATCH_PROPERTY, "");
        private final String sourceCommit = System.getProperty(SOURCE_COMMIT_PROPERTY, "");
        private final String sourceVariant = System.getProperty(SOURCE_VARIANT_PROPERTY, "");
        private final boolean expectedShader = Boolean.parseBoolean(System.getProperty(SHADER_PROPERTY, "false"));
        private final String label = "quiet_matrix_" + labelPart(batchId) + "_" + labelPart(runId) + "_"
                + UUID.randomUUID().toString().replace("-", "");
        private static String labelPart(String value) {
            return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        }

        private final long installedNs = System.nanoTime();
        private final long installedEpochMs = System.currentTimeMillis();
        private final long jvmStartEpochMs = ManagementFactory.getRuntimeMXBean().getStartTime();
        private final long[] premeasureHeads = new long[MAX_PREMEASURE_FRAMES];
        private final JsonArray samples = new JsonArray();
        private final JsonArray readiness = new JsonArray();
        private final JsonArray serverSamples = new JsonArray();
        private final JsonArray casts = new JsonArray();
        private final JsonArray rawManifest = new JsonArray();
        private String frameSessionArtifact;
        private final AtomicBoolean spawnClaimed = new AtomicBoolean();
        private final AtomicBoolean cleanupRequested = new AtomicBoolean();
        private final CompletableFuture<Spawn> spawn = new CompletableFuture<>();
        private final CompletableFuture<Boolean> cleanupFuture = new CompletableFuture<>();
        private final PhotonBenchFocusAudit focus = new PhotonBenchFocusAudit();
        private final PhotonBenchCameraAudit camera = new PhotonBenchCameraAudit();
        private final QueenOwnedLaserEffects.Statistics ownedBefore = QueenOwnedLaserEffects.statistics();
        private volatile Throwable failure;
        private volatile long setupNs;
        private volatile long windowReadyNs;
        private volatile long levelAvailableNs;
        private volatile long playerAvailableNs;
        private volatile long worldConfiguredNs;
        private volatile long premeasureStartedNs;
        private volatile long premeasureStoppedNs;
        private volatile long startNs;
        private volatile long stopObservedNs;
        private volatile long spawnRequestedNs;
        private volatile long spawnCreatedNs;
        private volatile long spawnAddedNs;
        private volatile long clientSeenNs;
        private volatile long clientLastSeenNs;
        private volatile long clientLostNs;
        private volatile long cleanupRequestedNs;
        private volatile long cleanupStartedNs;
        private volatile long entityRemovedNs;
        private volatile int premeasureCount;
        private volatile int premeasureDropped;
        private volatile int maxTargetCount;
        private volatile int targetSubmitCount;
        private volatile int completedCasts;
        private volatile int serverMaxActiveLaser;
        private volatile boolean serverHadLaser;
        private volatile boolean serverQuiescent = true;
        private volatile boolean clientQuiescent = true;
        private volatile boolean allTailsFinished;
        private volatile boolean premeasureStarted;
        private volatile boolean snapshotReadyWritten;
        private volatile boolean visualSnapshotReadyWritten;
        private volatile boolean visualSnapshotAcknowledged;
        private volatile boolean armed;
        private volatile boolean finished;
        private volatile boolean archived;
        private volatile boolean sparkStopping;
        private volatile boolean captureStarted;
        private volatile boolean submissionBound;
        private volatile boolean aliveBeforeCleanup;
        private ClientLevel level;
        private Vec3 origin;
        private CompletableFuture<World> world;
        private ScheduledExecutorService scheduler;
        private ScheduledFuture<?> spawnTask;
        private PhotonBenchSpark spark;
        private JsonObject frames;
        private JsonObject measuredSubmissions;
        private PreMeasure premeasure;
        private Path snapshotAck;
        private Path snapshotImage;
        private String snapshotAckDimensions;
        private Path visualSnapshotAck;
        private Path visualSnapshotImage;
        private String visualSnapshotAckDimensions;
        private long snapshotReadyNs;
        private long snapshotAckNs;
        private long sparkStartedAckNs;
        private long visualSnapshotObservedNs;
        private long visualSnapshotRequestedNs;
        private long lastSampleNs;
        private String rawArchiveError;

        private Session() {
            require(variant != null, "quiet_unknown_scenario");
            require(runId.matches("[A-Za-z0-9_]{1,32}"), "quiet_bad_run_id");
            require(batchId.matches("[A-Za-z0-9_.-]{1,80}"), "quiet_bad_batch_id");
            require(sourceCommit.matches("[0-9a-f]{40}"), "quiet_bad_source_commit");
            require(sourceVariant.matches("[A-Za-z0-9_.-]{1,32}"), "quiet_bad_source_variant");
            require(expectedShader == formalShaderRun() || visualDiagnostic(), "quiet_shader_run_mismatch");
            require(acceptedRunId(runId) || visualDiagnostic(), "quiet_unselected_run_id");
            require(!visualDiagnostic() || variant == Variant.LASER3, "quiet_visual_variant");
        }

        private void premeasureHead(Minecraft minecraft) {
            if (windowReadyNs == 0) {
                var game = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
                require(game.endsWith(Path.of("build", "agent-backups", "20261102-photon-quiet-harness",
                                batchId, runId, "game")),
                        "quiet_wrong_game_directory");
                windowReadyNs = System.nanoTime();
            }
            var window = minecraft.getWindow();
            if (window != null && window.handle() != 0
                    && GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) {
                fail(new IllegalStateException("quiet_window_iconified"));
            }
            if (minecraft.level != null && levelAvailableNs == 0) {
                levelAvailableNs = System.nanoTime();
                level = minecraft.level;
            }
            if (minecraft.player != null && playerAvailableNs == 0) {
                playerAvailableNs = System.nanoTime();
            }
            if (!premeasureStarted && windowReadyNs != 0 && levelAvailableNs != 0 && playerAvailableNs != 0) {
                premeasureStarted = true;
                premeasureStartedNs = System.nanoTime();
            }
            if (!premeasureStarted || armed || failure != null) {
                return;
            }
            if (premeasureCount < MAX_PREMEASURE_FRAMES) {
                premeasureHeads[premeasureCount++] = System.nanoTime();
            } else {
                premeasureDropped++;
            }
        }

        private void beforeRender(Minecraft minecraft) {
            if (world == null && minecraft.level != null && minecraft.player != null
                    && minecraft.getSingleplayerServer() != null) {
                origin = new Vec3(0.5, minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0), 0.5);
                var server = minecraft.getSingleplayerServer();
                var playerId = minecraft.player.getUUID();
                world = CompletableFuture.supplyAsync(() -> configure(server, playerId, origin), server);
            }
            if (!armed || failure != null) {
                holdCamera(minecraft);
            }
            if (armed || failure != null || minecraft.level == null || minecraft.player == null
                    || minecraft.screen != null || minecraft.getOverlay() != null
                    || world == null || !world.isDone() || world.isCompletedExceptionally()) {
                return;
            }
            var window = minecraft.getWindow();
            require(window != null && window.handle() != 0, "quiet_window_missing");
            require(window.getWidth() > 0 && window.getHeight() > 0, "quiet_invalid_window");
            require(GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_ICONIFIED) != GLFW.GLFW_TRUE,
                    "quiet_window_minimized");
            require(IrisCompat.isUsingShaderPack() == expectedShader, "quiet_shader_mismatch");
            require(ModList.get().isLoaded("sodium"), "quiet_requires_sodium");
            require(!PhotonFrameCapture.running() && !PhotonPerformanceCapture.active(),
                    "quiet_capture_owned_elsewhere");
            if (!snapshotReadyWritten) {
                writeSnapshotReady();
                return;
            }
            if (!snapshotAcknowledged()) {
                return;
            }
            if (snapshotAckNs == 0) {
                snapshotAckNs = System.nanoTime();
                premeasureSnapshotCompleted(snapshotAck);
                require(submissionReady(), "quiet_submission_diagnostics_not_requested");
            }
            armAfterSparkReady();
        }

        private void writeVisualSnapshotReady() {
            writeSnapshotReady(VISUAL_SNAPSHOT_READY_FILE_PROPERTY, label, "quiet_visual_snapshot_ready");
            visualSnapshotReadyWritten = true;
            visualSnapshotRequestedNs = System.nanoTime();
        }

        private void writeSnapshotReady() {
            writeSnapshotReady(SNAPSHOT_READY_FILE_PROPERTY, label, "quiet_snapshot_ready");
            snapshotReadyWritten = true;
            snapshotReadyNs = System.nanoTime();
        }

        private void writeSnapshotReady(String property, String owner, String phase) {
            var value = System.getProperty(property, "");
            require(!value.isBlank(), "quiet_snapshot_ready_path_missing");
            var path = Path.of(value).toAbsolutePath().normalize();
            var game = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
            require(path.startsWith(game.resolve("performance")) && path.getParent().equals(game.resolve("performance")),
                    "quiet_snapshot_ready_outside_owner");
            if (Files.exists(path)) {
                throw new IllegalStateException("quiet_snapshot_ready_exists:" + phase);
            }
            try {
                Files.createDirectories(path.getParent());
                var temporary = path.resolveSibling(path.getFileName() + ".tmp");
                Files.writeString(temporary, owner + "\n", StandardCharsets.UTF_8);
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception problem) {
                throw new IllegalStateException("quiet_snapshot_ready_write_failed", problem);
            }
            Lobecorp.LOGGER.info("QUIET_MATRIX runId={} phase={} path={}", runId, phase, path);
        }

        private boolean snapshotAcknowledged() {
            var result = readSnapshotAck(SNAPSHOT_ACK_FILE_PROPERTY, SNAPSHOT_IMAGE_PROPERTY, label);
            if (result == null) {
                return false;
            }
            snapshotAck = result.path();
            snapshotImage = result.image();
            snapshotAckDimensions = result.dimensions();
            return true;
        }

        private boolean visualSnapshotAcknowledged() {
            var result = readSnapshotAck(VISUAL_SNAPSHOT_ACK_FILE_PROPERTY, VISUAL_SNAPSHOT_IMAGE_PROPERTY, label);
            if (result == null) {
                return false;
            }
            visualSnapshotAck = result.path();
            visualSnapshotImage = result.image();
            visualSnapshotAckDimensions = result.dimensions();
            return true;
        }

        private SnapshotAck readSnapshotAck(String ackProperty, String imageProperty, String owner) {
            var value = System.getProperty(ackProperty, "");
            require(!value.isBlank(), "quiet_snapshot_ack_path_missing");
            var path = Path.of(value).toAbsolutePath().normalize();
            var game = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
            require(path.startsWith(game.resolve("performance")) && path.getParent().equals(game.resolve("performance")),
                    "quiet_snapshot_ack_outside_owner");
            if (!Files.isRegularFile(path)) {
                return null;
            }
            try {
                var ack = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                require(SNAPSHOT_SCHEMA.equals(ack.get("schema").getAsString()), "quiet_snapshot_ack_schema");
                require(runId.equals(ack.get("runId").getAsString()), "quiet_snapshot_ack_run_id");
                require(batchId.equals(ack.get("batchId").getAsString()), "quiet_snapshot_ack_batch_id");
                require(owner.equals(ack.get("label").getAsString()), "quiet_snapshot_ack_identity");
                var dimensions = ack.get("dimensions").getAsString();
                require(dimensions.matches("[0-9]+x[0-9]+"), "quiet_snapshot_ack_dimensions");
                var image = Path.of(ack.get("image").getAsString()).toAbsolutePath().normalize();
                var configured = Path.of(System.getProperty(imageProperty, "")).toAbsolutePath().normalize();
                require(configured.getParent().equals(game.resolve("performance")), "quiet_snapshot_image_outside_owner");
                require(image.equals(configured), "quiet_snapshot_image_mismatch");
                require(Files.isRegularFile(image), "quiet_snapshot_image_missing");
                var expected = ack.get("expectedDimensions").getAsString();
                require(expected.matches("[0-9]+x[0-9]+"), "quiet_snapshot_expected_dimensions");
                require(expected.equals(dimensions), "quiet_snapshot_dimensions_mismatch");
                var configuredDimensions = System.getProperty(EXPECTED_DIMENSIONS_PROPERTY, "");
                require(configuredDimensions.matches("[0-9]+x[0-9]+"), "quiet_expected_dimensions_missing");
                require(configuredDimensions.equals(expected), "quiet_expected_dimensions_mismatch");
                var window = Minecraft.getInstance().getWindow();
                require(expected.equals(window.getWidth() + "x" + window.getHeight()),
                        "quiet_snapshot_dimensions_window_mismatch");
                require(ack.get("pid").getAsLong() == ProcessHandle.current().pid(), "quiet_snapshot_pid");
                var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(image)));
                require(digest.equals(ack.get("sha256").getAsString()), "quiet_snapshot_image_hash");
                return new SnapshotAck(path, image, dimensions);
            } catch (Exception problem) {
                throw new IllegalStateException("quiet_snapshot_ack_read_failed", problem);
            }
        }

        private boolean submissionReady() {
            if (!PhotonSubmissionDiagnostics.probesRequested()) {
                return false;
            }
            try {
                PhotonSubmissionDiagnostics.beginCase(label + "_measure");
                submissionBound = true;
                return true;
            } catch (RuntimeException problem) {
                throw new IllegalStateException("quiet_submission_owner", problem);
            }
        }

        private void armAfterSparkReady() {
            require(failure == null, "quiet_start_after_failure");
            if (armed) {
                return;
            }
            premeasureStoppedNs = System.nanoTime();
            premeasure = new PreMeasure(premeasureCount, premeasureHeads.clone(), premeasureStartedNs,
                    premeasureStoppedNs, premeasureSummary());
            if (spark == null) {
                spark = PhotonBenchSpark.start(Minecraft.getInstance(), runId);
            }
            spark.poll();
            if (!spark.running()) {
                return;
            }
            sparkStartedAckNs = spark.snapshot().get("started_ack_ns").getAsLong();
            require(sparkStartedAckNs > 0, "quiet_spark_started_ack_missing");
            PhotonBenchConditions.begin(0x51);
            PhotonPerformanceCapture.start(label, variant.seconds, PhotonPerformanceTraceUtil.Mode.GPU);
            captureStarted = true;
            if (submissionBound) {
                PhotonSubmissionDiagnostics.bindFrameCapture();
            }
            armed = true;
            PhotonFrameCapture.phase("five_seconds", SPAWN_SECONDS, this::entered);
            if (variant.laser) {
                for (int index = 0; index < 3; index++) {
                    final int ordinal = index;
                    long target = TimeUnit.SECONDS.toNanos(10L + index * 10L);
                    PhotonFrameCapture.phase("cast_" + ordinal, target / 1e9, () -> requestCast(ordinal, target));
                }
            }
            Lobecorp.LOGGER.info("QUIET_MATRIX runId={} phase=armed variant={} shader={} pid={}",
                    runId, variant.label, expectedShader, ProcessHandle.current().pid());
        }

        private void premeasureSnapshotCompleted(Path path) {
            Lobecorp.LOGGER.info("QUIET_MATRIX runId={} phase=premeasure_snapshot_complete path={}",
                    runId, path);
        }

        private void entered() {
            startNs = PhotonFrameCapture.liveStatus().get("started_ns").getAsLong();
            require(startNs != 0, "quiet_t0_missing");
            scheduler = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("photon-quiet-" + runId).factory());
            scheduler.schedule(() -> {
                try {
                    cleanup();
                } catch (RuntimeException | LinkageError problem) {
                    fail(problem);
                } finally {
                    scheduler.shutdown();
                }
            }, Math.max(0, deadlineNs() - System.nanoTime()), TimeUnit.NANOSECONDS);
            spawnTask = scheduler.schedule(() -> {
                if (!spawnClaimed.compareAndSet(false, true)) {
                    return;
                }
                spawnRequestedNs = System.nanoTime();
                checkDispatch(spawnRequestedNs, "quiet_spawn_scheduler_late");
                world.thenCompose(value -> CompletableFuture.supplyAsync(
                                () -> add(value, spawnRequestedNs), value.server))
                        .whenComplete((result, problem) -> {
                            if (problem == null) {
                                spawn.complete(result);
                            } else {
                                fail(problem);
                                spawn.completeExceptionally(problem);
                            }
                        });
            }, Math.max(0, spawnTargetNs() - System.nanoTime()), TimeUnit.NANOSECONDS);
            Lobecorp.LOGGER.info("QUIET_MATRIX runId={} phase=t0 t0Ns={} target={}", runId, startNs, variant.target);
        }

        private World configure(MinecraftServer server, UUID playerId, Vec3 position) {
            var target = server.overworld();
            var expected = System.getProperty("modBench.client.worldId", "") + "-flat";
            require(server.getWorldData().getLevelName().equals(expected), "quiet_world_identity");
            require(System.getProperty("modBench.client.worldPreset", "").equals("flat"),
                    "quiet_requires_flat_preset");
            var clock = target.dimensionType().defaultClock().orElseThrow();
            require(server.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.NOON), "quiet_noon");
            target.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
            target.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
            target.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.setWeatherParameters(0, 0, false, false);
            server.setDifficulty(Difficulty.NORMAL, true);
            var player = server.getPlayerList().getPlayer(playerId);
            require(player != null, "quiet_player_missing");
            player.teleportTo(target, position.x, position.y, position.z - 10, Set.of(), 0, 0, false);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            worldConfiguredNs = System.nanoTime();
            return new World(server, target, playerId, position);
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

        private Spawn add(World value, long requested) {
            long created = System.nanoTime();
            spawnCreatedNs = created;
            checkDispatch(created, "quiet_spawn_server_late");
            if (variant.target == Target.BLANK) {
                return new Spawn(false, null, -1, requested, created, System.nanoTime());
            }
            var expectedType = variant.target == Target.COW ? EntityType.COW
                    : AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get();
            require(value.level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(value.origin, value.origin).inflate(64),
                    entity -> entity.getType() == expectedType).isEmpty(), "quiet_target_already_exists");
            var entity = expectedType.create(value.level, EntitySpawnReason.COMMAND);
            require(entity != null, "quiet_target_create_failed");
            entity.setPos(value.origin);
            entity.setNoAi(true);
            entity.setInvulnerable(true);
            entity.setPersistenceRequired();
            entity.setYRot(variant.laser ? 0 : 180);
            entity.setYHeadRot(variant.laser ? 0 : 180);
            entity.yBodyRot = variant.laser ? 0 : 180;
            require(value.level.addFreshEntity(entity), "quiet_target_spawn_rejected");
            long added = System.nanoTime();
            spawnAddedNs = added;
            Lobecorp.LOGGER.info("QUIET_MATRIX runId={} phase=target_added type={} uuid={} requestedNs={} addedNs={}",
                    runId, variant.target, entity.getUUID(), requested, added);
            return new Spawn(true, entity.getUUID(), entity.getId(), requested, created, added);
        }

        private void requestCast(int ordinal, long targetOffsetNs) {
            long requested = System.nanoTime();
            Minecraft.getInstance().execute(() -> {
                try {
                    observeClient();
                    require(failure == null && !cleanupRequested.get(), "quiet_cast_after_failure");
                    long target = startNs + targetOffsetNs;
                    checkDispatch(requested, "quiet_cast_scheduler_late");
                    require(requested >= target, "quiet_cast_scheduler_early");
                    require(clientQuiescent, "quiet_previous_client_tail_alive");
                    var stats = QueenOwnedLaserEffects.statistics();
                    require(stats.started() - ownedBefore.started() == ordinal,
                            "quiet_previous_cast_not_observed");
                    require(stats.fallbacks() == ownedBefore.fallbacks() && stats.errors() == ownedBefore.errors(),
                            "quiet_owned_fallback");
                    require(ready(), "quiet_resources_not_ready_at_cast");
                    require(statisticsRequested(), "quiet_owned_statistics_not_requested");
                    world.thenCompose(value -> CompletableFuture.supplyAsync(
                                    () -> cast(value, ordinal, target, requested), value.server))
                            .whenComplete((result, problem) -> {
                                if (problem != null) {
                                    fail(problem);
                                }
                            });
                } catch (RuntimeException | LinkageError problem) {
                    fail(problem);
                }
            });
        }

        private JsonObject cast(World value, int ordinal, long target, long requested) {
            long actual = System.nanoTime();
            checkDispatch(actual, "quiet_cast_server_late");
            require(actual >= target, "quiet_cast_server_early");
            var spawned = spawn.getNow(null);
            require(spawned != null && spawned.present(), "quiet_cast_target_missing");
            var entity = value.level.getEntity(spawned.uuid());
            require(entity instanceof TheQueenOfHatred, "quiet_cast_queen_missing");
            var queen = (TheQueenOfHatred) entity;
            require(queen.isAlive(), "quiet_cast_queen_missing");
            var skills = IEntitySkillAccess.get(queen);
            require(skills != null && !skills.hasActiveSkills(), "quiet_previous_skill_alive");
            require(completedCasts == ordinal, "quiet_previous_skill_not_completed");
            var position = value.origin.add(0, 0, 16);
            var outcome = skills.cast(TheQueenOfHatredSkills.LASER.get(), null, position);
            var item = new JsonObject();
            item.addProperty("ordinal", ordinal + 1);
            item.addProperty("target_ns", target);
            item.addProperty("requested_ns", requested);
            item.addProperty("started_ns", actual);
            item.addProperty("confirmed_ns", System.nanoTime());
            item.addProperty("status", outcome.status().name());
            item.addProperty("started", outcome.started());
            item.addProperty("queen_uuid", queen.getUUID().toString());
            item.add("position_target", new Gson().toJsonTree(List.of(position.x, position.y, position.z)));
            casts.add(item);
            require(outcome.started() && outcome.status().name().equals("STARTED"),
                    "quiet_cast_not_started:" + outcome.status());
            serverHadLaser = true;
            serverQuiescent = false;
            observeServer(value);
            return item;
        }

        private boolean ready() {
            var key = new PhotonOwnedEffectResources.SourceKey(PhotonOwnedLaser.ID,
                    PhotonOwnedEffectResources.SourceKind.EXPORTED_FX,
                    PhotonOwnedEffectResources.resourceRevision());
            return PhotonOwnedEffectResources.lookup(key).readiness()
                    == PhotonOwnedEffectResources.Readiness.READY;
        }

        private boolean statisticsRequested() {
            return Boolean.getBoolean("lobecorp.photon.bench.ownedStatistics")
                    || QueenOwnedLaserEffects.statistics().started() > 0;
        }

        private void observeServer(World value) {
            var spawned = spawn.getNow(null);
            if (spawned == null || !spawned.present() || cleanupRequested.get()) {
                return;
            }
            var entity = value.level.getEntity(spawned.uuid());
            require(entity instanceof TheQueenOfHatred, "quiet_server_queen_lost");
            var queen = (TheQueenOfHatred) entity;
            require(queen.isAlive(), "quiet_server_queen_lost");
            var skills = IEntitySkillAccess.get(queen);
            require(skills != null, "quiet_server_skills_missing");
            int active = (int) skills.activeSkills().stream()
                    .filter(runtime -> runtime.skill() instanceof LaserSkill).count();
            serverMaxActiveLaser = Math.max(serverMaxActiveLaser, active);
            require(active <= 1, "quiet_server_laser_overlap");
            if (active == 0 && serverHadLaser) {
                completedCasts++;
                serverHadLaser = false;
            }
            serverQuiescent = active == 0;
            var sample = new JsonObject();
            sample.addProperty("ns", System.nanoTime());
            sample.addProperty("offset_seconds", startNs == 0 ? 0 : (System.nanoTime() - startNs) / 1e9);
            sample.addProperty("active_laser", active);
            sample.addProperty("completed_casts", completedCasts);
            synchronized (serverSamples) {
                serverSamples.add(sample);
            }
        }

        private void observeClient() {
            var ownedAfter = QueenOwnedLaserEffects.statistics();
            var legacy = QueenBeamEffects.retentionStatistics();
            clientQuiescent = ownedAfter.playing() == 0 && ownedAfter.tails() == 0 && legacy.playing() == 0;
            if (variant.laser && ownedAfter.started() - ownedBefore.started() == 3 && clientQuiescent) {
                allTailsFinished = true;
            }
        }

        private void observeEntitySubmit(EntityType<?> type) {
            if (variant.target == Target.BLANK) {
                return;
            }
            var expected = variant.target == Target.COW ? EntityType.COW
                    : AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get();
            if (type == expected) {
                targetSubmitCount++;
            }
        }

        private void afterFrame(Minecraft minecraft) {
            pollSpark();
            focus.audit(armed && !finished, armed ? "measure" : "premeasure");
            if (spawn.isDone() && !spawn.isCompletedExceptionally() && !spawn.isCancelled()) {
                var value = spawn.getNow(null);
                if (value != null && value.present() && clientSeenNs == 0 && minecraft.level != null) {
                    var entity = minecraft.level.getEntity(value.id());
                    if (entity != null && value.uuid().equals(entity.getUUID())) {
                        clientSeenNs = System.nanoTime();
                    }
                }
            }
            if (!armed || finished) {
                return;
            }
            observeClient();
            long now = System.nanoTime();
            var currentLevel = minecraft.level;
            var player = minecraft.player;
            if (currentLevel != level || currentLevel == null || player == null) {
                fail(new IllegalStateException("quiet_world_changed"));
            } else {
                camera.sample();
                var spawned = spawn.getNow(null);
                if (spawned != null && spawned.present()) {
                    var entity = currentLevel.getEntity(spawned.id());
                    boolean present = entity != null && spawned.uuid().equals(entity.getUUID()) && entity.isAlive();
                    if (present) {
                        clientLastSeenNs = now;
                    } else if (clientSeenNs != 0 && now < deadlineNs() && clientLostNs == 0) {
                        clientLostNs = now;
                        fail(new IllegalStateException("quiet_client_entity_lost"));
                    }
                }
                if (now - lastSampleNs >= TimeUnit.MILLISECONDS.toNanos(100)) {
                    lastSampleNs = now;
                    int count = targetCount(currentLevel, player.getBoundingBox().inflate(64));
                    maxTargetCount = Math.max(maxTargetCount, count);
                    require(count <= (variant.target == Target.BLANK ? 0 : 1), "quiet_target_count");
                    var sample = new JsonObject();
                    sample.addProperty("ns", now);
                    sample.addProperty("offset_seconds", (now - startNs) / 1e9);
                    sample.addProperty("target_count", count);
                    samples.add(sample);
                    var item = new JsonObject();
                    item.addProperty("ns", now);
                    item.addProperty("offset_seconds", (now - startNs) / 1e9);
                    item.addProperty("revision", PhotonOwnedEffectResources.resourceRevision());
                    item.addProperty("readiness", variant.laser ? readyName() : "NOT_APPLICABLE");
                    readiness.add(item);
                }
            }
            if (visualDiagnostic()) {
                captureVisualSnapshot();
            }
            if (!PhotonPerformanceCapture.active()) {
                frames = PhotonFrameCapture.snapshot();
                stopObservedNs = now;
                finished = true;
                require(frames.has("label") && label.equals(frames.get("label").getAsString()), "quiet_frame_owner");
                require("DURATION".equals(frames.get("state").getAsString()), "quiet_frame_ended_early");
                cleanup();
                Lobecorp.LOGGER.info("QUIET_MATRIX runId={} phase=finished t0Ns={} stopNs={} delayNs={}",
                        runId, startNs, stopObservedNs, stopObservedNs - deadlineNs());
            }
        }

        private void captureVisualSnapshot() {
            if (visualSnapshotAcknowledged || failure != null) {
                return;
            }
            var stats = QueenOwnedLaserEffects.statistics();
            var firstActive = stats.components().stream()
                    .filter(value -> value.ordinal() > ownedBefore.started())
                    .findFirst();
            if (firstActive.isEmpty() || firstActive.get().activeObserved().size() < PhotonOwnedLaser.COMPONENTS.size()) {
                return;
            }
            if (!visualSnapshotReadyWritten) {
                writeVisualSnapshotReady();
                return;
            }
            if (visualSnapshotAcknowledged()) {
                visualSnapshotAcknowledged = true;
                visualSnapshotObservedNs = System.nanoTime();
                Lobecorp.LOGGER.info("QUIET_MATRIX runId={} phase=visual_snapshot_ack path={}",
                        runId, visualSnapshotAck);
            }
        }

        private String readyName() {
            var key = new PhotonOwnedEffectResources.SourceKey(PhotonOwnedLaser.ID,
                    PhotonOwnedEffectResources.SourceKind.EXPORTED_FX,
                    PhotonOwnedEffectResources.resourceRevision());
            return PhotonOwnedEffectResources.lookup(key).readiness().name();
        }

        private int targetCount(ClientLevel level, AABB bounds) {
            if (variant.target == Target.BLANK) {
                return level.getEntitiesOfClass(LivingEntity.class, bounds, entity ->
                        entity.getType() == EntityType.COW
                                || entity.getType() == AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get()).size();
            }
            var type = variant.target == Target.COW ? EntityType.COW
                    : AbnormalitieEntityTypes.THE_QUEEN_OF_HATRED.get();
            return level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity.getType() == type).size();
        }

        private void observeSubmission(Minecraft minecraft) {
            if (!armed || submissionBound || !PhotonSubmissionDiagnostics.probesRequested()) {
                return;
            }
            if (minecraft.screen == null && minecraft.getOverlay() == null) {
                PhotonSubmissionDiagnostics.beginFrame();
            }
        }

        private void pollSpark() {
            if (spark != null) {
                spark.poll();
            }
        }

        private void cleanup() {
            if (!cleanupRequested.compareAndSet(false, true)) {
                return;
            }
            cleanupRequestedNs = System.nanoTime();
            if (spawnClaimed.compareAndSet(false, true) && spawnTask != null) {
                spawnTask.cancel(false);
                spawn.complete(null);
            }
            if (world == null) {
                cleanupFuture.complete(true);
                return;
            }
            world.thenCompose(value -> CompletableFuture.supplyAsync(() -> {
                        cleanupStartedNs = System.nanoTime();
                        var result = spawn.getNow(null);
                        if (result == null || !result.present()) {
                            return true;
                        }
                        var entity = value.level.getEntity(result.uuid());
                        aliveBeforeCleanup = entity != null && entity.isAlive();
                        if (!aliveBeforeCleanup) {
                            fail(new IllegalStateException("quiet_entity_not_alive_before_cleanup"));
                        }
                        if (entity != null) {
                            var skills = IEntitySkillAccess.get((LivingEntity) entity);
                            if (skills != null) {
                                skills.cancelAll(true);
                            }
                            entity.discard();
                            entityRemovedNs = System.nanoTime();
                        }
                        return value.level.getEntity(result.uuid()) == null;
                    }, value.server))
                    .whenComplete((result, problem) -> {
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

        private void checkDispatch(long actual, String reason) {
            var target = reason.startsWith("quiet_spawn") ? spawnTargetNs()
                    : startNs + TimeUnit.SECONDS.toNanos(10L + Math.max(0, completedCasts) * 10L);
            if (actual < target || actual - target > MAX_DISPATCH_DELAY_NS) {
                throw new IllegalStateException(reason + ": target=" + target + ", actual=" + actual);
            }
        }

        private long spawnTargetNs() {
            return startNs + TimeUnit.SECONDS.toNanos(SPAWN_SECONDS);
        }

        private long deadlineNs() {
            return startNs + TimeUnit.SECONDS.toNanos(variant.seconds);
        }

        private JsonObject premeasureSummary() {
            var result = new JsonObject();
            result.addProperty("scope", "public_flat_world_and_standard_snapshot_before_any_measured_entity_or_effect");
            result.addProperty("started_ns", premeasureStartedNs);
            result.addProperty("stopped_ns", premeasureStoppedNs);
            result.addProperty("duration_ns", premeasureStoppedNs - premeasureStartedNs);
            result.addProperty("frame_count", premeasureCount);
            result.addProperty("dropped_frames", premeasureDropped);
            result.addProperty("snapshot_sha256", snapshotSha());
            return result;
        }

        private String snapshotSha() {
            if (snapshotImage == null || !Files.isRegularFile(snapshotImage)) {
                return null;
            }
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(snapshotImage)));
            } catch (Exception problem) {
                fail(problem);
                return null;
            }
        }

        private void archive(BenchClientContext context) throws Exception {
            if (archived) {
                return;
            }
            if (visualDiagnostic()) {
                archiveVisual(context);
                return;
            }
            var profile = PhotonPerformanceCapture.status();
            require(!profile.get("active").getAsBoolean() && !profile.get("exporting").getAsBoolean(),
                    "quiet_profile_active");
            measuredSubmissions = PhotonSubmissionDiagnostics.endCase();
            validate();
            archivePreMeasure(context);
            archiveFrames(context);
            require(measuredSubmissions.get("scope_protocol_errors").getAsLong() == 0,
                    "quiet_submission_scope_errors");
            spark.archive(context);
            var result = report();
            result.addProperty("archived", true);
            result.add("frame_artifacts", rawManifest.deepCopy());
            context.artifacts().write(label + "_quiet.json", "application/json", result.toString());
            archived = true;
        }

        private void archiveVisual(BenchClientContext context) throws Exception {
            validateVisual();
            var profile = PhotonPerformanceCapture.status();
            require(!profile.get("active").getAsBoolean() && !profile.get("exporting").getAsBoolean(),
                    "quiet_visual_profile_active");
            archivePreMeasure(context);
            archiveVisualSnapshot(context);
            archiveFrames(context);
            measuredSubmissions = PhotonSubmissionDiagnostics.endCase();
            spark.archive(context);
            var result = report();
            result.addProperty("archived", true);
            result.add("frame_artifacts", rawManifest.deepCopy());
            context.artifacts().write(label + "_quiet_visual.json", "application/json", result.toString());
            archived = true;
        }

        private void archivePreMeasure(BenchClientContext context) throws Exception {
            require(premeasure != null, "quiet_premeasure_missing");
            var ack = JsonParser.parseString(Files.readString(snapshotAck, StandardCharsets.UTF_8)).getAsJsonObject();
            var csv = new StringBuilder("frame_id,head_ns,offset_ns,interval_ns\n");
            for (int index = 0; index < premeasure.frameCount(); index++) {
                long head = premeasure.heads()[index];
                csv.append(index).append(',').append(head).append(',')
                        .append(head - premeasure.startedNs()).append(',')
                        .append(index == 0 ? "" : Long.toString(head - premeasure.heads()[index - 1])).append('\n');
            }
            context.artifacts().write(label + "_premeasure_frames.csv", "text/csv", csv.toString());
            var summary = premeasure.summary();
            context.artifacts().write(label + "_premeasure_summary.json", "application/json", summary.toString());
            var bytes = Files.readAllBytes(snapshotImage);
            var target = context.artifacts().write(label + "_premeasure_snapshot.png", "image/png", bytes);
            var item = new JsonObject();
            item.addProperty("source", snapshotImage.toString());
            item.addProperty("artifact", target.toString());
            item.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            item.addProperty("scope", "standard_bench_snapshot_before_measure_and_target_spawn");
            item.addProperty("label", ack.get("label").getAsString());
            item.addProperty("pid", ack.get("pid").getAsLong());
            item.addProperty("dimensions", ack.get("dimensions").getAsString());
            rawManifest.add(item);
        }

        private void archiveVisualSnapshot(BenchClientContext context) throws Exception {
            var ack = JsonParser.parseString(Files.readString(visualSnapshotAck, StandardCharsets.UTF_8)).getAsJsonObject();
            var bytes = Files.readAllBytes(visualSnapshotImage);
            var target = context.artifacts().write(label + "_visual_active.png", "image/png", bytes);
            var item = new JsonObject();
            item.addProperty("source", visualSnapshotImage.toString());
            item.addProperty("artifact", target.toString());
            item.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            item.addProperty("scope", "real_laser_first_active_phase_after_fifteen_components_observed");
            item.addProperty("label", ack.get("label").getAsString());
            item.addProperty("pid", ack.get("pid").getAsLong());
            item.addProperty("dimensions", ack.get("dimensions").getAsString());
            rawManifest.add(item);
        }

        private void archiveFrames(BenchClientContext context) throws Exception {
            var profile = PhotonPerformanceCapture.status();
            var output = profile.get("output");
            require(output != null && !output.isJsonNull(), "quiet_profile_output_missing");
            var directory = Path.of(output.getAsString()).toAbsolutePath().normalize();
            var performance = context.minecraft().gameDirectory.toPath().resolve("performance")
                    .toAbsolutePath().normalize();
            require(directory.startsWith(performance), "quiet_profile_outside_owner");
            archiveProfileFile(context, directory, "session.json", "application/json",
                    "trace session origin and frame_origin_ns alignment");
            archiveProfileFile(context, directory, "frames.csv", "text/csv",
                    "measured_natural_window_full_45_or_25_seconds");
        }

        private void archiveProfileFile(BenchClientContext context, Path directory, String name, String mediaType,
                                        String scope) throws Exception {
            var source = directory.resolve(name);
            require(Files.isRegularFile(source), "quiet_profile_file_missing:" + name);
            var bytes = Files.readAllBytes(source);
            var artifact = context.artifacts().write(label + "_" + name, mediaType, bytes);
            var item = new JsonObject();
            item.addProperty("source", source.toString());
            item.addProperty("artifact", artifact.toString());
            item.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            item.addProperty("scope", scope);
            rawManifest.add(item);
        }

        private JsonObject report() {
            var result = new JsonObject();
            result.addProperty("schema", "photon-quiet-v1");
            result.addProperty("run_id", runId);
            result.addProperty("batch_id", batchId);
            result.addProperty("source_commit", sourceCommit);
            result.addProperty("source_variant", sourceVariant);
            result.addProperty("class_loader", getClass().getClassLoader().getName());
            result.addProperty("class_location", classLocation());
            result.addProperty("class_sha256", classSha256());
            result.addProperty("sodium_artifact", modArtifact("sodium"));
            result.addProperty("iris_artifact", modArtifact("iris"));
            result.addProperty("expected_dimensions", System.getProperty(EXPECTED_DIMENSIONS_PROPERTY, ""));
            result.addProperty("label", label);
            result.addProperty("variant", variant.label);
            result.addProperty("scenario_id", variant.id);
            result.addProperty("pid", ProcessHandle.current().pid());
            result.addProperty("expected_shader", expectedShader);
            result.addProperty("shader_in_use", IrisCompat.isUsingShaderPack());
            result.addProperty("sodium_loaded", ModList.get().isLoaded("sodium"));
            result.addProperty("capture_seconds", variant.seconds);
            result.addProperty("spawn_seconds", SPAWN_SECONDS);
            result.addProperty("entry_t0_ns", startNs);
            result.addProperty("observer_installed_ns", installedNs);
            result.addProperty("observer_installed_epoch_ms", installedEpochMs);
            result.addProperty("jvm_started_epoch_ms", jvmStartEpochMs);
            result.addProperty("setup_ns", setupNs);
            result.addProperty("window_ready_ns", windowReadyNs);
            result.addProperty("level_available_ns", levelAvailableNs);
            result.addProperty("player_available_ns", playerAvailableNs);
            result.addProperty("world_configured_ns", worldConfiguredNs);
            result.addProperty("premeasure_started_ns", premeasureStartedNs);
            result.addProperty("premeasure_stopped_ns", premeasureStoppedNs);
            result.addProperty("premeasure_duration_ns", premeasureStoppedNs - premeasureStartedNs);
            result.addProperty("snapshot_ready_ns", snapshotReadyNs);
            result.addProperty("snapshot_ack_ns", snapshotAckNs);
            result.addProperty("spark_started_ack_ns", sparkStartedAckNs);
            result.addProperty("snapshot_ready_file", System.getProperty(SNAPSHOT_READY_FILE_PROPERTY, ""));
            result.addProperty("snapshot_ack_file", snapshotAck == null ? "" : snapshotAck.toString());
            result.addProperty("snapshot_image", snapshotImage == null ? "" : snapshotImage.toString());
            result.addProperty("visual_snapshot_ready_file", visualDiagnostic()
                    ? System.getProperty(VISUAL_SNAPSHOT_READY_FILE_PROPERTY, "") : "");
            result.addProperty("visual_snapshot_ack_file", visualSnapshotAck == null ? "" : visualSnapshotAck.toString());
            result.addProperty("visual_snapshot_image", visualSnapshotImage == null ? "" : visualSnapshotImage.toString());
            result.addProperty("visual_snapshot_observed_ns", visualSnapshotObservedNs);
            result.addProperty("visual_snapshot_requested_ns", visualSnapshotRequestedNs);
            result.addProperty("visual_snapshot_dimensions", visualSnapshotAckDimensions);
            var currentGame = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
            result.addProperty("game_directory", currentGame.toString());
            result.addProperty("game_performance_directory", currentGame.resolve("performance").toString());
            result.addProperty("batch_directory", currentGame.getParent().getParent().toString());
            result.addProperty("run_directory", currentGame.getParent().toString());
            result.addProperty("frame_session_artifact", frameSessionArtifact);
            result.addProperty("snapshot_dimensions", snapshotAckDimensions);
            result.addProperty("snapshot_schema", SNAPSHOT_SCHEMA);
            result.addProperty("snapshot_ack_run_id", runId);
            result.addProperty("premeasure_frames", premeasureCount);
            result.addProperty("premeasure_dropped_frames", premeasureDropped);
            result.add("premeasure", premeasure == null ? new JsonObject() : premeasure.summary());
            result.addProperty("spawn_target_ns", startNs + TimeUnit.SECONDS.toNanos(SPAWN_SECONDS));
            result.addProperty("spawn_requested_ns", spawnRequestedNs);
            result.addProperty("spawn_created_ns", spawnCreatedNs);
            result.addProperty("spawn_added_ns", spawnAddedNs);
            result.addProperty("client_seen_ns", clientSeenNs);
            result.addProperty("client_last_seen_ns", clientLastSeenNs);
            result.addProperty("client_lost_ns", clientLostNs);
            result.addProperty("max_target_count", maxTargetCount);
            result.addProperty("target_submit_count", targetSubmitCount);
            result.addProperty("completed_casts", completedCasts);
            result.addProperty("server_max_active_laser", serverMaxActiveLaser);
            result.addProperty("all_tails_finished", allTailsFinished);
            result.addProperty("spark_owner", spark == null ? null : spark.snapshot().get("sender_name").getAsString());
            result.add("spark", spark == null ? new JsonObject() : spark.snapshot());
            result.addProperty("stop_observed_ns", stopObservedNs);
            result.addProperty("cleanup_requested_ns", cleanupRequestedNs);
            result.addProperty("cleanup_started_ns", cleanupStartedNs);
            result.addProperty("entity_removed_ns", entityRemovedNs);
            result.addProperty("cleanup_complete", cleanupDone());
            result.addProperty("finished", finished);
            result.addProperty("failure", failure == null ? null : failure.toString());
            result.addProperty("frame_owner", label);
            result.addProperty("frame_state", frames == null ? null : frames.get("state").getAsString());
            result.addProperty("frame_count", frames == null ? 0 : frames.get("frame_count").getAsInt());
            result.add("frame_summary", frames == null ? new JsonObject() : frames.deepCopy());
            result.add("owned_before", new Gson().toJsonTree(ownedBefore));
            result.add("owned_after", new Gson().toJsonTree(QueenOwnedLaserEffects.statistics()));
            result.add("casts", casts.deepCopy());
            result.add("readiness", readiness.deepCopy());
            result.add("samples", samples.deepCopy());
            synchronized (serverSamples) {
                result.add("server_samples", serverSamples.deepCopy());
            }
            result.add("focus_audit", focus.snapshot());
            result.add("camera_audit", cameraSnapshot());
            result.addProperty("submission_probes", PhotonSubmissionDiagnostics.probesRequested());
            result.add("measure_submissions", measuredSubmissions == null
                    ? new JsonObject() : measuredSubmissions.deepCopy());
            result.addProperty("premeasure_snapshot_required", true);
            result.addProperty("premeasure_snapshot_completed", snapshotAck != null);
            result.addProperty("snapshot_ack_schema", SNAPSHOT_SCHEMA);
            result.addProperty("visual_diagnostic", visualDiagnostic());
            result.addProperty("performance_matrix_eligible", !visualDiagnostic());
            result.addProperty("visual_snapshot", visualDiagnostic() && visualSnapshotAck != null);
            result.addProperty("measurement_t0_scope",
                    "first GameRenderer HEAD after public flat world, standard snapshot ack and spark RUNNING ack; target spawn scheduled at +5s");
            result.addProperty("t0_after_spark_started_ack", sparkStartedAckNs != 0 && startNs > sparkStartedAckNs);
            result.addProperty("premeasure_scope",
                    "public world terrain and snapshot stabilization only; no measured entity/effect; retained raw frames and duration");
            result.addProperty("owned_statistics_overlay",
                    Boolean.getBoolean("lobecorp.photon.bench.ownedStatistics"));
            result.addProperty("owned_statistics_overlay_scope",
                    "common test harness observation only; applied to both A and B; excluded from optimization gain");
            result.addProperty("laser_schedule_seconds", "[10,20,30]");
            result.addProperty("full_window_required", true);
            result.addProperty("active_window_diagnostic_only", true);
            result.add("vm_arguments", new Gson().toJsonTree(ManagementFactory.getRuntimeMXBean().getInputArguments()));
            return result;
        }

        private JsonObject cameraSnapshot() {
            var result = new JsonObject();
            result.addProperty("samples", camera.samples());
            result.addProperty("changes", camera.changes());
            result.addProperty("unavailable_frames", camera.unavailableFrames());
            result.add("first", camera.first() == null ? new JsonObject() : camera.first());
            result.add("last", camera.last() == null ? new JsonObject() : camera.last());
            return result;
        }

        private String classLocation() {
            try {
                var resource = getClass().getResource("/" + getClass().getName().replace('.', '/') + ".class");
                return resource == null ? "unavailable" : resource.toString();
            } catch (RuntimeException | LinkageError problem) {
                return "unavailable:" + problem.getClass().getSimpleName();
            }
        }

        private String modArtifact(String modId) {
            try {
                var file = net.neoforged.fml.ModList.get().getModFileById(modId);
                return file == null ? "missing" : file.getFile().getFilePath().getFileName().toString();
            } catch (RuntimeException | LinkageError problem) {
                return "unavailable:" + problem.getClass().getSimpleName();
            }
        }

        private String classSha256() {
            try (var stream = getClass().getResourceAsStream("/" + getClass().getName().replace('.', '/') + ".class")) {
                if (stream == null) {
                    return "unavailable";
                }
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
            } catch (Exception | LinkageError problem) {
                return "unavailable:" + problem.getClass().getSimpleName();
            }
        }

        private void validate() {
            require(finished && failure == null && startNs != 0, "quiet_not_complete");
            require(selectedRunIds().contains(runId) && !visualDiagnostic(), "quiet_not_formal_run");
            require(visualDiagnostic() || variant == Variant.selected(), "quiet_selected_mismatch");
            require(PhotonRuntimeFactory.mode() == PhotonRuntimeFactory.Mode.HYBRID, "quiet_mode_changed");
            require(IrisCompat.isUsingShaderPack() == expectedShader, "quiet_shader_changed");
            require(ModList.get().isLoaded("sodium"), "quiet_sodium_missing");
            long dispatchObservedNs = variant.target == Target.BLANK ? spawnCreatedNs : spawnAddedNs;
            require(dispatchObservedNs >= spawnTargetNs()
                            && dispatchObservedNs - spawnTargetNs() <= MAX_DISPATCH_DELAY_NS,
                    "quiet_spawn_delay");
            if (variant.target != Target.BLANK) {
                require(clientSeenNs != 0 && clientLastSeenNs >= clientSeenNs && clientLostNs == 0
                        && aliveBeforeCleanup, "quiet_target_not_visible");
                require(maxTargetCount == 1, "quiet_target_count_mismatch");
                require(targetSubmitCount > 0, "quiet_target_not_submitted");
            } else {
                require(maxTargetCount == 0, "quiet_blank_target_present");
            }
            require(cleanupDone() && cleanupRequestedNs >= deadlineNs(), "quiet_cleanup_not_confirmed");
            require(cleanupRequestedNs < stopObservedNs, "quiet_cleanup_after_stop_observation");
            require(frames != null && label.equals(frames.get("label").getAsString())
                    && frames.get("started_ns").getAsLong() == startNs
                    && "DURATION".equals(frames.get("state").getAsString()), "quiet_frame_window");
            require(frames.get("frame_count").getAsInt() > 0 && frames.get("error").isJsonNull(),
                    "quiet_frame_error");
            require(frames.get("gpu_completed").getAsInt() > 0 && frames.get("gpu_errors").getAsInt() == 0,
                    "quiet_gpu_unavailable");
            var sparkState = spark == null ? new JsonObject() : spark.snapshot();
            require(spark != null && "COMPLETE".equals(sparkState.get("state").getAsString())
                            && !sparkState.get("cleanup_unconfirmed").getAsBoolean()
                            && sparkState.get("profile_bytes").getAsLong() > 0
                            && !sparkState.get("profile_sha256").getAsString().isBlank(),
                    "quiet_spark_profile_unverified");
            require(!visualDiagnostic(), "quiet_visual_entered_performance_validation");
            require(premeasure != null && premeasure.frameCount() > 0, "quiet_premeasure_empty");
            require(snapshotAck != null && snapshotImage != null && Files.isRegularFile(snapshotImage),
                    "quiet_snapshot_missing");
            require(sparkStartedAckNs != 0 && startNs > sparkStartedAckNs, "quiet_t0_before_spark_ack");
            require(measuredSubmissions != null
                    && measuredSubmissions.get("scope_protocol_errors").getAsLong() == 0,
                    "quiet_submission_scope");
            require(camera.samples() > 0 && camera.unavailableFrames() == 0, "quiet_camera_invalid");
            require(camera.changes() == 0, "quiet_camera_changed");
            if (variant.laser) {
                require(QueenOwnedLaserEffects.enabled(), "quiet_owned_laser_disabled");
                require(completedCasts == 3 && serverQuiescent && serverMaxActiveLaser == 1,
                        "quiet_laser_casts");
                require(allTailsFinished, "quiet_tail_not_finished");
                var stats = QueenOwnedLaserEffects.statistics();
                require(stats.started() - ownedBefore.started() == 3, "quiet_owned_started");
                require(stats.fallbacks() == ownedBefore.fallbacks() && stats.errors() == ownedBefore.errors()
                        && stats.overlaps() == ownedBefore.overlaps(), "quiet_owned_errors");
                require(stats.components().stream().filter(value -> value.ordinal() > ownedBefore.started())
                        .count() == 3, "quiet_component_history");
                var created = stats.components().stream().filter(value -> value.ordinal() > ownedBefore.started())
                        .allMatch(value -> value.created().containsAll(PhotonOwnedLaser.COMPONENTS)
                                && value.activeObserved().containsAll(PhotonOwnedLaser.COMPONENTS));
                require(created, "quiet_fifteen_components");
            } else {
                var stats = QueenOwnedLaserEffects.statistics();
                require(stats.started() == ownedBefore.started() && stats.fallbacks() == ownedBefore.fallbacks(),
                        "quiet_unexpected_laser");
            }
            require(castScheduleComplete(), "quiet_cast_schedule");
        }

        private void validateVisual() {
            require(visualDiagnostic(), "quiet_visual_not_selected");
            require(finished && failure == null && startNs != 0, "quiet_visual_not_complete");
            require(completedCasts == 3 && serverQuiescent && serverMaxActiveLaser == 1,
                    "quiet_visual_casts");
            require(allTailsFinished, "quiet_visual_tail");
            require(targetSubmitCount > 0, "quiet_visual_target_not_submitted");
            require(snapshotAck != null && snapshotImage != null && Files.isRegularFile(snapshotImage),
                    "quiet_visual_snapshot_missing");
            require(visualSnapshotAck != null && visualSnapshotImage != null && Files.isRegularFile(visualSnapshotImage),
                    "quiet_visual_active_snapshot_missing");
            require(visualSnapshotObservedNs > 0, "quiet_visual_active_snapshot_not_observed");
            require(visualSnapshotRequestedNs > 0 && visualSnapshotObservedNs > visualSnapshotRequestedNs,
                    "quiet_visual_snapshot_before_request");
            var stats = QueenOwnedLaserEffects.statistics();
            require(stats.started() - ownedBefore.started() == 3, "quiet_visual_owned_started");
            var components = stats.components().stream().filter(value -> value.ordinal() > ownedBefore.started())
                    .toList();
            require(components.size() == 3, "quiet_visual_component_history");
            require(components.stream().allMatch(value -> value.created().containsAll(PhotonOwnedLaser.COMPONENTS)
                    && value.activeObserved().containsAll(PhotonOwnedLaser.COMPONENTS)),
                    "quiet_visual_fifteen_components");
        }

        private boolean castScheduleComplete() {
            if (!variant.laser) {
                return true;
            }
            var expected = new long[]{10, 20, 30};
            for (var row : casts) {
                if (!row.isJsonObject()) {
                    return false;
                }
                var item = row.getAsJsonObject();
                long target = item.get("target_ns").getAsLong();
                int ordinal = item.get("ordinal").getAsInt();
                if (target - startNs != TimeUnit.SECONDS.toNanos(expected[ordinal - 1])) {
                    return false;
                }
                if (item.get("started_ns").getAsLong() - target > MAX_DISPATCH_DELAY_NS) {
                    return false;
                }
            }
            return casts.size() == 3;
        }

        private void fail(Throwable problem) {
            if (failure == null) {
                failure = problem;
            }
        }

        private void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalStateException(message);
            }
        }

        private void archiveFailure(BenchClientContext context) {
            if (archived) {
                return;
            }
            try {
                if (measuredSubmissions == null && submissionBound) {
                    measuredSubmissions = PhotonSubmissionDiagnostics.endCase();
                }
                var result = report();
                result.addProperty("archive_failure", true);
                context.artifacts().write(label + "_failure.json", "application/json", result.toString());
                if (frames != null) {
                    try {
                        archiveFrames(context);
                    } catch (Exception problem) {
                        rawArchiveError = problem.toString();
                    }
                }
                if (premeasure != null && snapshotAck != null && snapshotImage != null
                        && Files.isRegularFile(snapshotImage)) {
                    try {
                        archivePreMeasure(context);
                    } catch (Exception problem) {
                        rawArchiveError = rawArchiveError == null ? problem.toString()
                                : rawArchiveError + "; " + problem;
                    }
                }
                if (spark != null) {
                    try {
                        var state = spark.snapshot();
                        if (state.get("profile_bytes").getAsLong() > 0
                                && !state.get("profile_sha256").getAsString().isBlank()) {
                            spark.archive(context);
                        }
                    } catch (Exception | LinkageError problem) {
                        rawArchiveError = rawArchiveError == null ? problem.toString()
                                : rawArchiveError + "; " + problem;
                    }
                }
            } catch (Exception | LinkageError problem) {
                rawArchiveError = problem.toString();
            }
            archived = true;
        }

        private void teardown(BenchClientContext context) {
            try {
                if (captureStarted) {
                    PhotonPerformanceCapture.cancel();
                }
            } catch (RuntimeException | LinkageError problem) {
                rawArchiveError = rawArchiveError == null ? problem.toString() : rawArchiveError + "; " + problem;
            } finally {
                try {
                    if (submissionBound && measuredSubmissions == null) {
                        measuredSubmissions = PhotonSubmissionDiagnostics.endCase();
                    }
                } catch (RuntimeException | LinkageError problem) {
                    rawArchiveError = rawArchiveError == null ? problem.toString() : rawArchiveError + "; " + problem;
                } finally {
                    PhotonBenchConditions.end();
                    if (scheduler != null) {
                        scheduler.shutdownNow();
                    }
                    closeSpark();
                }
            }
        }

        private void closeSpark() {
            if (spark == null) {
                return;
            }
            try {
                spark.close();
            } catch (RuntimeException | LinkageError problem) {
                rawArchiveError = rawArchiveError == null ? problem.toString()
                        : rawArchiveError + "; " + problem;
            }
        }
    }
}
