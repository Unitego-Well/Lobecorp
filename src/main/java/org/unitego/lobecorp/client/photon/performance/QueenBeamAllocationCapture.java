package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordedMethod;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 仅开发采样显式启用；采样权重不是逐对象精确分配计数，解析在帧时间记录结束后执行。
public class QueenBeamAllocationCapture {
	public static final String REQUEST_ENV = "LOBECORP_LASER_PROFILE_JFR";
	private static final long MAX_RECORDING_BYTES = 64L * 1024 * 1024;
	// 分组阈值：蓄力前 200 ms 归入蓄力，释放后 2 s 内归入释放。
	private static final long CHARGE_LEAD_NS = 200_000_000;
	private static final long RELEASE_NS = 2_000_000_000L;
	private static final Gson GSON = new Gson();
	private static final List<Stage> STAGES = new ArrayList<>();
	private static final Map<UUID, Integer> CYCLES = new LinkedHashMap<>();
	private static @Nullable Recording recording;
	private static Instant origin = Instant.EPOCH;
	private static long originNs;

	private record Stage(int cycle, int phase, long nanoTime) {
	}

	public static void start() {
		if (!Boolean.parseBoolean(System.getenv(REQUEST_ENV))) {
			return;
		}
		if (recording != null) {
			throw new IllegalStateException("allocation_recording_already_started");
		}
		STAGES.clear();
		CYCLES.clear();
		recording = new Recording();
		recording.setName("lobecorp_laser_allocation");
		recording.setMaxSize(MAX_RECORDING_BYTES);
		recording.enable("jdk.ObjectAllocationSample").with("throttle", "200/s").withStackTrace();
		recording.enable("jdk.GarbageCollection");
		recording.enable("jdk.GCPhasePause");
		recording.enable("jdk.Compilation");
		originNs = System.nanoTime();
		origin = Instant.now();
		recording.start();
	}

	public static void phase(UUID animation, double phase, long nanoTime) {
		if (recording != null) {
			var cycle = CYCLES.computeIfAbsent(animation, _ -> CYCLES.size());
			STAGES.add(new Stage(cycle, (int) phase, nanoTime));
		}
	}

	public static JsonObject finish(String label) throws IOException {
		var active = recording;
		var result = new JsonObject();
		result.addProperty("enabled", active != null);
		if (active == null) {
			return result;
		}
		recording = null;
		var directory = Minecraft.getInstance().gameDirectory.toPath().resolve("performance");
		Files.createDirectories(directory);
		var path = directory.resolve(label + "_allocation_" + System.nanoTime() + ".jfr");
		try (active) {
			active.stop();
			active.dump(path);
		}
		result.addProperty("jfr_path", path.toAbsolutePath().toString());
		result.addProperty("sample_weight_scope", "JFR ObjectAllocationSample weight; statistical bytes represented");
		Map<String, Map<String, Long>> stacks = new LinkedHashMap<>();
		Map<String, Map<String, Long>> classes = new LinkedHashMap<>();
		var pauses = new JsonArray();
		var compilations = new JsonArray();
		long samples = 0;
		try (var events = new RecordingFile(path)) {
			while (events.hasMoreEvents()) {
				var event = events.readEvent();
				long nanoTime = originNs + Duration.between(origin, event.getStartTime()).toNanos();
				var group = group(nanoTime);
				var type = event.getEventType().getName();
				if (type.equals("jdk.GCPhasePause")) {
					var pause = new JsonObject();
					pause.addProperty("nano_time", nanoTime);
					pause.addProperty("duration_ms", event.getDuration().toNanos() / 1_000_000.0);
					pause.addProperty("group", group);
					pauses.add(pause);
				} else if (type.equals("jdk.Compilation")) {
					var compiled = new JsonObject();
					RecordedMethod method = event.getValue("method");
					compiled.addProperty("method", method.getType().getName() + "." + method.getName());
					compiled.addProperty("nano_time", nanoTime);
					compiled.addProperty("duration_ms", event.getDuration().toNanos() / 1_000_000.0);
					compiled.addProperty("group", group);
					compilations.add(compiled);
				} else if (type.equals("jdk.ObjectAllocationSample") && event.getThread() != null
						&& event.getThread().getJavaName().equals("Render thread")) {
					samples++;
					long weight = event.getLong("weight");
					classes.computeIfAbsent(group, _ -> new LinkedHashMap<>())
							.merge(event.getClass("objectClass").getName(), weight, Long::sum);
					var trace = event.getStackTrace();
					if (trace != null) {
						var frames = new ArrayList<String>();
						for (var frame : trace.getFrames()) {
							var method = frame.getMethod();
							frames.add(method.getType().getName() + "." + method.getName() + ":" + frame.getLineNumber());
						}
						stacks.computeIfAbsent(group, _ -> new LinkedHashMap<>())
								.merge(String.join(" <- ", frames), weight, Long::sum);
					}
				}
			}
		}
		result.addProperty("render_thread_samples", samples);
		var groups = new JsonObject();
		for (var entry : stacks.entrySet()) {
			var data = new JsonObject();
			data.add("stacks", top(entry.getValue(), 40));
			data.add("classes", top(classes.getOrDefault(entry.getKey(), Map.of()), 20));
			groups.add(entry.getKey(), data);
		}
		result.add("allocation_groups", groups);
		result.add("gc_pauses", pauses);
		result.add("compilations", compilations);
		var summary = directory.resolve(label + "_allocation_summary_" + System.nanoTime() + ".json");
		Files.writeString(summary, GSON.toJson(result), StandardOpenOption.CREATE_NEW);
		var reference = new JsonObject();
		reference.addProperty("enabled", true);
		reference.addProperty("jfr_path", path.toAbsolutePath().toString());
		reference.addProperty("summary_path", summary.toAbsolutePath().toString());
		reference.addProperty("render_thread_samples", samples);
		return reference;
	}

	private static String group(long nanoTime) {
		Stage current = null;
		for (var stage : STAGES) {
			if (stage.phase() == 0 && nanoTime >= stage.nanoTime() - CHARGE_LEAD_NS && nanoTime < stage.nanoTime()) {
				return "charge_" + stage.cycle();
			}
			if (stage.nanoTime() <= nanoTime) {
				current = stage;
			}
		}
		if (current == null) {
			return "empty";
		}
		return switch (current.phase()) {
			case 0 -> "charge_" + current.cycle();
			case 1 -> (nanoTime < current.nanoTime() + RELEASE_NS ? "release_" : "sustain_") + current.cycle();
			default -> "recovery_and_idle_" + current.cycle();
		};
	}

	private static JsonArray top(Map<String, Long> values, int limit) {
		var result = new JsonArray();
		values.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).limit(limit)
				.forEach(entry -> {
					var item = new JsonObject();
					item.addProperty("name", entry.getKey());
					item.addProperty("weight_bytes", entry.getValue());
					result.add(item);
				});
		return result;
	}
}
