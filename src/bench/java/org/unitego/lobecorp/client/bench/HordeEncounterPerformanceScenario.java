package org.unitego.lobecorp.client.bench;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import com.zhongbai233.bench.api.neoforge.client.BenchClientScenario;
import com.zhongbai233.bench.api.neoforge.client.BenchClientStepResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import jdk.jfr.FlightRecorder;
import jdk.jfr.RecordingState;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.api.encounter.Encounters;
import org.unitego.lobecorp.bench.plugin.BenchMixinPlugin;
import org.unitego.lobecorp.api.horde.Hordes;
import org.unitego.lobecorp.client.horde.HordeClient;
import org.unitego.lobecorp.client.development.DevelopmentWindowPolicy;
import org.unitego.lobecorp.client.horde.HordeRenderMetrics;
import org.unitego.lobecorp.client.horde.HordeRenderPolicy;
import org.unitego.lobecorp.client.horde.HordeGpuRenderer;
import org.unitego.lobecorp.client.horde.HordeIrisShadows;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.config.HordePathVariant;
import org.unitego.lobecorp.world.encounter.midnightcleanup.MidnightCleanupEncounter;
import org.unitego.lobecorp.world.horde.HordeStartRequest;
import org.unitego.lobecorp.world.horde.HordeStartResult;
import org.unitego.lobecorp.world.horde.HordeStopMode;

/// 正式遭遇性能采集；setup 即提交真实模板，禁止视觉假夹具和主动截图。
public class HordeEncounterPerformanceScenario implements BenchClientScenario {
    public static final String ID = "lobecorp.horde-encounter-performance";
    public static final Duration TIMEOUT = Duration.ofMinutes(10);
    private static final int SETTLE_TICKS = 100;
    private static final int MEASURE_TICKS = 400;
    private static final long SPARK_TIMEOUT_NS = TimeUnit.SECONDS.toNanos(30);
    private final JsonArray samples = new JsonArray();
    private CompletableFuture<HordeStartResult> start;
    private long failures;
    private long lastFrame = -1;
    private int warmupTicks;
    private volatile long eventStartTick;
    private volatile int measureStartTick;
    private long logOffset;
    private final Path log = Path.of("logs/latest.log");
    private int measureTicks;
    private long nonemptySamples;
    private boolean stageOwned;
    private boolean sparkOwned;
    private final HordeEncounterSpark spark = new HordeEncounterSpark();
    private boolean stopped;
    private boolean verified;
    private UUID gpuToken;
    private boolean encounterRemoved;
    private long actualMeasureStartTick = -1;
    private long positiveStageFrames;
    private long measureStartNs;
    private long measureEndNs;
    private long captureEndNs;
    private long captureStartNs;
    private boolean collectionComplete;
    private final JsonObject setupGate = new JsonObject();

    @Override
    public void setup(BenchClientContext context) throws Exception {
        checkSetupGate(context);
        logOffset = Files.exists(log) ? Files.size(log) : 0;
        var server = context.minecraft().getSingleplayerServer();
        if (server == null || !HordeClient.events().isEmpty()) {
            throw new IllegalStateException("需要独立集成世界且无既有浪潮");
        }

        // 只固定本轮独立夹具的调试覆盖层，不发送键盘输入或修改玩家界面设置文件。
        var debugEntries = context.minecraft().debugEntries;
        setupGate.addProperty("debugOverlayBefore", debugEntries.isOverlayVisible());
        debugEntries.setOverlayVisible(false);
        setupGate.addProperty("debugOverlayAfter", debugEntries.isOverlayVisible());
        if (debugEntries.isOverlayVisible()) {
            throw new AssertionError("基准调试覆盖层未关闭");
        }

        captureStartNs = System.nanoTime();
        failures = HordeRenderMetrics.failureSequence();
        gpuToken = PhotonFrameCapture.start("horde_encounter_performance", PhotonFrameCapture.maxSeconds(), HordeRenderMetrics::worldGpuFrame);
        HordeRenderMetrics.start(context.resultDirectory().resolve("artifacts/stages/horde-encounter-stages.jsonl"),
                true, HordeRenderMetrics.GpuMode.FULL_STAGE_SUM);
        stageOwned = true;
        var center = context.player().position();
        double angle = Math.toRadians(context.player().getYRot());
        var offset = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(MidnightCleanupEncounter.TEMPLATE_LENGTH / 2);
        var dimension = context.level().dimension();
        start = CompletableFuture.supplyAsync(() -> {
            var profile = Hordes.profile(MidnightCleanupEncounter.ID);
            if (profile == null) {
                throw new IllegalStateException("深夜清扫模板未注册");
            }

            int travel = (int) Math.ceil(MidnightCleanupEncounter.TEMPLATE_LENGTH / profile.movementSpeed());
            int generation = MidnightCleanupEncounter.TEMPLATE_TICKS - travel;
            if (generation < 20) {
                throw new IllegalStateException("模板速度不满足三分钟契约");
            }

            var level = server.getLevel(dimension);
            eventStartTick = level.getGameTime();
            measureStartTick = MidnightCleanupEncounter.OPENING_TICKS + travel + SETTLE_TICKS;
            return Encounters.start(level, MidnightCleanupEncounter.ID,
                    new HordeStartRequest(center.subtract(offset), center.add(offset), List.of(),
                            generation, MidnightCleanupEncounter.TEMPLATE_WIDTH));
        }, server);
        spark.start(context);
        sparkOwned = true;
    }

    @Override
    public BenchClientStepResult stabilize(BenchClientContext context) {
        observe(context, HordeRenderMetrics.Phase.STABILIZE);
        if (!start.isDone()) {
            return BenchClientStepResult.CONTINUE;
        }

        requireStart();
        return BenchClientStepResult.COMPLETE;
    }

    @Override
    public BenchClientStepResult warmup(BenchClientContext context) {
        observe(context, HordeRenderMetrics.Phase.WARMUP);
        warmupTicks++;
        return eventTick(context) >= measureStartTick ? BenchClientStepResult.COMPLETE : BenchClientStepResult.CONTINUE;
    }

    @Override
    public BenchClientStepResult measure(BenchClientContext context) {
        requireStart();
        if (actualMeasureStartTick < 0) {
            actualMeasureStartTick = eventTick(context);
            measureStartNs = System.nanoTime();
            if (actualMeasureStartTick > measureStartTick + 2) {
                throw new AssertionError("错过固定事件测量窗");
            }
        }
        if (eventTick(context) < measureStartTick + MEASURE_TICKS) {
            observe(context, HordeRenderMetrics.Phase.MEASURE);
            measureTicks++;
            return BenchClientStepResult.CONTINUE;
        }

        measureEndNs = System.nanoTime();
        HordeRenderMetrics.phase(HordeRenderMetrics.Phase.VERIFY);
        return BenchClientStepResult.COMPLETE;
    }

    private void checkSetupGate(BenchClientContext context) throws Exception {
        String scenario = System.getProperty("modBench.scenarios", "");
        String variant = System.getProperty("lobecorp.horde.variant", "");
        setupGate.addProperty("effectiveScenario", scenario);
        setupGate.addProperty("explicitVariant", variant);
        setupGate.addProperty("effectiveVariant", HordePathVariant.current().name());
        var recordings = FlightRecorder.getFlightRecorder().getRecordings().stream()
                .filter(recording -> recording.getState() == RecordingState.RUNNING).toList();
        setupGate.addProperty("runningJfrRecordingCount", recordings.size());
        var names = new JsonArray();
        recordings.forEach(recording -> names.add(recording.getName()));
        setupGate.add("runningJfrRecordingNames", names);
        boolean variantMatches = false;
        if (!variant.isBlank()) {
            try {
                variantMatches = HordePathVariant.current() == HordePathVariant.valueOf(variant.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // 非法显式版本同样导致门禁失败，不能回退到打包默认值。
            }
        }
        boolean nativeRequired = DevelopmentWindowPolicy.enabled() && DevelopmentWindowPolicy.nativeMode();
        boolean nativeReady = !nativeRequired || DevelopmentWindowPolicy.nativeCreated(context.minecraft().getWindow().handle());
        setupGate.addProperty("nativeMaximizeRequired", nativeRequired);
        setupGate.addProperty("nativeMaximizeReady", nativeReady);
        setupGate.addProperty("benchWindowPreservationApplied", BenchMixinPlugin.preservedWindowApplied());
        boolean preservationRequired = Boolean.getBoolean("lobecorp.horde.bench.requireWindowPreservationApplied");
        setupGate.addProperty("benchWindowPreservationRequired", preservationRequired);
        boolean overlayGuardRequired = Boolean.getBoolean("lobecorp.horde.bench.requireDebugOverlayGuardApplied");
        setupGate.addProperty("debugOverlayGuardApplied", BenchMixinPlugin.controlledDebugOverlayApplied());
        boolean passed = ID.equals(scenario) && variantMatches && recordings.size() == 1 && nativeReady
                && (!preservationRequired || BenchMixinPlugin.preservedWindowApplied())
                && (!overlayGuardRequired || BenchMixinPlugin.controlledDebugOverlayApplied());
        setupGate.addProperty("passed", passed);
        // 仅输出允许的有效属性与运行态录制信息，不读取或打印完整JVM启动参数。
        var path = context.resultDirectory().resolve("artifacts/horde-encounter-setup-gate.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(setupGate));
        context.artifacts().register(path, "application/json");
        if (!passed) {
            throw new IllegalStateException("setup门禁失败：场景、显式版本或RUNNING JFR录制数不符合要求，见setup-gate制品");
        }
    }

    private long eventTick(BenchClientContext context) {
        return context.level().getGameTime() - eventStartTick;
    }

    private void requireStart() {
        var result = start.join();
        if (!result.started()) {
            throw new AssertionError("正式遭遇启动失败：" + result.message());
        }
    }

    private void observe(BenchClientContext context, HordeRenderMetrics.Phase phase) {
        HordeRenderMetrics.phase(phase);
        if (HordeRenderMetrics.failureSequence() != failures) {
            throw new AssertionError("本轮出现Horde渲染错误");
        }
        long frame = context.frames().sampleCount();
        if (frame == lastFrame) {
            return;
        }
        lastFrame = frame;
        var row = new JsonObject();
        row.addProperty("phase", phase.name());
        row.addProperty("frame", frame);
        row.addProperty("eventTick", eventTick(context));
        row.addProperty("nanoTime", System.nanoTime());
        boolean debugOverlay = context.minecraft().debugEntries.isOverlayVisible();
        row.addProperty("debugOverlay", debugOverlay);
        row.addProperty("debugOverlayBlockedRequests", BenchControlledDebugOverlay.blockedRequests());
        if (debugOverlay) {
            throw new AssertionError("基准期间调试覆盖层状态改变，不能与受控负载比较");
        }
        long instances = HordeClient.batches().stream().mapToLong(HordeClient.Batch::count).sum();
        row.addProperty("submittedInstances", instances);
        if (phase == HordeRenderMetrics.Phase.MEASURE && instances > 0) {
            nonemptySamples++;
        }
        long window = context.minecraft().getWindow().handle();
        row.addProperty("focused", GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) != 0);
        row.addProperty("iconified", GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) != 0);
        row.addProperty("maximized", GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_MAXIMIZED) != 0);
        row.addProperty("windowX", context.minecraft().getWindow().getX());
        row.addProperty("windowY", context.minecraft().getWindow().getY());
        row.addProperty("width", context.minecraft().getWindow().getWidth());
        row.addProperty("height", context.minecraft().getWindow().getHeight());
        samples.add(row);
    }

    @Override
    public void verify(BenchClientContext context) throws Exception {
        if (!Files.exists(log) || Files.size(log) < logOffset) {
            throw new AssertionError("本轮日志缺失或被轮换");
        }
        try (var input = Files.newInputStream(log)) {
            input.skipNBytes(logOffset);
            var text = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (text.lines().anyMatch(line -> line.contains("/ERROR]") || line.contains("/FATAL]"))) {
                throw new AssertionError("本轮新增错误日志，包含动画错误时同样判fail");
            }
        }
        if (nonemptySamples == 0 || HordeRenderMetrics.failureSequence() != failures) {
            throw new AssertionError("workloadCorrect失败：空负载或渲染错误");
        }
        if (HordePathVariant.layered() && HordeVisualConfig.LAYER_COUNT.get() > 0 && !HordeGpuRenderer.layerSupported()) {
            throw new AssertionError("分层剔除未受支持：" + HordeGpuRenderer.layerStatus());
        }
        verified = true;
        writeReport(context, false);
    }

    private void writeReport(BenchClientContext context, boolean correct) throws Exception {
        var report = new JsonObject();
        report.addProperty("schemaVersion", 1);
        report.addProperty("variantSchemaVersion", 2);
        report.addProperty("variantMode", HordePathVariant.current().name().toLowerCase(Locale.ROOT));
        var flags = new JsonObject();
        flags.addProperty("motionKernel", HordeRenderPolicy.motionKernel());
        flags.addProperty("avoidanceKernel", HordePathVariant.avoidanceKernel());
        flags.addProperty("irisKernel", HordePathVariant.irisKernel());
        flags.addProperty("facingCache", HordePathVariant.facingCache());
        flags.addProperty("facingKernel", HordePathVariant.facingKernel());
        flags.addProperty("sceneOcclusion", HordePathVariant.sceneOcclusion());
        flags.addProperty("selectedScatter", HordeRenderPolicy.selectedScatter());
        flags.addProperty("depthScissor", HordeRenderPolicy.depthScissor());
        flags.addProperty("detailLod", HordePathVariant.detailLod());
        flags.addProperty("reduceHiddenFaces", HordeVisualConfig.REDUCE_HIDDEN_FACES.get() && HordePathVariant.reduceHiddenFaces());
        boolean shaderActive = HordeIrisShadows.active();
        flags.addProperty("shaderActive", shaderActive);
        flags.addProperty("renderPath", HordeRenderPolicy.settings().shaderPackActive() ? "iris" : "native");
        flags.addProperty("policyShaderPackActive", HordeRenderPolicy.settings().shaderPackActive());
        flags.addProperty("layered", HordePathVariant.layered());
        flags.addProperty("layerCount", HordeVisualConfig.LAYER_COUNT.get());
        flags.addProperty("layerSupported", HordeGpuRenderer.layerSupported());
        flags.addProperty("layerStatus", HordeGpuRenderer.layerStatus());
        flags.addProperty("layerVisibleCount", HordeGpuRenderer.layerVisibleCount());
        report.add("effectiveFlags", flags);
        report.addProperty("testConditionsVersion", 3);
        report.addProperty("debugOverlayPolicy", "vanilla_API_hidden_at_setup; Bench_only_open_request_guard; assert_unchanged_in_observe; no_OS_input");
        report.add("setupGate", setupGate);
        report.addProperty("scenarioId", ID);
        report.addProperty("workloadCorrect", correct);
        report.addProperty("encounterRemoved", encounterRemoved);
        var cleanup = new JsonObject();
        cleanup.addProperty("encounterRemoved", encounterRemoved);
        report.add("cleanup", cleanup);
        report.addProperty("measureStartNs", measureStartNs);
        report.addProperty("measureEndNs", measureEndNs);
        report.addProperty("captureEndNs", captureEndNs);
        report.addProperty("captureStartNs", captureStartNs);
        report.addProperty("collectionComplete", collectionComplete);
        report.addProperty("gpuScope", "earliest_setup_render_context_to_teardown_stop; pre_context_JVM_start_not_available");
        report.addProperty("gpuMaxSeconds", PhotonFrameCapture.maxSeconds());
        report.addProperty("frameTimestampContract", "gpu.summary.started_ns + gpu.frames.head_offset_ns; interval_ns is actual render HEAD interval; use both endpoints inside measureStartNs/measureEndNs");
        report.addProperty("sparkScope", "setup_through_teardown_stop_includes_all_phases");
        report.addProperty("sparkLocalPath", HordeEncounterSpark.LOCAL_PATH);
        report.addProperty("sparkSaved", spark.saved());
        report.addProperty("sparkLocalValidated", spark.saved());
        report.addProperty("stageMetricsSource", "artifacts/stages/horde-encounter-stages.jsonl");
        report.addProperty("serverUnitsScope", "not_collected_no_mob_or_render_count_substitution");
        report.addProperty("eventId", start != null && start.isDone() && !start.isCompletedExceptionally()
                ? String.valueOf(start.join().eventId()) : "unavailable");
        report.addProperty("width", MidnightCleanupEncounter.TEMPLATE_WIDTH);
        report.addProperty("length", MidnightCleanupEncounter.TEMPLATE_LENGTH);
        report.addProperty("durationTicks", MidnightCleanupEncounter.TEMPLATE_TICKS);
        report.addProperty("nonemptyMeasureSamples", nonemptySamples);
        report.addProperty("positiveStageFrames", positiveStageFrames);
        report.addProperty("actualMeasureStartEventTick", actualMeasureStartTick);
        report.addProperty("warmupTicks", warmupTicks);
        report.addProperty("measureStartEventTick", measureStartTick);
        report.addProperty("measureEndEventTick", measureStartTick + MEASURE_TICKS);
        report.addProperty("model", new MidnightCleanupEncounter().visuals().model().toString());
        report.addProperty("measureTicks", measureTicks);
        report.add("samples", samples);
        Path path = context.resultDirectory().resolve("artifacts/horde-encounter-performance.json");
        Files.createDirectories(path.getParent());
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        context.artifacts().register(path, "application/json");
    }

    @Override
    public void teardown(BenchClientContext context) throws Exception {
        if (stopped) {
            return;
        }
        stopped = true;
        try {
            HordeRenderMetrics.phase(HordeRenderMetrics.Phase.TEARDOWN);
            captureEndNs = System.nanoTime();
            if (sparkOwned) {
                spark.stop(context);
                sparkOwned = false;
            }
            if (gpuToken != null) {
                boolean completeCapture = PhotonFrameCapture.active();
                PhotonFrameCapture.stop();
                var capture = PhotonFrameCapture.snapshot();
                if (!completeCapture || !capture.has("capture_id")
                        || !gpuToken.toString().equals(capture.get("capture_id").getAsString())
                        || !capture.has("saved_path") || capture.get("saved_path").isJsonNull()) {
                    throw new AssertionError("GPU全程捕获提前结束或未保存，时长/容量上限不能视为完整");
                }
                var gpu = context.resultDirectory().resolve("artifacts/gpu/horde-encounter-gpu.json");
                Files.createDirectories(gpu.getParent());
                Files.copy(Path.of(capture.get("saved_path").getAsString()), gpu, StandardCopyOption.REPLACE_EXISTING);
                context.artifacts().register(gpu, "application/json");
            }
            var stages = HordeRenderMetrics.finish();
            stageOwned = false;
            context.artifacts().register(stages, "application/x-ndjson");
            try (var lines = Files.lines(stages)) {
                var header = JsonParser.parseString(lines.findFirst().orElseThrow()).getAsJsonObject();
                if (header.get("frames").getAsLong() == 0 || header.get("gpu_completed").getAsLong() == 0
                        || header.get("gpu_errors").getAsLong() != 0) {
                    throw new AssertionError("阶段GPU采集为空或存在错误");
                }
            }
            try (var lines = Files.lines(stages)) {
                positiveStageFrames = lines.skip(1).map(JsonParser::parseString).map(element -> element.getAsJsonObject())
                        .filter(row -> row.get("phase").getAsString().equals("MEASURE"))
                        .filter(row -> row.getAsJsonObject("counts").get("instances").getAsLong() > 0).count();
            }
            if (positiveStageFrames == 0) {
                throw new AssertionError("稳定测量窗实际绘制实例为空");
            }
            long deadline = System.nanoTime() + SPARK_TIMEOUT_NS;
            // 处理客户端队列中的网络/聊天任务，避免阻塞Spark回执投递。
            context.minecraft().managedBlock(() -> {
                try {
                    spark.capture(context);
                } catch (java.io.IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
                return spark.saved() || System.nanoTime() >= deadline;
            });
            spark.requireSaved();
            collectionComplete = true;
            writeReport(context, verified);
        } finally {
            if (stageOwned) {
                stageOwned = false;
                var path = HordeRenderMetrics.finish();
                context.artifacts().register(path, "application/x-ndjson");
            }
            if (start != null) {
                var server = context.minecraft().getSingleplayerServer();
                start.thenAcceptAsync(result -> {
                    if (result.started()) {
                        var stopped = Encounters.stop(server, result.eventId(), HordeStopMode.CLEAR);
                        encounterRemoved = stopped.stopped() && Encounters.active(server).stream()
                                .noneMatch(instance -> instance.id().equals(result.eventId()));
                    } else {
                        encounterRemoved = true;
                    }
                }, server).get(30, TimeUnit.SECONDS);
                writeReport(context, verified && collectionComplete && encounterRemoved && spark.saved() && positiveStageFrames > 0);
                if (!encounterRemoved) {
                    throw new AssertionError("本次遭遇未清理完成");
                }
            }
        }
    }
}
