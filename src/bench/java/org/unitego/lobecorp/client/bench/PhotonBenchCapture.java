package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import org.unitego.lobecorp.client.photon.performance.PhotonBenchConditions;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.client.photon.performance.PhotonSubmissionDiagnostics;

import java.nio.file.Files;
import java.nio.file.Path;

/// 模型基准的现有帧采集与提交计数适配；原始帧以独立文件附入同一 BenchMod 报告。
@SuppressWarnings("resource") // Minecraft 是上下文借用对象，不归采集适配器关闭。
public class PhotonBenchCapture {
	private final String label;
	private JsonObject setupSubmissions;
	private JsonObject measuredSubmissions;
	private JsonObject capture;
	private boolean capturing;
	private boolean naturalDuration;
	private double requestedSeconds;
	private final boolean submissionProbes;

	public PhotonBenchCapture(String label, long seed) {
		this.label = label;
		if (PhotonFrameCapture.liveStatus().has("state")
				&& PhotonFrameCapture.liveStatus().get("state").getAsString().equals("RUNNING")) {
			throw new IllegalStateException("不能覆盖其他所有者的帧采集。");
		}

		if (PhotonSubmissionDiagnostics.enabled()) {
			throw new IllegalStateException("不能覆盖其他所有者的提交诊断。");
		}
		PhotonBenchConditions.begin(seed);
		submissionProbes = PhotonSubmissionDiagnostics.probesRequested();
		if (submissionProbes) {
			PhotonSubmissionDiagnostics.beginCase(label);
		}
	}

	public void start(double seconds, Runnable cast) {
		if (PhotonFrameCapture.liveStatus().has("state")
				&& PhotonFrameCapture.liveStatus().get("state").getAsString().equals("RUNNING")) {
			throw new IllegalStateException("主测量开始前出现其他所有者的帧采集。");
		}
		naturalDuration = cast != null;
		requestedSeconds = seconds;
		if (submissionProbes) {
			setupSubmissions = PhotonSubmissionDiagnostics.endCase();
			PhotonSubmissionDiagnostics.beginCase(label + "_measure");
		}
		PhotonFrameCapture.start(label.replace('-', '_'), seconds);
		capturing = true;
		if (submissionProbes) {
			PhotonSubmissionDiagnostics.bindFrameCapture();
		}
		if (cast != null) {
			PhotonFrameCapture.phase("cast_0", 4, cast);
			PhotonFrameCapture.phase("cast_1", 31, cast);
			PhotonFrameCapture.phase("cast_2", 58, cast);
		}
	}

	public boolean complete() {
		var status = PhotonFrameCapture.liveStatus();
		if (!status.has("state") || status.get("state").getAsString().equals("RUNNING")) {
			return false;
		}

		capture = ownedSnapshot();
		measuredSubmissions = submissionProbes ? PhotonSubmissionDiagnostics.endCase() : null;
		capturing = false;
		validateCapture();
		return true;
	}

	private JsonObject ownedSnapshot() {
		var value = PhotonFrameCapture.snapshot();
		if (!value.has("label") || !label.replace('-', '_').equals(value.get("label").getAsString())) {
			throw new IllegalStateException("当前帧采集不属于此模型场景。");
		}
		return value;
	}

	private void validateCapture() {
		if (capture == null || !capture.has("saved_path") || capture.get("frame_count").getAsInt() == 0) {
			throw new IllegalStateException("模型基准缺少完整帧与 GPU 记录。");
		}
		if (!capture.get("gpu_available").getAsBoolean() || capture.get("gpu_completed").getAsInt() == 0) {
			throw new IllegalStateException("模型基准没有有效 GPU 查询，不能通过夹具验收。");
		}
		var state = capture.get("state").getAsString();
		if (naturalDuration) {
			if (!state.equals("DURATION") || capture.get("elapsed_seconds").getAsDouble() + 0.001 < requestedSeconds
					|| capture.getAsJsonArray("phases").size() != 3
					|| capture.get("current_phase_id").getAsInt() != 3) {
				throw new IllegalStateException("真实激光主采集没有自然完成请求时长和全部阶段：" + state);
			}
		} else if (!state.equals("STOPPED")) {
			throw new IllegalStateException("按基准 tick 结束的帧窗口意外提前终止：" + state);
		}
		if (submissionProbes && measuredSubmissions != null
				&& measuredSubmissions.get("scope_protocol_errors").getAsLong() != 0) {
			throw new IllegalStateException("提交诊断作用域不平衡。");
		}
	}

	public void stop() {
		if (!capturing) {
			return;
		}

		ownedSnapshot();
		PhotonFrameCapture.stop();
		capture = ownedSnapshot();
		measuredSubmissions = submissionProbes ? PhotonSubmissionDiagnostics.endCase() : null;
		capturing = false;
	}

	public void write(BenchClientContext context, JsonObject report) throws Exception {
		stop();
		report.addProperty("seed", context.seed());
		report.addProperty("capture_mode", naturalDuration ? "natural_wall_duration" : "bench_measure_ticks");
		report.addProperty("submission_probes", submissionProbes);
		report.addProperty("probe_switch", PhotonSubmissionDiagnostics.probeState());
		report.addProperty("wp0_cross_run_gate_verified", false);
		report.addProperty("fixed_camera_observed", measuredSubmissions != null
				&& measuredSubmissions.get("camera_changes").getAsLong() == 0
				&& measuredSubmissions.get("camera_unavailable_frames").getAsLong() == 0);
		report.addProperty("fixed_window_observed", measuredSubmissions != null
				&& measuredSubmissions.get("window_changes").getAsLong() == 0);
		report.addProperty("particle_seed_control", "per_case_sequence_at_setEffect_return");
		report.addProperty("seeded_objects", PhotonBenchConditions.seededObjects());
		report.addProperty("particle_seed_determinism_verified", false);
		report.addProperty("player_pose", context.automation().pose().toString());
		report.add("setup_submissions", setupSubmissions == null ? JsonNull.INSTANCE : setupSubmissions);
		report.add("measure_submissions", measuredSubmissions == null ? JsonNull.INSTANCE : measuredSubmissions);
		report.add("capture", capture);
		report.addProperty("main_capture_started_ns", capture.get("started_ns").getAsLong());
		report.addProperty("main_capture_frames", capture.get("frame_count").getAsInt());
		validateCapture();

		var path = Path.of(capture.get("saved_path").getAsString()).toAbsolutePath().normalize();
		var directory = context.minecraft().gameDirectory.toPath().resolve("performance").toAbsolutePath().normalize();
		if (!path.startsWith(directory)) {
			throw new IllegalStateException("帧产物不属于当前基准目录。");
		}

		context.artifacts().write(label + "-frames.json", "application/json", Files.readAllBytes(path));
	}

	public void close() {
		try {
			stop();
		} finally {
			PhotonSubmissionDiagnostics.endCase();
			PhotonBenchConditions.end();
		}
	}
}
