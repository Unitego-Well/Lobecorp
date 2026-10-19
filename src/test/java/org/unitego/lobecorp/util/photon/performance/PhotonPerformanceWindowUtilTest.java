package org.unitego.lobecorp.util.photon.performance;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceReportUtil.Frames;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Capture;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Event;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mode;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.View;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonPerformanceWindowUtilTest {
	@TempDir
	Path directory;

	@AfterEach
	public void clear() {
		PhotonPerformanceTraceUtil.finish("test_cleanup");
	}

	@Test
	public void intervalMetricsUsePreviousRenderAndKeepMeasuredZero() {
		var frames = frames(new long[]{0, 10, 30}, new long[]{-1, 10, 20}, new long[]{4, 7, 999},
				new long[]{-1, 0, 999}, new long[]{-1, 50, 70}, 100);
		var bounds = PhotonPerformanceWindowUtil.bounds(frames, 1, 2);
		assertEquals(100, bounds.fromNs());
		assertEquals(130, bounds.toNs());
		var metrics = PhotonPerformanceWindowUtil.frameMetrics(frames, 1, 2);
		assertEquals(11, metrics.cpu().sum());
		assertEquals(7, metrics.cpu().maximum());
		assertEquals(1, metrics.gpu().samples());
		assertEquals(1, metrics.gpu().missing());
		assertEquals(0, metrics.gpu().sum());
		assertEquals(120, metrics.allocations().sum());
		assertEquals(20, metrics.intervals().p99());
		assertThrows(IllegalArgumentException.class, () -> PhotonPerformanceWindowUtil.bounds(frames, 0, 2));
		assertThrows(IllegalArgumentException.class, () -> PhotonPerformanceWindowUtil.bounds(frames, 2, 1));
		assertThrows(IllegalArgumentException.class, () -> PhotonPerformanceWindowUtil.bounds(frames, 1, 3));
	}

	@Test
	public void missingDistributionsRemainNull() {
		var frames = frames(new long[]{0, 10}, new long[]{-1, 10}, new long[]{-1, -1},
				new long[]{-1, -1}, new long[]{-1, -1}, 0);
		var metrics = PhotonPerformanceWindowUtil.frameMetrics(frames, 1, 1);
		assertNull(metrics.cpu().sum());
		assertNull(metrics.gpu().p99());
		assertEquals(1, metrics.allocations().missing());
	}

	@Test
	public void crossingCallsDoNotInventSelfAllocationOrThreadCpu() {
		var accumulator = new PhotonPerformanceWindowUtil.CpuAccumulator(1, "render", 0, Stage.SEEK);
		var bounds = new PhotonPerformanceWindowUtil.Bounds(1, 1, 100, 200);
		accumulator.add(event(50, 150, 80, 1000, 60, 1), bounds);
		accumulator.add(event(150, 180, 20, 40, 15, 1), bounds);
		accumulator.add(event(190, 0, -1, -1, -1, 0), bounds);
		var row = accumulator.row();
		assertEquals(1, row.fullCalls());
		assertEquals(1, row.partialCalls());
		assertEquals(1, row.incomplete());
		assertEquals(80, row.inclusiveOverlapNs());
		assertEquals(20, row.knownSelfNs());
		assertEquals(40, row.knownSelfAllocatedBytes());
		assertEquals(15, row.knownInclusiveThreadCpuNs());
		assertEquals(1, row.allocationSamples());
		assertEquals(30, row.maximumFullNs());
	}

	@Test
	public void zeroDurationAtWindowStartIsACompletedCall() {
		var accumulator = new PhotonPerformanceWindowUtil.CpuAccumulator(1, "render", 0, Stage.SEEK);
		accumulator.add(event(100, 100, 0, 0, 0, 1), new PhotonPerformanceWindowUtil.Bounds(1, 1, 100, 200));
		assertEquals(1, accumulator.row().fullCalls());
		assertEquals(0, accumulator.row().maximumFullNs());
		assertEquals(1, accumulator.row().allocationSamples());
	}

	@Test
	public void nestedCallsStaySeparateInsteadOfSummingInclusiveTime() {
		var bounds = new PhotonPerformanceWindowUtil.Bounds(1, 1, 0, 100);
		var parent = new PhotonPerformanceWindowUtil.CpuAccumulator(1, "render", 0, Stage.SEEK);
		var child = new PhotonPerformanceWindowUtil.CpuAccumulator(1, "render", 1, Stage.REPLAY);
		parent.add(event(0, 100, 60, -1, -1, 1), bounds);
		child.add(event(20, 60, 40, -1, -1, 1), bounds);
		assertEquals(100, parent.row().inclusiveOverlapNs());
		assertEquals(40, child.row().inclusiveOverlapNs());
		assertEquals(100, parent.row().knownSelfNs() + child.row().knownSelfNs());
		assertEquals(0, parent.row().allocationSamples());
	}

	@Test
	public void gpuSelectionUsesWholeSubmissionEnvelopeAndKeepsUnknownSeparate() {
		var frames = frames(new long[]{0, 100}, new long[]{-1, 100}, new long[]{1, 1},
				new long[]{1, 1}, new long[]{1, 1}, 100);
		var result = PhotonPerformanceWindowUtil.analyze(emptyCapture(Mode.GPU), frames,
				List.of(gpu(110, 150, 9), gpu(90, 120, 90), gpu(150, -1, -1), gpu(120, 130, -1)), 1, 1, () -> false);
		assertEquals(1, result.gpu().size());
		assertEquals(9, result.gpu().getFirst().sumNs());
		assertEquals(1, result.gpu().getFirst().measured());
		assertEquals(1, result.gpu().getFirst().unavailable());
		assertEquals(1, result.gpuPartial());
		assertEquals(1, result.gpuUnknownSubmission());
	}

	@Test
	public void overviewRangeDoesNotPretendToHaveRawCalls() {
		PhotonPerformanceTraceUtil.start("overview_window", Mode.OVERVIEW);
		long token = PhotonPerformanceTraceUtil.begin(Stage.RENDER, 0);
		PhotonPerformanceTraceUtil.end(token, true);
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		var peak = capture.view().aggregates().getFirst().maximumEvent();
		assertNotNull(peak);
		long start = peak.startNs() - 1;
		long duration = Math.max(2, peak.endNs() - start + 1);
		var frames = frames(new long[]{0, duration}, new long[]{-1, duration}, new long[]{-1, -1},
				new long[]{-1, -1}, new long[]{-1, -1}, start);
		var result = PhotonPerformanceWindowUtil.analyze(capture, frames, List.of(), 1, 1, () -> false);
		assertFalse(result.detailedCpu());
		assertTrue(result.cpu().isEmpty());
		assertEquals(1, result.cpuPeakHints().size());
	}

	@Test
	public void cancelledAnalysisCannotPublishPartialRows() {
		var frames = frames(new long[]{0, 10}, new long[]{-1, 10}, new long[]{1, 1},
				new long[]{1, 1}, new long[]{1, 1}, 0);
		assertThrows(CancellationException.class, () -> PhotonPerformanceWindowUtil.analyze(emptyCapture(Mode.DETAIL),
				frames, List.of(gpu(1, 2, 1)), 1, 1, () -> true));
	}

	@Test
	public void timeBucketsPreserveLongFrameSpike() {
		var frames = frames(new long[]{0, 1, 2, 3, 4, 5}, new long[]{-1, 1, 1000, 1, 1, 1},
				new long[6], new long[6], new long[6], 0);
		int[] points = PhotonPerformanceWindowUtil.chartFrames(frames, 2);
		assertEquals(2, points.length);
		assertEquals(2, points[0]);
		assertEquals(0, PhotonPerformanceWindowUtil.chartFrames(frames, 0).length);
	}

	@Test
	public void rawRangeAndRetentionUseTimestamps() {
		PhotonPerformanceTraceUtil.start("raw_window", Mode.DETAIL);
		long token = PhotonPerformanceTraceUtil.begin(Stage.SEEK, 0);
		PhotonPerformanceTraceUtil.end(token, true);
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		var events = new ArrayList<Event>();
		var thread = capture.threads().getFirst();
		thread.forEachEvent(events::add);
		var event = events.getFirst();
		events.clear();
		thread.forEachEvent(event.startNs() - 1, event.endNs() + 1, events::add);
		assertEquals(1, events.size());
		events.clear();
		thread.forEachEvent(event.endNs() + 1, event.endNs() + 100, events::add);
		assertTrue(events.isEmpty());
		assertEquals(event.startNs(), thread.retention().firstNs());
		assertEquals(event.endNs(), thread.retention().lastNs());
		assertEquals(1, thread.retention().completed());
	}

	@Test
	public void windowExportIncludesCoverageAndNeverFillsMissingWithZero() throws Exception {
		var frames = frames(new long[]{0, 10}, new long[]{-1, 10}, new long[]{-1, -1},
				new long[]{-1, -1}, new long[]{-1, -1}, 0);
		var capture = emptyCapture(Mode.DETAIL);
		var result = PhotonPerformanceWindowUtil.analyze(capture, frames, List.of(), 1, 1, () -> false);
		PhotonPerformanceReportUtil.writeWindow(directory, capture, new JsonObject(), result);
		var json = JsonParser.parseString(Files.readString(directory.resolve("window.json"))).getAsJsonObject();
		assertTrue(json.getAsJsonObject("frames").getAsJsonObject("gpu").get("p99").isJsonNull());
		assertTrue(json.get("quality_scope").getAsString().contains("whole capture"));
		assertTrue(Files.readString(directory.resolve("cpu-window.csv")).contains("fully_contained_calls"));
		var other = new PhotonPerformanceWindowUtil.Result(99, result.bounds(), result.frames(), true, List.of(), List.of(),
				List.of(), List.of(), List.of(), List.of(), 0, 0, 0, 0, 0);
		assertThrows(IllegalArgumentException.class, () -> PhotonPerformanceReportUtil.writeWindow(directory, capture, new JsonObject(), other));
	}

	private static Event event(long from, long to, long self, long allocation, long cpu, int flags) {
		return new Event(1, 0, 0, 0, Stage.SEEK, 0, Double.NaN, from, to, self, allocation, allocation, cpu, flags);
	}

	private static Capture emptyCapture(Mode mode) {
		return new Capture(new View(7, "test", mode, 0, Instant.EPOCH, 0, 0,
				List.of(new PhotonPerformanceTraceUtil.Node(0, 0, "shared", "shared", "", "")),
				List.of(), List.of(), List.of(), 0, 0, 0, null,
				new PhotonPerformanceTraceUtil.Quality(0, 0, 0, 0, 0, 0, 0, null), List.of()), "complete", 1000, List.of());
	}

	private static JsonObject gpu(long from, long to, long duration) {
		var value = new JsonObject();
		value.addProperty("record_type", "sample");
		value.addProperty("pass", "shared");
		value.addProperty("node_id", 0);
		value.addProperty("ownership", "shared_or_unattributed");
		value.addProperty("cpu_submit_begin_ns", from);
		value.add("cpu_submit_end_ns", to < 0 ? JsonNull.INSTANCE : new JsonPrimitive(to));
		value.addProperty("status", duration < 0 ? "query_timeout" : "measured");
		value.addProperty("duration_ns", duration);
		return value;
	}

	private static Frames frames(long[] heads, long[] intervals, long[] cpu, long[] gpu, long[] allocation, long origin) {
		int count = heads.length;
		return new Frames(new JsonObject(), count, origin, heads, intervals, new long[count], cpu, gpu, allocation,
				new int[count], new int[count], new int[count], new int[count], new int[count], new int[count], new String[count]);
	}
}
