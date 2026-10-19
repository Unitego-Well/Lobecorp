package org.unitego.lobecorp.util.photon.performance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Aggregate;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Capture;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Event;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mark;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Node;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// 冻结数据的本地导出；不在渲染线程序列化工程，不将环形保留窗口的分位称为全会话分位。
public class PhotonPerformanceReportUtil {
	private static final Gson GSON = new GsonBuilder().serializeNulls().registerTypeAdapter(Event.class, new TypeAdapter<Event>() {
		@Override
		public void write(JsonWriter writer, Event event) throws IOException {
			writer.beginObject();
			writer.name("id").value(Long.toUnsignedString(event.id()));
			writer.name("parent").value(Long.toUnsignedString(event.parent()));
			writer.name("frame_at_begin").value(event.frame());
			writer.name("node").value(event.node());
			writer.name("stage").value(event.stage().name());
			writer.name("generation").value(event.generation());
			writer.name("local_time_ticks");
			if (Double.isFinite(event.localTime())) {
				writer.value(event.localTime());
			} else {
				writer.nullValue();
			}
			writer.name("start_ns").value(event.startNs());
			writer.name("end_ns").value(event.endNs());
			writer.name("self_ns").value(event.selfNs());
			writer.name("allocated_bytes_approx");
			if (event.allocatedBytes() < 0) {
				writer.nullValue();
			} else {
				writer.value(event.allocatedBytes());
			}
			writer.name("self_allocated_bytes_approx");
			if (event.selfAllocatedBytes() < 0) {
				writer.nullValue();
			} else {
				writer.value(event.selfAllocatedBytes());
			}
			writer.name("inclusive_thread_cpu_ns");
			if (event.threadCpuNs() < 0) {
				writer.nullValue();
			} else {
				writer.value(event.threadCpuNs());
			}
			writer.name("completed").value(event.completed());
			writer.name("failed").value(event.failed());
			writer.endObject();
		}

		@Override
		public Event read(JsonReader reader) {
			throw new UnsupportedOperationException("export_only_event_adapter");
		}
	}.nullSafe()).create();
	private static final double NS_PER_MS = 1_000_000d;
	private static final double NS_PER_US = 1_000d;
	private static final int TOP_ROWS = 30;
	public static final String SCHEMA = "lobecorp_photon_performance_v1";

	public record Frames(JsonObject summary, int count, long originNs, long[] heads, long[] intervals,
	                     long[] minecraft, long[] cpu, long[] gpu, long[] allocations, int[] widths, int[] heights,
	                     int[] flags, int[] phases, int[] intervalPhases, int[] limits, String[] throttleReasons) {
	}

	private record Key(long thread, int node, Stage stage) {
	}

	public static Path write(Path directory, Capture capture, Frames frames, JsonObject environment,
	                         List<JsonObject> gpuSamples) throws IOException {
		Files.createDirectories(directory);
		var view = capture.view();
		var session = new JsonObject();
		session.addProperty("schema", SCHEMA);
		session.addProperty("session_id", view.sessionId());
		session.addProperty("label", view.label());
		session.addProperty("mode", view.mode().name());
		session.addProperty("started_ns", view.startedNs());
		session.addProperty("frame_origin_ns", frames.originNs());
		session.addProperty("started_utc", view.startedAt().toString());
		session.addProperty("started_local", view.startedAt().atZone(ZoneId.of("Asia/Shanghai")).toString());
		session.addProperty("finished_ns", capture.finishedNs());
		session.addProperty("end_reason", capture.reason());
		session.addProperty("problem", view.problem());
		session.addProperty("dropped_records", view.dropped());
		session.addProperty("overwritten_events", view.overwritten());
		session.addProperty("incomplete_spans", view.incomplete());
		session.add("collector_quality", GSON.toJsonTree(view.quality()));
		session.add("node_contexts", GSON.toJsonTree(view.contexts()));
		session.addProperty("node_context_scope", "secondary execution ownership; node parent retains actual target ownership; context is not an extra timed call");
		session.addProperty("capacity_scope", "first exhausted non-rolling budget stops new spans; already-open spans close; freeze at next render HEAD before appending a frame");
		session.addProperty("allocation_scope", "approximate same-thread heap counter deltas; self subtracts measured children");
		session.addProperty("object_quantile_scope", "per-call wall time of retained completed ring events; not whole-session or per-frame P99");
		session.addProperty("event_frame_scope", "render frame id at span begin; use monotonic timestamps for interval boundaries");
		session.addProperty("thread_cpu_scope", "inclusive platform-thread CPU in enabled coarse scopes; unavailable is not zero");
		session.addProperty("gpu_scope", "pass/batch device queries or GameRenderer.render; excludes extract/present/swap/limiter");
		session.add("environment", environment);
		session.add("frame_summary", frames.summary());
		try (var writer = new JsonWriter(writer(directory.resolve("session.json")))) {
			writer.beginObject();
			for (var entry : session.entrySet()) {
				writer.name(entry.getKey());
				GSON.toJson(entry.getValue(), writer);
			}
			writer.name("nodes").beginArray();
			for (var node : view.nodes()) {
				GSON.toJson(node, Node.class, writer);
			}
			writer.endArray().name("aggregates").beginArray();
			for (var aggregate : view.aggregates()) {
				GSON.toJson(aggregate, Aggregate.class, writer);
			}
			writer.endArray().name("counters").beginArray();
			for (var counter : view.counters()) {
				GSON.toJson(counter, PhotonPerformanceTraceUtil.Counter.class, writer);
			}
			writer.endArray().endObject();
		}
		writeFrames(directory.resolve("frames.csv"), frames, view.startedNs());
		writeObjects(directory.resolve("objects.csv"), capture);
		writeOperations(directory.resolve("operations.jsonl"), view.marks());
		writeTrace(directory.resolve("trace.json"), capture);
		writeGpu(directory.resolve("gpu.jsonl"), frames, gpuSamples);
		writeOutliers(directory.resolve("outliers.trace.json"), capture);
		writeReport(directory.resolve("report.md"), capture, frames);
		return directory;
	}

	private static BufferedWriter writer(Path path) throws IOException {
		return Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
	}

	private static void writeFrames(Path path, Frames frames, long origin) throws IOException {
		try (var writer = writer(path)) {
			row(writer, "frame_id", "head_offset_ns", "interval_ns", "previous_minecraft_ns", "render_cpu_ns",
					"render_gpu_ns", "render_thread_allocated_bytes", "framebuffer_width", "framebuffer_height",
					"flags", "phase_id", "interval_phase_id", "limit", "throttle_reason");
			for (int index = 0; index < frames.count(); index++) {
				row(writer, index, frames.originNs() + frames.heads()[index] - origin, available(frames.intervals()[index]),
						available(frames.minecraft()[index]), available(frames.cpu()[index]), available(frames.gpu()[index]),
						available(frames.allocations()[index]), frames.widths()[index], frames.heights()[index],
						frames.flags()[index], frames.phases()[index], frames.intervalPhases()[index],
						frames.limits()[index], frames.throttleReasons()[index]);
			}
		}
	}

	/// 区间报告只使用保留原始记录；计数器和独立保留的峰值证据均不能冒充区间总量。
	public static Path writeWindow(Path directory, Capture capture, JsonObject environment,
	                               PhotonPerformanceWindowUtil.Result result) throws IOException {
		if (capture.view().sessionId() != result.sessionId()) {
			throw new IllegalArgumentException("window_session_mismatch");
		}
		Files.createDirectories(directory);
		var value = new JsonObject();
		value.addProperty("schema", SCHEMA + "_window");
		value.addProperty("session_id", result.sessionId());
		value.addProperty("cpu_scope", "retained raw events only; inclusive overlap clipped; self/allocation/thread CPU only for fully contained completed calls; never sum nested inclusive rows");
		value.addProperty("gpu_scope", "retained whole GPU queries whose CPU submission envelope is fully contained; device clock uncalibrated; never clip duration or sum nested passes");
		value.addProperty("quality_scope", "drops/overwrites/incomplete are whole capture; retention envelopes are not proof of full window coverage");
		value.addProperty("peak_hint_scope", "independently retained whole-capture maxima intersecting the window; separate evidence, not additional window calls");
		value.addProperty("frame_scope", "interval[i] and allocation[i] span HEAD[i-1] to HEAD[i]; render CPU/GPU use i-1; quantiles nearest rank over measured selected samples");
		value.addProperty("detailed_cpu", result.detailedCpu());
		value.addProperty("global_dropped", result.globalDropped());
		value.addProperty("global_overwritten", result.globalOverwritten());
		value.addProperty("global_incomplete", result.globalIncomplete());
		value.add("collector_quality", GSON.toJsonTree(capture.view().quality()));
		value.add("node_contexts", GSON.toJsonTree(capture.view().contexts()));
		value.addProperty("gpu_partial_submission", result.gpuPartial());
		value.addProperty("gpu_unknown_submission", result.gpuUnknownSubmission());
		value.add("bounds", GSON.toJsonTree(result.bounds()));
		value.add("frames", GSON.toJsonTree(result.frames()));
		value.add("cpu", GSON.toJsonTree(result.cpu()));
		value.add("gpu", GSON.toJsonTree(result.gpu()));
		value.add("thread_retention", GSON.toJsonTree(result.retention()));
		value.add("cpu_peak_hints", GSON.toJsonTree(result.cpuPeakHints()));
		value.add("gpu_peak_hints", GSON.toJsonTree(result.gpuPeakHints()));
		value.add("nodes", GSON.toJsonTree(capture.view().nodes()));
		value.add("environment", environment);
		try (var writer = writer(directory.resolve("window.json"))) {
			GSON.toJson(value, writer);
		}
		var contexts = contexts(capture);
		try (var writer = writer(directory.resolve("cpu-window.csv"))) {
			row(writer, "thread_id", "thread_name", "node_id", "context_node", "name", "resource", "stage", "fully_contained_calls",
					"partial_calls", "incomplete", "failed", "inclusive_overlap_ns", "known_self_ns", "maximum_full_call_ns",
					"self_allocation_samples", "known_self_allocated_bytes", "thread_cpu_samples", "known_inclusive_thread_cpu_ns");
			for (var cpu : result.cpu()) {
				var node = node(capture, cpu.node());
				row(writer, cpu.threadId(), cpu.threadName(), cpu.node(), contexts.get(cpu.node()), node.name(), node.resource(), cpu.stage(),
						cpu.fullCalls(), cpu.partialCalls(), cpu.incomplete(), cpu.failed(), cpu.inclusiveOverlapNs(),
						cpu.fullCalls() == 0 ? null : cpu.knownSelfNs(), cpu.fullCalls() == 0 ? null : cpu.maximumFullNs(),
						cpu.allocationSamples(), cpu.allocationSamples() == 0 ? null : cpu.knownSelfAllocatedBytes(),
						cpu.cpuSamples(), cpu.cpuSamples() == 0 ? null : cpu.knownInclusiveThreadCpuNs());
			}
		}
		writeOperations(directory.resolve("operations.jsonl"), result.marks());
		return directory;
	}

	private static void writeObjects(Path path, Capture capture) throws IOException {
		var contexts = contexts(capture);
		var samples = new HashMap<Key, LongArrayList>();
		for (var thread : capture.threads()) {
			thread.forEachEvent(event -> {
				if (event.completed()) {
					var key = new Key(thread.threadId(), event.node(), event.stage());
					samples.computeIfAbsent(key, _ -> new LongArrayList()).add(event.endNs() - event.startNs());
				}
			});
		}
		try (var writer = writer(path)) {
			row(writer, "thread_id", "node_id", "parent_node", "context_node", "kind", "name", "definition_id", "resource", "stage",
					"calls", "inclusive_ns", "self_ns", "self_mean_ns", "max_call_ns", "retained_samples",
					"retained_call_p95_ns", "retained_call_p99_ns", "allocated_bytes_approx",
					"self_allocated_bytes_approx", "allocation_samples", "inclusive_thread_cpu_ns", "cpu_samples", "failures",
					"quantile_scope");
			for (var aggregate : capture.view().aggregates()) {
				var node = node(capture, aggregate.node());
				var values = samples.get(new Key(aggregate.threadId(), aggregate.node(), aggregate.stage()));
				long[] sorted = values == null ? new long[0] : values.toLongArray();
				java.util.Arrays.sort(sorted);
				row(writer, aggregate.threadId(), node.id(), node.parent(), contexts.get(node.id()), node.kind(), node.name(), node.definitionId(),
						node.resource(), aggregate.stage(), aggregate.calls(), aggregate.inclusiveNs(), aggregate.selfNs(),
						aggregate.calls() == 0 ? "" : aggregate.selfNs() / aggregate.calls(), aggregate.maximumNs(), sorted.length,
						quantile(sorted, 0.95), quantile(sorted, 0.99),
						aggregate.allocationSamples() == 0 ? "" : aggregate.allocatedBytes(),
						aggregate.allocationSamples() == 0 ? "" : aggregate.selfAllocatedBytes(),
						aggregate.allocationSamples(), aggregate.cpuSamples() == 0 ? "" : aggregate.cpuNs(),
						aggregate.cpuSamples(), aggregate.failures(), "retained_completed_calls");
			}
		}
	}

	private static void writeOperations(Path path, List<Mark> marks) throws IOException {
		try (var writer = writer(path)) {
			for (var mark : marks) {
				writer.write(GSON.toJson(mark(mark)));
				writer.newLine();
			}
		}
	}

	private static JsonObject mark(Mark mark) {
		var value = new JsonObject();
		value.addProperty("time_ns", mark.timeNs());
		value.addProperty("frame_at_begin", mark.frame());
		value.addProperty("thread_id", mark.threadId());
		value.addProperty("stage", mark.stage().name());
		value.addProperty("node_id", mark.node());
		value.addProperty("code", mark.code());
		value.addProperty("detail", mark.detail());
		number(value, "value", mark.value());
		number(value, "secondary", mark.secondary());
		return value;
	}

	private static void writeTrace(Path path, Capture capture) throws IOException {
		try (var writer = new JsonWriter(writer(path))) {
			writer.beginObject().name("displayTimeUnit").value("ms").name("traceEvents").beginArray();
			for (var thread : capture.threads()) {
				var metadata = new JsonObject();
				metadata.addProperty("ph", "M");
				metadata.addProperty("pid", 1);
				metadata.addProperty("tid", thread.threadId());
				metadata.addProperty("name", "thread_name");
				var args = new JsonObject();
				args.addProperty("name", thread.threadName());
				metadata.add("args", args);
				GSON.toJson(metadata, writer);
				try {
					thread.forEachEvent(event -> {
						if (event.completed()) {
							try {
								GSON.toJson(traceEvent(capture, thread.threadId(), event), writer);
							} catch (RuntimeException failure) {
								throw failure;
							}
						}
					});
				} catch (UncheckedIOException failure) {
					throw failure.getCause();
				}
			}
			for (var mark : capture.view().marks()) {
				var value = new JsonObject();
				value.addProperty("ph", "i");
				value.addProperty("s", "t");
				value.addProperty("pid", 1);
				value.addProperty("tid", mark.threadId());
				value.addProperty("name", mark.code());
				value.addProperty("cat", mark.stage().name());
				value.addProperty("ts", (mark.timeNs() - capture.view().startedNs()) / NS_PER_US);
				value.add("args", mark(mark));
				GSON.toJson(value, writer);
			}
			writer.endArray().endObject();
		}
	}


	private static void writeOutliers(Path path, Capture capture) throws IOException {
		try (var writer = new JsonWriter(writer(path))) {
			writer.beginObject().name("displayTimeUnit").value("ms").name("traceEvents").beginArray();
			for (var aggregate : capture.view().aggregates()) {
				if (aggregate.maximumEvent() != null) {
					GSON.toJson(traceEvent(capture, aggregate.threadId(), aggregate.maximumEvent()), writer);
				}
			}
			for (var mark : capture.view().marks()) {
				var value = new JsonObject();
				value.addProperty("ph", "i");
				value.addProperty("s", "t");
				value.addProperty("pid", 1);
				value.addProperty("tid", mark.threadId());
				value.addProperty("name", mark.code());
				value.addProperty("ts", (mark.timeNs() - capture.view().startedNs()) / NS_PER_US);
				value.add("args", mark(mark));
				GSON.toJson(value, writer);
			}
			writer.endArray().endObject();
		}
	}

	private static JsonObject traceEvent(Capture capture, long thread, Event event) {
		var value = new JsonObject();
		value.addProperty("ph", "X");
		value.addProperty("pid", 1);
		value.addProperty("tid", thread);
		value.addProperty("name", node(capture, event.node()).name() + " / " + event.stage().name());
		value.addProperty("cat", event.stage().name());
		value.addProperty("ts", (event.startNs() - capture.view().startedNs()) / NS_PER_US);
		value.addProperty("dur", (event.endNs() - event.startNs()) / NS_PER_US);
		var args = new JsonObject();
		args.addProperty("span_id", Long.toUnsignedString(event.id()));
		args.addProperty("parent_span_id", Long.toUnsignedString(event.parent()));
		args.addProperty("node_id", event.node());
		args.addProperty("frame_at_begin", event.frame());
		args.addProperty("observed_generation", event.generation());
		number(args, "local_time_ticks", event.localTime());
		args.addProperty("self_ns", event.selfNs());
		args.addProperty("failed", event.failed());
		args.add("allocated_bytes_approx", event.allocatedBytes() < 0 ? JsonNull.INSTANCE : GSON.toJsonTree(event.allocatedBytes()));
		args.add("self_allocated_bytes_approx", event.selfAllocatedBytes() < 0 ? JsonNull.INSTANCE : GSON.toJsonTree(event.selfAllocatedBytes()));
		args.add("thread_cpu_ns", event.threadCpuNs() < 0 ? JsonNull.INSTANCE : GSON.toJsonTree(event.threadCpuNs()));
		value.add("args", args);
		return value;
	}

	private static void writeGpu(Path path, Frames frames, List<JsonObject> details) throws IOException {
		try (var writer = writer(path)) {
			for (int index = 0; index < frames.count(); index++) {
				var value = new JsonObject();
				value.addProperty("frame_id", index);
				value.addProperty("pass", "GameRenderer.render");
				value.addProperty("source", "global TimerQuery");
				value.addProperty("status", frames.gpu()[index] < 0 ? "unavailable" : "measured");
				value.add("duration_ns", frames.gpu()[index] < 0 ? JsonNull.INSTANCE : GSON.toJsonTree(frames.gpu()[index]));
				value.add("gpu_timestamp_ns", JsonNull.INSTANCE);
				writer.write(GSON.toJson(value));
				writer.newLine();
			}
			for (var value : details) {
				writer.write(GSON.toJson(value));
				writer.newLine();
			}
		}
	}

	private static void writeReport(Path path, Capture capture, Frames frames) throws IOException {
		var ordered = new ArrayList<>(capture.view().aggregates());
		ordered.sort(Comparator.comparingLong(Aggregate::selfNs).reversed());
		try (var writer = writer(path)) {
			writer.write("# Photon 性能记录\n\n");
			writer.write("会话：" + capture.view().label() + "；模式：" + capture.view().mode() + "；帧数：" + frames.count() + "。\n\n");
			writer.write("记录范围：整会话聚合 self 与 inclusive 分列；objects.csv 的 P95/P99 只来自保留的完整调用，不是全会话逐帧分位。GPU 与 CPU 不相加，共享 pass 不分摊给每个对象。\n\n");
			writer.write("丢样：" + capture.view().dropped() + "；环形覆盖：" + capture.view().overwritten()
					+ "；未完成作用域：" + capture.view().incomplete() + "；结束原因：" + capture.reason() + "。\n\n");
			var quality = capture.view().quality();
			writer.write("丢样分类：节点请求 " + quality.deniedNodeRequests() + "；线程请求 " + quality.deniedThreadRequests()
					+ "；原始 span " + quality.droppedRawSpans() + "；深度 " + quality.droppedDepthSpans()
					+ "；聚合请求 " + quality.deniedAggregateRequests() + "；计数器请求 " + quality.deniedCounterRequests()
					+ "；重要标记 " + quality.droppedImportantMarks() + "。请求数不代表唯一对象数。\n\n");
			if (quality.limit() != null) {
				writer.write("容量中止：" + quality.limit().reason() + "；触发帧 " + quality.limit().frame()
						+ "；触发时间 ns " + quality.limit().timeNs()
						+ "。这是提前结束的部分捕获；原预算未扩大，不补造未测调用。\n\n");
			}
			writer.write("| 对象 | 阶段 | 线程 | 调用 | self 合计 ms | 最大调用 ms |\n|---|---|---:|---:|---:|---:|\n");
			for (int index = 0; index < Math.min(TOP_ROWS, ordered.size()); index++) {
				var value = ordered.get(index);
				writer.write(String.format(Locale.ROOT, "| %s | %s | %d | %d | %.4f | %.4f |%n",
						node(capture, value.node()).name().replace("|", "\\|"), value.stage(), value.threadId(),
						value.calls(), value.selfNs() / NS_PER_MS, value.maximumNs() / NS_PER_MS));
			}
			writer.write("\n完整原始数据见 session.json、frames.csv、objects.csv、operations.jsonl、trace.json、gpu.jsonl；outliers.trace.json 保留每项最大调用，避免首次卡顿被滚动事件覆盖。"
					+ "没有对象运行不等于零成本；未采到、未归属、查询未回收、模式限制和容量限制需结合原始字段判断。"
					+ "本报告不自动推定根因或宣称达到稳定帧率。\n");
		}
	}

	private static Int2IntOpenHashMap contexts(Capture capture) {
		var result = new Int2IntOpenHashMap();
		for (var context : capture.view().contexts()) {
			result.put(context.node(), context.owner());
		}
		return result;
	}

	private static Node node(Capture capture, int id) {
		return capture.view().nodes().get(id >= 0 && id < capture.view().nodes().size() ? id : 0);
	}

	private static Object quantile(long[] sorted, double quantile) {
		return sorted.length == 0 ? "" : sorted[(int) Math.ceil(sorted.length * quantile) - 1];
	}

	private static Object available(long value) {
		return value < 0 ? "" : value;
	}

	private static void number(JsonObject object, String key, double value) {
		if (Double.isFinite(value)) {
			object.addProperty(key, value);
		} else {
			object.add(key, JsonNull.INSTANCE);
		}
	}

	private static void row(BufferedWriter writer, Object... values) throws IOException {
		for (int index = 0; index < values.length; index++) {
			if (index > 0) {
				writer.write(',');
			}
			writer.write('"');
			writer.write(String.valueOf(values[index] == null ? "" : values[index]).replace("\"", "\"\""));
			writer.write('"');
		}
		writer.newLine();
	}
}
