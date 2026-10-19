package org.unitego.lobecorp.util.photon.performance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceReportUtil.Frames;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mode;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Node;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonPerformanceReportUtilTest {
	@TempDir
	private Path directory;

	@AfterEach
	public void clear() {
		PhotonPerformanceTraceUtil.finish("test_cleanup");
	}

	@Test
	public void exportPreservesMissingMeasurementsAndQuotedObjectNames() throws Exception {
		PhotonPerformanceTraceUtil.start("export", Mode.DETAIL);
		var object = new Object();
		int node = PhotonPerformanceTraceUtil.node(object, new Node(0, 0, "model", "a,\"b\"\nline", "part", "lobecorp:test"));
		long scope = PhotonPerformanceTraceUtil.begin(Stage.MODEL_SAMPLE, node);
		PhotonPerformanceTraceUtil.end(scope, true);
		PhotonPerformanceTraceUtil.important(Stage.CLIENT_PHASE, node, "phase", "", Double.NaN, 0);
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		var summary = new JsonObject();
		summary.addProperty("state", "STOPPED");
		var frames = new Frames(summary, 1, capture.view().startedNs(), new long[]{0}, new long[]{-1},
				new long[]{5000}, new long[]{4000}, new long[]{-1}, new long[]{0}, new int[]{2560}, new int[]{1494},
				new int[]{1}, new int[]{0}, new int[]{0}, new int[]{260}, new String[]{"NONE"});
		var output = PhotonPerformanceReportUtil.write(directory.resolve("capture"), capture, frames, new JsonObject(), List.of());
		for (var name : List.of("session.json", "frames.csv", "objects.csv", "operations.jsonl", "trace.json", "gpu.jsonl", "report.md", "outliers.trace.json")) {
			assertTrue(Files.isRegularFile(output.resolve(name)), name);
		}
		var session = JsonParser.parseString(Files.readString(output.resolve("session.json"))).getAsJsonObject();
		assertEquals(PhotonPerformanceReportUtil.SCHEMA, session.get("schema").getAsString());
		var maximum = session.getAsJsonArray("aggregates").get(0).getAsJsonObject().getAsJsonObject("maximumEvent");
		assertTrue(maximum.get("local_time_ticks").isJsonNull());
		var gpu = JsonParser.parseString(Files.readAllLines(output.resolve("gpu.jsonl")).getFirst()).getAsJsonObject();
		assertEquals("unavailable", gpu.get("status").getAsString());
		assertTrue(gpu.get("duration_ns").isJsonNull());
		assertTrue(Files.readString(output.resolve("objects.csv")).contains("\"a,\"\"b\"\"\nline\""));
		var trace = JsonParser.parseString(Files.readString(output.resolve("trace.json"))).getAsJsonObject();
		assertTrue(trace.getAsJsonArray("traceEvents").asList().stream().anyMatch(value ->
				value.getAsJsonObject().has("dur") && value.getAsJsonObject().get("dur").getAsDouble() >= 0));
		assertFalse(Files.readString(output.resolve("session.json")).contains("NaN"));
		assertFalse(Files.readString(output.resolve("operations.jsonl")).contains("NaN"));
	}

	@Test
	public void capacityExportExplainsThePartialCaptureAndSeparatedLosses() throws Exception {
		PhotonPerformanceTraceUtil.start("capacity_export", Mode.DETAIL);
		long scope = PhotonPerformanceTraceUtil.begin(Stage.RENDER, 0);
		for (int index = 0; index < PhotonPerformanceTraceUtil.MAX_NODES - 1; index++) {
			PhotonPerformanceTraceUtil.namedNode(0, "part", Integer.toString(index), "part", "");
		}
		PhotonPerformanceTraceUtil.namedNode(0, "part", "overflow", "overflow", "");
		PhotonPerformanceTraceUtil.end(scope, true);
		var capture = PhotonPerformanceTraceUtil.finish("CAPACITY");
		assertNotNull(capture);
		var summary = new JsonObject();
		summary.addProperty("state", "CAPACITY");
		var frames = new Frames(summary, 1, capture.view().startedNs(), new long[]{0}, new long[]{-1},
				new long[]{-1}, new long[]{-1}, new long[]{-1}, new long[]{-1}, new int[]{2560}, new int[]{1494},
				new int[]{1}, new int[]{0}, new int[]{0}, new int[]{260}, new String[]{"NONE"});
		var output = PhotonPerformanceReportUtil.write(directory.resolve("capacity"), capture, frames, new JsonObject(), List.of());
		var session = JsonParser.parseString(Files.readString(output.resolve("session.json"))).getAsJsonObject();
		var quality = session.getAsJsonObject("collector_quality");
		assertEquals("CAPACITY", session.get("end_reason").getAsString());
		assertEquals("NODES", quality.getAsJsonObject("limit").get("reason").getAsString());
		assertEquals(1, quality.get("deniedNodeRequests").getAsLong());
		assertEquals(0, quality.get("droppedRawSpans").getAsLong());
		assertEquals(0, quality.get("deniedAggregateRequests").getAsLong());
		assertEquals(PhotonPerformanceTraceUtil.MAX_NODES, session.getAsJsonArray("nodes").size());
		assertTrue(Files.readString(output.resolve("report.md")).contains("提前结束的部分捕获"));
	}

	@Test
	public void exportRetainsTrackTargetAndPlaybackContext() throws Exception {
		PhotonPerformanceTraceUtil.start("context_export", Mode.DETAIL);
		int target = PhotonPerformanceTraceUtil.namedNode(0, "target", "target", "target", "");
		int player = PhotonPerformanceTraceUtil.namedNode(0, "timeline_player", "animation", "laser_start", "");
		int track = PhotonPerformanceTraceUtil.node(new Object(), new Node(0, target, "track", "position", "", ""), player);
		long frame = PhotonPerformanceTraceUtil.begin(Stage.ANIMATION_FRAME, player, 1.5);
		long sample = PhotonPerformanceTraceUtil.begin(Stage.TRACK, track, 1.5);
		PhotonPerformanceTraceUtil.end(sample, true);
		PhotonPerformanceTraceUtil.end(frame, true);
		var capture = PhotonPerformanceTraceUtil.finish("complete");
		assertNotNull(capture);
		var summary = new JsonObject();
		summary.addProperty("state", "STOPPED");
		var frames = new Frames(summary, 1, capture.view().startedNs(), new long[]{0}, new long[]{-1},
				new long[]{-1}, new long[]{-1}, new long[]{-1}, new long[]{-1}, new int[]{2560}, new int[]{1494},
				new int[]{1}, new int[]{0}, new int[]{0}, new int[]{260}, new String[]{"NONE"});
		var output = PhotonPerformanceReportUtil.write(directory.resolve("context"), capture, frames, new JsonObject(), List.of());
		var session = JsonParser.parseString(Files.readString(output.resolve("session.json"))).getAsJsonObject();
		var context = session.getAsJsonArray("node_contexts").get(0).getAsJsonObject();
		assertEquals(track, context.get("node").getAsInt());
		assertEquals(player, context.get("owner").getAsInt());
		assertEquals(target, session.getAsJsonArray("nodes").get(track).getAsJsonObject().get("parent").getAsInt());
		var csv = Files.readAllLines(output.resolve("objects.csv"));
		assertTrue(csv.getFirst().contains("context_node"));
		var trackRow = csv.stream().filter(line -> line.contains("\"TRACK\"")).findFirst().orElseThrow();
		assertEquals("\"" + player + "\"", trackRow.split(",")[3]);
		assertTrue(session.getAsJsonArray("aggregates").asList().stream()
				.anyMatch(value -> value.getAsJsonObject().get("stage").getAsString().equals("ANIMATION_FRAME")));
	}
}
