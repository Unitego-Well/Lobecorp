package org.unitego.lobecorp.client.bench;

import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil;

import com.google.gson.JsonObject;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import jdk.jfr.FlightRecorder;
import jdk.jfr.RecordingState;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;

import java.time.Duration;

/// 只增强本场景已由 BenchMod 创建的录制，并持有一次详细反序列化采集。
public class PhotonBenchDiagnostics {
	private final String label;
	private final String group;
	private final String scenario;
	private final JsonObject jfr = new JsonObject();
	private boolean started;
	private boolean finished;
	private boolean archived;
	private JsonObject details;

	public PhotonBenchDiagnostics(String label, String group) {
		this(label, group, LobecorpBenchConfig.PHOTON_FIRST_CAST_PERFORMANCE_SCENARIO);
	}

	public PhotonBenchDiagnostics(String label, String group, String scenario) {
		this.label = label;
		this.group = group;
		this.scenario = scenario;
	}

	public void start() {
		if (started) {
			throw new IllegalStateException("详细采集不能重复启动。");
		}

		var name = "modbench:" + scenario;
		var matches = FlightRecorder.getFlightRecorder().getRecordings().stream()
				.filter(recording -> name.equals(recording.getName()) && recording.getState() == RecordingState.RUNNING).toList();
		if (matches.size() != 1) {
			throw new IllegalStateException("未找到唯一 RUNNING 的自有 BenchMod JFR：" + name + "，数量=" + matches.size());
		}

		var recording = matches.getFirst();
		jfr.addProperty("game_pid", ProcessHandle.current().pid());
		jfr.addProperty("recording_id", recording.getId());
		jfr.addProperty("recording_name", recording.getName());
		jfr.addProperty("settings_requested_ns", System.nanoTime());
		recording.enable("jdk.ClassLoad").withThreshold(Duration.ZERO).withStackTrace();
		recording.enable("jdk.ClassDefine").withThreshold(Duration.ZERO).withStackTrace();
		recording.enable("jdk.FileRead").withThreshold(Duration.ofMillis(1)).withStackTrace();
		recording.enable("jdk.FileWrite").withThreshold(Duration.ofMillis(1)).withStackTrace();
		recording.enable("jdk.JavaMonitorEnter").withThreshold(Duration.ofMillis(1)).withStackTrace();
		var settings = recording.getSettings();
		var actual = new JsonObject();
		settings.forEach(actual::addProperty);
		jfr.add("actual_settings", actual);
		for (var event : new String[]{"jdk.ClassLoad", "jdk.ClassDefine", "jdk.FileRead", "jdk.FileWrite", "jdk.JavaMonitorEnter"}) {
			if (!"true".equals(settings.get(event + "#enabled")) || !"true".equals(settings.get(event + "#stackTrace"))) {
				throw new IllegalStateException("JFR 事件未启用：" + event);
			}

			var threshold = event.equals("jdk.ClassLoad") || event.equals("jdk.ClassDefine") ? "0 ns" : "1000000 ns";
			if (!threshold.equals(settings.get(event + "#threshold"))) {
				throw new IllegalStateException("JFR 阈值未生效：" + event + "=" + settings.get(event + "#threshold"));
			}
		}

		jfr.addProperty("settings_verified_ns", System.nanoTime());
		jfr.addProperty("recording_state", recording.getState().name());
		jfr.addProperty("settings_verified", true);
		jfr.addProperty("class_event_scope", "inclusive durations; ClassLoad and ClassDefine must not be summed");
		PhotonFXLoadDiagnosticsUtil.startDetailedCapture(label);
		started = true;
	}

	public void finish(BenchClientContext context) {
		if (!started) {
			return;
		}

		if (!finished) {
			finished = true;
			details = PhotonFXLoadDiagnosticsUtil.finishDetailedCapture();
			details.addProperty("comparison_group", group);
			details.add("bench_jfr", jfr.deepCopy());
		}

		if (!archived && details != null) {
			try {
				context.artifacts().write(label + "-deserialize-details.json", "application/json", details.toString());
				archived = true;
			} catch (Exception failure) {
				throw new IllegalStateException("详细反序列化采集归档失败。", failure);
			}
		}
	}

	public JsonObject details() {
		if (!finished || details == null) {
			throw new IllegalStateException("详细采集尚未结束。");
		}

		return details.deepCopy();
	}

	public JsonObject snapshot() {
		var report = new JsonObject();
		report.addProperty("comparison_group", group);
		report.addProperty("started", started);
		report.addProperty("finished", finished);
		report.addProperty("archived", archived);
		report.addProperty("artifact", label + "-deserialize-details.json");
		report.add("bench_jfr", jfr.deepCopy());
		return report;
	}
}
