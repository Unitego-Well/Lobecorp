package org.unitego.lobecorp.client.horde;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuQuery;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.TimerQuery;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;

/// 显式开启的渲染周期统计；GPU轮换采样或分帧逐调用求和，不嵌套查询或等待结果。
public class HordeRenderMetrics {
    public enum Stage {
        PREPARE(false), LIGHT(false), REGIONS(true), TERRAIN(true), SPATIAL(true), IRIS_PREPARE(true), UNITS(true), UNIT_STATS(true), MOTION(true), EXPAND(true), COPY(true), BASE(true), GLOW(true);
        private final boolean gpu;

        Stage(boolean gpu) {
            this.gpu = gpu;
        }
    }

    public enum Counter {
        INSTANCES, LOGICAL_BATCHES, GROUPS, PASSES, DRAWS, COPIES, COPY_BYTES, UPLOAD_BYTES,
        EXPANDED_VERTICES, REGIONS, LIGHT_SECTIONS, LIGHT_SLOTS, LIGHT_COVERAGE_BUILDS,
		LIGHT_COLUMN_QUERIES, SPATIAL_WORDS, SPATIAL_LEVELS, IRIS_FRAME_WORDS, PARTICLE_AREAS, PARTICLES, FAILURES, DEGRADED, LOD_NEAR_BLOCKS, LOD_MID_BLOCKS, LOD_FAR_BLOCKS, LOD_NEAR_INSTANCES, LOD_MID_INSTANCES, LOD_FAR_INSTANCES, HISTORY_COPIES, HISTORY_COPY_BYTES, HISTORY_INSTANCES,
        PENETRATION_MAX, PENETRATION_REJECTED
    }

    public enum Phase {SETUP, STABILIZE, WARMUP, MEASURE, VERIFY, TEARDOWN}

    public enum GpuMode {ROTATING_SAMPLE, FULL_STAGE_SUM}

    private enum Incomplete {
        OUTER_QUERY_BUSY, PENDING_BUDGET, FRAME_QUERY_BUDGET, NESTED_STAGE, QUERY_ERROR, CANCELLED, MISSING_END, RENDER_FRAME_MISMATCH, DRAW_FAILED
    }

    private static final int MAX_FRAMES = 65536;
    private static final int MAX_PENDING_QUERIES = 128;
    private static final int MAX_FULL_PENDING_QUERIES = 1024;
    private static final int MAX_FULL_QUERIES_PER_FRAME = 256;
    private static final int MAX_READY_SUMS = 1024;
    private static final int GPU_COHORTS = 3;
    private static final int TOTAL_COHORT = 1;
    private static final int WORLD_COHORT = 2;
    private static final Stage[] GPU_STAGES = {Stage.REGIONS, Stage.TERRAIN, Stage.SPATIAL, Stage.IRIS_PREPARE, Stage.UNITS, Stage.UNIT_STATS, Stage.MOTION, Stage.EXPAND, Stage.COPY, Stage.BASE, Stage.GLOW};
    private static final Stage[] STAGES = Stage.values();
    private static final Counter[] COUNTERS = Counter.values();
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final long[] COUNTS = new long[COUNTERS.length];
    private static final long[] CPU = new long[STAGES.length];
    private static final int[] CALLS = new int[STAGES.length];
    private static final ArrayDeque<StageSum> READY_SUMS = new ArrayDeque<>();
    private static @Nullable Capture capture;
    private static @Nullable Pending recording;
    private static long lastExtractNs = -1;
    private static int lastExtractBatches;
    private static int row = -1;
    private static boolean attempted;
    private static long recordingStarted;
    // 生命周期累计值不随帧或捕获重置，用于发现两个逻辑刻之间的瞬时绘制失败。
    private static long failureSequence;

    public static void start(Path destination) {
        start(destination, true);
    }

    public static void start(Path destination, boolean gpu) {
        start(destination, gpu, GpuMode.ROTATING_SAMPLE);
    }

    public static void start(Path destination, boolean gpu, GpuMode mode) {
        if (capture != null) {
            throw new IllegalStateException("已有群体阶段统计正在记录");
        }
        READY_SUMS.clear();
        capture = new Capture(destination, gpu, mode);
        lastExtractNs = -1;
        lastExtractBatches = 0;
    }

    /// 共享外层render标识，零次或多次Horde draw不会改变世界查询所属组。
    public static boolean worldGpuFrame() {
        var current = capture;
        return current == null || !current.gpu || current.size >= MAX_FRAMES
                || current.mode == GpuMode.ROTATING_SAMPLE || !PhotonFrameCapture.renderFrameOpen()
                || frameCohort(current, PhotonFrameCapture.renderFrameId()) == WORLD_COHORT;
    }

    public static boolean collecting() {
        return capture != null && capture.gpu;
    }

    public static void phase(Phase phase) {
        if (capture != null) {
            capture.phase = phase;
        }
    }

    public static long extractStarted() {
        return capture == null ? 0 : System.nanoTime();
    }

    public static void extracted(long started, int batches) {
        lastExtractNs = started == 0 ? -1 : System.nanoTime() - started;
        lastExtractBatches = batches;
    }

    public static void beginFrame(boolean iris) {
        var previous = recording;
        if (previous != null && capture != null) {
            incomplete(capture, previous.row, previous.stage, Incomplete.MISSING_END);
            endRecording(capture, previous);
        }
        Arrays.fill(COUNTS, 0);
        COUNTS[Counter.PENETRATION_MAX.ordinal()] = -1;
        COUNTS[Counter.PENETRATION_REJECTED.ordinal()] = -1;
        Arrays.fill(CPU, 0);
        Arrays.fill(CALLS, 0);
        attempted = false;
        row = -1;
        var current = capture;
        if (current == null) {
            return;
        }
        poll(current, false);
        if (current.size < MAX_FRAMES) {
            row = current.size++;
            current.times[row] = System.nanoTime();
            current.phases[row] = (byte) current.phase.ordinal();
            current.iris[row] = iris;
            current.extractNs[row] = lastExtractNs;
            current.extractBatches[row] = lastExtractBatches;
            current.frameBudget[row] = current.pending.size() <= MAX_FULL_PENDING_QUERIES - MAX_FULL_QUERIES_PER_FRAME;
            long frameId = PhotonFrameCapture.renderFrameId();
            current.renderFrames[row] = frameId;
            current.sampleStages[row] = current.mode == GpuMode.ROTATING_SAMPLE ? row % GPU_STAGES.length
                    : stageFrame(current, row) ? current.stageCohorts++ % GPU_STAGES.length : -1;
            current.validFrame[row] = current.mode != GpuMode.FULL_STAGE_SUM || !current.gpu
                    || PhotonFrameCapture.renderFrameOpen();
            if (current.mode == GpuMode.FULL_STAGE_SUM && current.gpu) {
                if (!current.validFrame[row]) {
                    invalidateFrame(current, row);
                }
                if (current.lastRenderFrame == frameId) {
                    for (int invalid = current.firstRowInFrame; invalid <= row; invalid++) {
                        invalidateFrame(current, invalid);
                    }
                    int firstInvalid = current.firstRowInFrame;
                    READY_SUMS.removeIf(sum -> sum.row >= firstInvalid && sum.row <= row);
                    current.duplicateDraws++;
                } else {
                    if (current.lastRenderFrame != Long.MIN_VALUE && frameId > current.lastRenderFrame + 1) {
                        current.missingDrawFrames += frameId - current.lastRenderFrame - 1;
                    }
                    current.firstRowInFrame = row;
                }
                current.lastRenderFrame = frameId;
                beginDrawTotal(current);
            }
        }
    }

    public static long begin(Stage stage) {
        return begin(stage, 0);
    }

    public static long begin(Stage stage, long workUnits) {
        var current = capture;
        if (current == null || row < 0 || current.ended[row]) {
            return 0;
        }
        boolean queryStarted = false;
        if (stage.gpu) {
            int call = ++current.expected[stage.ordinal()][row];
            boolean sample = !attempted && current.sampleStages[row] >= 0 && GPU_STAGES[current.sampleStages[row]] == stage;
            boolean requested = current.gpu && (current.mode == GpuMode.ROTATING_SAMPLE ? sample : stageFrame(current, row) && current.validFrame[row]);
            if (requested) {
                attempted |= sample;
                if (TimerQuery.getInstance().isRecording()) {
                    current.busySkipped++;
                    incomplete(current, row, stage, Incomplete.OUTER_QUERY_BUSY);
                } else if (recording != null) {
                    current.busySkipped++;
                    incomplete(current, row, stage, Incomplete.NESTED_STAGE);
                } else if (current.mode == GpuMode.FULL_STAGE_SUM && !current.frameBudget[row]
                        || current.pending.size() >= pendingLimit(current)) {
                    current.pendingSkipped++;
                    incomplete(current, row, stage, Incomplete.PENDING_BUDGET);
                } else if (current.mode == GpuMode.FULL_STAGE_SUM
                        && current.frameQueries[row] >= MAX_FULL_QUERIES_PER_FRAME) {
                    current.pendingSkipped++;
                    incomplete(current, row, stage, Incomplete.FRAME_QUERY_BUDGET);
                } else {
                    try {
                        var encoder = RenderSystem.getDevice().createCommandEncoder();
                        var query = encoder.timerQueryBegin();
                        recording = new Pending(query, encoder, row, stage, call, workUnits, sample);
                        queryStarted = true;
                        current.queryStarted[stage.ordinal()][row]++;
                        current.frameQueries[row]++;
                        current.started++;
                    } catch (RuntimeException exception) {
                        current.errors++;
                        incomplete(current, row, stage, Incomplete.QUERY_ERROR);
                    }
                }
            }
        }
        long started = System.nanoTime();
        if (queryStarted) {
            recordingStarted = started;
        }
        return started;
    }

    public static void end(Stage stage, long started) {
        var current = capture;
        if (started == 0 || current == null || row < 0 || current.ended[row]) {
            return;
        }
        CPU[stage.ordinal()] += System.nanoTime() - started;
        CALLS[stage.ordinal()]++;
        var active = recording;
        if (active != null && active.stage == stage && active.row == row && recordingStarted == started) {
            endRecording(current, active);
        }
    }

    private static boolean stageFrame(Capture current, int index) {
        return current.mode == GpuMode.FULL_STAGE_SUM && frameCohort(current, current.renderFrames[index]) == 0;
    }

    private static int frameCohort(Capture current, long frameId) {
        return (int) Math.floorMod(frameId - current.firstRenderFrame, (long) GPU_COHORTS);
    }

    private static String cohortName(Capture current, int index) {
        if (current.mode == GpuMode.ROTATING_SAMPLE) {
            return "ROTATING_SAMPLE";
        }
        return switch (frameCohort(current, current.renderFrames[index])) {
            case TOTAL_COHORT -> "HORDE_DRAW_TOTAL";
            case WORLD_COHORT -> "WORLD";
            default -> "STAGE_SUM";
        };
    }

    private static void invalidateFrame(Capture current, int index) {
        current.validFrame[index] = false;
        incomplete(current, index, null, Incomplete.RENDER_FRAME_MISMATCH);
        for (var stage : GPU_STAGES) {
            incomplete(current, index, stage, Incomplete.RENDER_FRAME_MISMATCH);
        }
    }

    private static void beginDrawTotal(Capture current) {
        if (!current.gpu || current.mode != GpuMode.FULL_STAGE_SUM
                || frameCohort(current, current.renderFrames[row]) != TOTAL_COHORT) {
            return;
        }
        if (!current.validFrame[row]) {
            return;
        }
        if (TimerQuery.getInstance().isRecording()) {
            current.busySkipped++;
            incomplete(current, row, null, Incomplete.OUTER_QUERY_BUSY);
            return;
        }
        if (recording != null || current.pending.size() >= pendingLimit(current)) {
            current.pendingSkipped++;
            incomplete(current, row, null, Incomplete.PENDING_BUDGET);
            return;
        }
        try {
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            recording = new Pending(encoder.timerQueryBegin(), encoder, row, null, 1, 0, false);
            recordingStarted = System.nanoTime();
            current.drawStarted[row]++;
            current.started++;
        } catch (RuntimeException failure) {
            current.errors++;
            incomplete(current, row, null, Incomplete.QUERY_ERROR);
        }
    }

    private static boolean drawComplete(Capture current, int index) {
        return current.gpu && current.mode == GpuMode.FULL_STAGE_SUM
                && frameCohort(current, current.renderFrames[index]) == TOTAL_COHORT
                && current.validFrame[index] && current.ended[index]
                && current.drawIncomplete[index] == 0
                && current.drawStarted[index] == 1 && current.drawCompleted[index] == 1;
    }

    private static JsonObject drawCoverage(Capture current, int index) {
        boolean selected = current.mode == GpuMode.FULL_STAGE_SUM
                && frameCohort(current, current.renderFrames[index]) == TOTAL_COHORT;
        var value = new JsonObject();
        value.addProperty("expected_calls", selected ? 1 : 0);
        value.addProperty("started_calls", current.drawStarted[index]);
        value.addProperty("completed_calls", current.drawCompleted[index]);
        value.addProperty("complete", drawComplete(current, index));
        value.addProperty("state", !current.gpu ? "DISABLED" : !selected ? "NOT_TOTAL_COHORT"
                : drawComplete(current, index) ? "COMPLETE" : "INCOMPLETE");
        var reasons = new JsonArray();
        for (var reason : Incomplete.values()) {
            if ((current.drawIncomplete[index] & 1 << reason.ordinal()) != 0) {
                reasons.add(reason.name().toLowerCase(Locale.ROOT));
            }
        }
        if (selected && !current.ended[index]) {
            reasons.add("frame_not_ended");
        }
        value.add("reasons", reasons);
        return value;
    }

    private static void queueDrawTotal(Capture current, int index) {
        if (current.phases[index] != Phase.MEASURE.ordinal() || current.drawQueued[index]
                || !drawComplete(current, index)) {
            return;
        }
        current.drawQueued[index] = true;
        if (READY_SUMS.size() >= MAX_READY_SUMS) {
            current.reportDropped++;
            return;
        }
        READY_SUMS.addLast(new StageSum(null, current.drawNs[index], index));
    }

    public static void drainDrawTotals(LongConsumer consumer) {
        var current = capture;
        if (current != null) {
            poll(current, false);
        }
        var iterator = READY_SUMS.iterator();
        while (iterator.hasNext()) {
            var sum = iterator.next();
            if (sum.stage == null) {
                consumer.accept(sum.nanoseconds);
                iterator.remove();
            }
        }
    }

    private static int pendingLimit(Capture current) {
        return current.mode == GpuMode.FULL_STAGE_SUM ? MAX_FULL_PENDING_QUERIES : MAX_PENDING_QUERIES;
    }

	private static void incomplete(Capture current, int index, @Nullable Stage stage, Incomplete reason) {
		if (stage == null) {
			current.drawIncomplete[index] |= 1 << reason.ordinal();
		} else {
			current.incomplete[stage.ordinal()][index] |= 1 << reason.ordinal();
		}
    }

    private static void endRecording(Capture current, Pending active) {
        recording = null;
        try {
            active.encoder.timerQueryEnd(active.query);
            current.pending.add(active);
        } catch (RuntimeException exception) {
            current.errors++;
            incomplete(current, active.row, active.stage, Incomplete.QUERY_ERROR);
            close(current, active);
        }
    }

    private static void close(Capture current, Pending pending) {
        try {
            pending.query.close();
        } catch (RuntimeException exception) {
            current.errors++;
            incomplete(current, pending.row, pending.stage, Incomplete.QUERY_ERROR);
        }
    }

    private static int constraintRow(Capture current, long frameId) {
        for (int index = current.size - 1; index >= 0 && current.renderFrames[index] >= frameId; index--) {
            if (current.renderFrames[index] == frameId && current.validFrame[index]) {
	            return index;
            }
        }
        return -1;
    }

    public static void expectConstraint(long frameId) {
        var current = capture;
        if (current == null) {
	        return;
        }
        int index = constraintRow(current, frameId);
        if (index >= 0) {
	        current.constraintExpected[index]++;
        }
    }

    public static void constraintResult(long frameId, double maximum, long rejected) {
        var current = capture;
        if (current == null) {
	        return;
        }
        int index = constraintRow(current, frameId);
        if (index < 0) {
	        return;
        }
        if (!Double.isFinite(maximum) || maximum < 0 || rejected < 0) {
            current.constraintErrors[index]++;
            return;
        }
        current.constraintCompleted[index]++;
        long depth = (long) Math.ceil(maximum * 1_000_000);
        int max = Counter.PENETRATION_MAX.ordinal(), count = Counter.PENETRATION_REJECTED.ordinal();
        if (!current.ended[index] && row == index) {
            COUNTS[max] = Math.max(COUNTS[max], depth);
            COUNTS[count] = Math.max(0, COUNTS[count]) + rejected;
        } else {
            current.counts[max][index] = Math.max(current.counts[max][index], depth);
            current.counts[count][index] = Math.max(0, current.counts[count][index]) + rejected;
        }
    }

    private static boolean constraintsComplete(Capture current, int index) {
        return current.constraintExpected[index] > 0
                && current.constraintExpected[index] == current.constraintCompleted[index]
                && current.constraintErrors[index] == 0;
    }

    public static void add(Counter counter, long value) {
        COUNTS[counter.ordinal()] += value;
        if (counter == Counter.FAILURES) {
            failureSequence += value;
        }
    }

	public static long failureSequence() {
		return failureSequence;
	}

    public static void set(Counter counter, long value) {
        COUNTS[counter.ordinal()] = value;
    }

    public static long value(Counter counter) {
        return COUNTS[counter.ordinal()];
    }

    public static long cpu(Stage stage) {
        return CPU[stage.ordinal()];
    }

    public static void endFrame() {
        var current = capture;
        if (current == null || row < 0) {
            return;
        }
        var active = recording;
        if (active != null) {
            if (active.stage == null) {
                endRecording(current, active);
            } else {
                incomplete(current, active.row, active.stage, Incomplete.MISSING_END);
                end(active.stage, recordingStarted);
                current.errors++;
            }
        }
        if (COUNTS[Counter.FAILURES.ordinal()] > 0) {
            for (var stage : GPU_STAGES) {
                incomplete(current, row, stage, Incomplete.DRAW_FAILED);
            }
            incomplete(current, row, null, Incomplete.DRAW_FAILED);
        }
        for (var stage : STAGES) {
            current.cpu[stage.ordinal()][row] = CPU[stage.ordinal()];
            current.calls[stage.ordinal()][row] = CALLS[stage.ordinal()];
        }
        for (var counter : COUNTERS) {
            current.counts[counter.ordinal()][row] = COUNTS[counter.ordinal()];
        }
        current.ended[row] = true;
        for (var stage : GPU_STAGES) {
            queueSum(current, row, stage);
        }
        queueDrawTotal(current, row);
    }

    private static void poll(Capture current, boolean finishing) {
        var iterator = current.pending.iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next();
            if (!finishing && current.size - pending.row < 2) {
                continue;
            }
            try {
                var result = pending.query.getValue();
                if (result.isEmpty()) {
                    if (!finishing) {
                        continue;
                    }
                    current.cancelled++;
                    incomplete(current, pending.row, pending.stage, Incomplete.CANCELLED);
                } else {
                    long nanoseconds = result.getAsLong();
                    if (nanoseconds < 0) {
                        throw new IllegalStateException("GPU查询返回负耗时");
                    }
                    if (pending.stage == null) {
                        current.drawNs[pending.row] = nanoseconds;
                        current.drawCompleted[pending.row]++;
                    } else {
                        int ordinal = pending.stage.ordinal();
                        current.gpuSums[ordinal][pending.row] = Math.addExact(current.gpuSums[ordinal][pending.row], nanoseconds);
                        current.queryCompleted[ordinal][pending.row]++;
                        if (pending.sample) {
                            current.gpuNs[ordinal][pending.row] = nanoseconds;
                            current.gpuCalls[pending.row] = pending.call;
                            current.gpuUnits[pending.row] = pending.units;
                        }
                    }
                    current.completed++;
                }
            } catch (RuntimeException exception) {
                current.errors++;
                incomplete(current, pending.row, pending.stage, Incomplete.QUERY_ERROR);
            }
            close(current, pending);
	        if (pending.stage == null) {
		        queueDrawTotal(current, pending.row);
	        } else {
		        queueSum(current, pending.row, pending.stage);
	        }
            iterator.remove();
        }
    }

    /// 非阻塞交付已经完整覆盖的测量阶段；finish之后仍可领取最后已解析的数据。
    public static void drainGpuSums(BiConsumer<Stage, Long> consumer) {
        var current = capture;
        if (current != null) {
            poll(current, false);
        }
        var iterator = READY_SUMS.iterator();
        while (iterator.hasNext()) {
            var sum = iterator.next();
            if (sum.stage != null) {
                consumer.accept(sum.stage, sum.nanoseconds);
                iterator.remove();
            }
        }
    }

    private static void queueSum(Capture current, int index, Stage stage) {
        int ordinal = stage.ordinal();
        if (current.phases[index] != Phase.MEASURE.ordinal() || current.queued[ordinal][index]
                || !complete(current, index, stage)) {
            return;
        }
        current.queued[ordinal][index] = true;
        if (READY_SUMS.size() >= MAX_READY_SUMS) {
            current.reportDropped++;
            return;
        }
        READY_SUMS.addLast(new StageSum(stage, current.gpuSums[ordinal][index], index));
    }

    private static boolean complete(Capture current, int index, Stage stage) {
        int ordinal = stage.ordinal();
        return current.gpu && stageFrame(current, index) && current.validFrame[index] && current.ended[index]
                && current.incomplete[ordinal][index] == 0
                && current.expected[ordinal][index] == current.queryStarted[ordinal][index]
                && current.expected[ordinal][index] == current.queryCompleted[ordinal][index];
    }

    private static JsonObject coverage(Capture current, int index, @Nullable Stage stage) {
        int expected = 0;
        int started = 0;
        int completed = 0;
        int reasons = 0;
        for (var candidate : GPU_STAGES) {
            if (stage != null && candidate != stage) {
                continue;
            }
            int ordinal = candidate.ordinal();
            expected += current.expected[ordinal][index];
            started += current.queryStarted[ordinal][index];
            completed += current.queryCompleted[ordinal][index];
            reasons |= current.incomplete[ordinal][index];
        }
        var value = new JsonObject();
        value.addProperty("expected_calls", expected);
        value.addProperty("started_calls", started);
        value.addProperty("completed_calls", completed);
        value.addProperty("unresolved_or_missing_calls", expected - completed);
        var missing = new JsonArray();
        for (var reason : Incomplete.values()) {
            if ((reasons & 1 << reason.ordinal()) != 0) {
                missing.add(reason.name().toLowerCase(Locale.ROOT));
            }
        }
        if (!current.ended[index]) {
            missing.add("frame_not_ended");
        }
        boolean full = current.gpu && stageFrame(current, index);
        boolean complete = full && current.validFrame[index] && current.ended[index] && reasons == 0 && expected == started && expected == completed;
        String state = !current.gpu ? "DISABLED"
                : current.mode == GpuMode.ROTATING_SAMPLE ? "ROTATING_SAMPLE"
                : !full ? cohortName(current, index) : complete ? "COMPLETE" : "INCOMPLETE";
        value.addProperty("state", state);
        value.addProperty("complete", complete);
        value.addProperty("incomplete", full && !complete);
        value.add("reasons", missing);
        return value;
    }

    public static Path finish() throws IOException {
        var current = capture;
        if (current == null) {
            throw new IllegalStateException("没有群体阶段统计");
        }
        var active = recording;
        if (active != null) {
            incomplete(current, active.row, active.stage, Incomplete.MISSING_END);
            if (active.stage == null) {
                endRecording(current, active);
            } else {
                end(active.stage, recordingStarted);
            }
        }
        capture = null;
        row = -1;
        poll(current, true);
        Files.createDirectories(current.destination.toAbsolutePath().getParent());
        try (var writer = Files.newBufferedWriter(current.destination, StandardCharsets.UTF_8)) {
            var header = new JsonObject();
            header.addProperty("schema", 3);
            header.addProperty("frames", current.size);
            header.addProperty("state", current.size == MAX_FRAMES ? "CAPACITY" : "STOPPED");
            header.addProperty("cpu_scope", "sum of CPU invocation scopes; parent scopes include children; own GPU query setup/teardown excluded");
            header.addProperty("gpu_mode", current.mode.name());
            header.addProperty("gpu_scope", current.mode == GpuMode.FULL_STAGE_SUM
                    ? "shared render-frame cohorts: STAGE_SUM, HORDE_DRAW_TOTAL, WORLD; non-nested elapsed queries"
                    : "one rotating stage invocation per draw cycle; not a whole-frame stage sum");
            header.addProperty("gpu_sum_scope", "GPU stages only; excludes world work outside Horde; CPU parent and child stages must not be added");
            header.addProperty("gpu_query_result", "OptionalLong present including zero is valid; unavailable, skipped and cancelled results remain unknown");
	        header.addProperty("gpu_query_error_scope", "query errors do not assert backend recovery; only this collector's own queries are ended or closed");
            header.addProperty("gpu_pending_limit", pendingLimit(current));
            header.addProperty("gpu_frame_query_limit", current.mode == GpuMode.FULL_STAGE_SUM ? MAX_FULL_QUERIES_PER_FRAME : 1);
            header.addProperty("extract_scope", "latest completed extract; may precede the draw cycle");
            header.addProperty("gpu_enabled", current.gpu);
            header.addProperty("gpu_started", current.started);
            header.addProperty("gpu_completed", current.completed);
            header.addProperty("gpu_skipped_busy", current.busySkipped);
            header.addProperty("gpu_skipped_pending", current.pendingSkipped);
            header.addProperty("gpu_cancelled", current.cancelled);
            header.addProperty("gpu_errors", current.errors);
            header.addProperty("gpu_report_dropped", current.reportDropped);
            header.addProperty("penetration_max_unit", "microblocks; divide by 1000000 for blocks");
            header.addProperty("constraint_scope", "GPU reduction, asynchronous scalar readback; partial/pending/error results are null");
            header.addProperty("gpu_duplicate_draws", current.duplicateDraws);
            header.addProperty("gpu_missing_draw_frames", current.missingDrawFrames);
            header.addProperty("horde_draw_scope", "Horde draw commands and gaps; excludes extract resource preparation");
            header.addProperty("coverage_audit_threshold", 0.15);
            header.addProperty("coverage_audit_rule", "same-load cohort statistics only; excess gap requires attribution, not invented zero or automatic measurement failure");
            writer.write(GSON.toJson(header));
            writer.newLine();
            for (int index = 0; index < current.size; index++) {
                var value = new JsonObject();
                value.addProperty("frame", index);
                value.addProperty("nano_time", current.times[index]);
                value.addProperty("phase", Phase.values()[current.phases[index]].name());
                value.addProperty("iris", current.iris[index]);
                value.addProperty("extract_cpu_ns", current.extractNs[index]);
                value.addProperty("extract_batches", current.extractBatches[index]);
                value.addProperty("gpu_sample_call", current.gpuCalls[index]);
                value.addProperty("gpu_sample_work_units", current.gpuUnits[index]);
                value.addProperty("gpu_mode", current.mode.name());
                value.addProperty("gpu_cohort", cohortName(current, index));
                value.addProperty("render_frame_id", current.renderFrames[index]);
                value.addProperty("render_frame_valid", current.validFrame[index]);
                value.add("horde_draw_coverage", drawCoverage(current, index));
                if (drawComplete(current, index)) {
                    value.addProperty("horde_draw_gpu_ns", current.drawNs[index]);
                } else {
                    value.add("horde_draw_gpu_ns", null);
                }
                var frameCoverage = coverage(current, index, null);
                value.add("gpu_coverage", frameCoverage);
                value.addProperty("gpu_incomplete", frameCoverage.get("incomplete").getAsBoolean());
                if (frameCoverage.get("complete").getAsBoolean()) {
                    long sum = 0;
                    for (var stage : GPU_STAGES) {
                        sum = Math.addExact(sum, current.gpuSums[stage.ordinal()][index]);
                    }
                    value.addProperty("gpu_frame_sum_ns", sum);
                } else {
                    value.add("gpu_frame_sum_ns", null);
                }
                value.addProperty("constraint_expected", current.constraintExpected[index]);
                value.addProperty("constraint_completed", current.constraintCompleted[index]);
                value.addProperty("constraint_errors", current.constraintErrors[index]);
                value.addProperty("constraints_complete", constraintsComplete(current, index));
                var stages = new JsonObject();
                for (var stage : STAGES) {
                    var item = new JsonObject();
                    item.addProperty("cpu_ns", current.cpu[stage.ordinal()][index]);
                    item.addProperty("cpu_frame_sum_ns", current.cpu[stage.ordinal()][index]);
                    item.addProperty("calls", current.calls[stage.ordinal()][index]);
                    if (stage.gpu) {
                        item.add("gpu_coverage", coverage(current, index, stage));
                    }
                    if (stage.gpu && complete(current, index, stage)) {
                        item.addProperty("gpu_frame_sum_ns", current.gpuSums[stage.ordinal()][index]);
                    } else {
                        item.add("gpu_frame_sum_ns", null);
                    }
                    long gpuNs = current.gpuNs[stage.ordinal()][index];
                    if (gpuNs < 0) {
                        item.add("gpu_sample_ns", null);
                    } else {
                        item.addProperty("gpu_sample_ns", gpuNs);
                    }
                    stages.add(stage.name().toLowerCase(Locale.ROOT), item);
                }
                value.add("stages", stages);
                var counts = new JsonObject();
                for (var counter : COUNTERS) {
                    if ((counter == Counter.PENETRATION_MAX || counter == Counter.PENETRATION_REJECTED)
                            && !constraintsComplete(current, index)) {
                        counts.add(counter.name().toLowerCase(Locale.ROOT), null);
                    } else {
                        counts.addProperty(counter.name().toLowerCase(Locale.ROOT), current.counts[counter.ordinal()][index]);
                    }
                }
                value.add("counts", counts);
                writer.write(GSON.toJson(value));
                writer.newLine();
            }
        }
        return current.destination;
    }

    private record StageSum(@Nullable Stage stage, long nanoseconds, int row) {
    }

    private record Pending(GpuQuery query, CommandEncoder encoder, int row, @Nullable Stage stage, int call, long units,
                           boolean sample) {
    }

    private static class Capture {
        private final Path destination;
        private final boolean gpu;
        private final GpuMode mode;
        private final long[] times = new long[MAX_FRAMES];
	    private final long firstRenderFrame = PhotonFrameCapture.renderFrameId() + 1;
	    private final long[] renderFrames = new long[MAX_FRAMES];
	    private final boolean[] validFrame = new boolean[MAX_FRAMES];
	    private final long[] drawNs = new long[MAX_FRAMES];
	    private final int[] constraintExpected = new int[MAX_FRAMES];
	    private final int[] constraintCompleted = new int[MAX_FRAMES];
	    private final int[] constraintErrors = new int[MAX_FRAMES];
	    private final int[] drawStarted = new int[MAX_FRAMES];
	    private final int[] drawCompleted = new int[MAX_FRAMES];
	    private final int[] drawIncomplete = new int[MAX_FRAMES];
	    private final boolean[] drawQueued = new boolean[MAX_FRAMES];
	    private long lastRenderFrame = Long.MIN_VALUE;
	    private long missingDrawFrames;
	    private int firstRowInFrame;
	    private int duplicateDraws;
	    private int stageCohorts;
	    private final int[] sampleStages = new int[MAX_FRAMES];
        private final byte[] phases = new byte[MAX_FRAMES];
        private final boolean[] iris = new boolean[MAX_FRAMES];
        private final long[] extractNs = new long[MAX_FRAMES];
        private final int[] extractBatches = new int[MAX_FRAMES];
        private final long[][] cpu = new long[STAGES.length][MAX_FRAMES];
        private final long[][] gpuNs = new long[STAGES.length][MAX_FRAMES];
	    private final long[][] gpuSums = new long[STAGES.length][MAX_FRAMES];
	    private final int[][] expected = new int[STAGES.length][MAX_FRAMES];
	    private final int[][] queryStarted = new int[STAGES.length][MAX_FRAMES];
	    private final int[][] queryCompleted = new int[STAGES.length][MAX_FRAMES];
	    private final int[][] incomplete = new int[STAGES.length][MAX_FRAMES];
	    private final int[] frameQueries = new int[MAX_FRAMES];
	    private final boolean[] frameBudget = new boolean[MAX_FRAMES];
	    private final boolean[] ended = new boolean[MAX_FRAMES];
	    private final boolean[][] queued = new boolean[STAGES.length][MAX_FRAMES];
        private final int[][] calls = new int[STAGES.length][MAX_FRAMES];
        private final long[][] counts = new long[COUNTERS.length][MAX_FRAMES];
        private final int[] gpuCalls = new int[MAX_FRAMES];
        private final long[] gpuUnits = new long[MAX_FRAMES];
        private final ArrayList<Pending> pending = new ArrayList<>();
        private Phase phase = Phase.SETUP;
        private int size, started, completed, busySkipped, pendingSkipped, cancelled, errors, reportDropped;

        private Capture(Path destination, boolean gpu, GpuMode mode) {
            this.destination = destination;
            this.gpu = gpu;
	        this.mode = mode;
            for (var values : gpuNs) {
                Arrays.fill(values, -1);
            }
        }
    }
}
