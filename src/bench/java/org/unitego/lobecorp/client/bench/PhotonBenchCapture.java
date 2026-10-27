package org.unitego.lobecorp.client.bench;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zhongbai233.bench.api.neoforge.client.BenchClientContext;
import org.unitego.lobecorp.client.photon.performance.PhotonBenchConditions;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.client.photon.performance.PhotonPerformanceCapture;
import org.unitego.lobecorp.client.photon.performance.PhotonSubmissionDiagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/// 模型基准的现有帧采集与提交计数适配；原始帧以独立文件附入同一 BenchMod 报告。
@SuppressWarnings("resource") // Minecraft 是上下文借用对象，不归采集适配器关闭。
public class PhotonBenchCapture {
	private final String label;
	private final String captureLabel;
	private final String prewarmLabel;
	private JsonObject prewarm;
	private String activeLabel;
	private String submissionLabel;
	private long prewarmTriggeredNs;
	private long prewarmPoseReadyNs;
	private long prewarmStoppedNs;
	private boolean prewarmFinished;
	private JsonObject setupSubmissions;
	private JsonObject measuredSubmissions;
	private JsonObject capture;
	private boolean capturing;
	private boolean naturalDuration;
	private boolean singleCast;
	private double requestedSeconds;
	private final boolean submissionProbes;

	public PhotonBenchCapture(String label, long seed) {
		this.label = label;
		captureLabel = label.replace('-', '_') + "_" + UUID.randomUUID().toString().replace("-", "");
		prewarmLabel = captureLabel + "_setup_prewarm";
		if (PhotonPerformanceCapture.active()) {
			throw new IllegalStateException("不能接管其他所有者的托管性能采集。");
		}
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
			submissionLabel = captureLabel + "_setup";
			PhotonSubmissionDiagnostics.beginCase(submissionLabel);
		}
	}

	/// setup 触发与首个 render HEAD 分开记录；位姿门禁前的帧不作为同相位截图证据。
	public void startPrewarm() {
		requireIdleCapture();
		prewarmTriggeredNs = System.nanoTime();
		PhotonFrameCapture.start(prewarmLabel, PhotonFrameCapture.maxSeconds());
		activeLabel = prewarmLabel;
	}

	public void prewarmPoseReady() {
		if (prewarmTriggeredNs != 0 && prewarmPoseReadyNs == 0) {
			prewarmPoseReadyNs = System.nanoTime();
		}
	}

	public void finishPrewarm(BenchClientContext context) {
		if (prewarmTriggeredNs == 0 || prewarmFinished) {
			return;
		}

		try {
			prewarmStoppedNs = System.nanoTime();
			prewarm = stopOwnedCapture();
			annotatePrewarm();
			// 普通 FrameCapture.stop 同步保存；托管异步导出在入口拒绝，不能按文件尚未落盘当作空样本。
			context.artifacts().write(label + "-prewarm-summary.json", "application/json", prewarm.toString());
			archive(context, prewarm, label + "-prewarm-frames.json");
			validateFrames(prewarm);
			if (!prewarm.get("state").getAsString().equals("STOPPED") || prewarmPoseReadyNs == 0) {
				throw new IllegalStateException("setup 预热采集提前结束或相机门禁未完成：" + prewarm.get("state"));
			}

			prewarmFinished = true;
		} catch (Exception failure) {
			throw new IllegalStateException("setup 预热窗口未完整归档或验收失败。", failure);
		}
	}

	private void annotatePrewarm() {
		prewarm.addProperty("setup_triggered_ns", prewarmTriggeredNs);
		prewarm.addProperty("pose_gate_ready_ns", prewarmPoseReadyNs);
		prewarm.addProperty("window_stop_requested_ns", prewarmStoppedNs);
		prewarm.addProperty("pose_gate_boundary", "setup_before_pose_gate_then_stabilize_and_warmup");
		prewarm.addProperty("same_phase_screenshot_evidence", false);
	}

	private void requireIdleCapture() {
		if (PhotonFrameCapture.running() || PhotonPerformanceCapture.active()) {
			throw new IllegalStateException("不能覆盖其他所有者的帧或托管性能采集。");
		}
	}

	private JsonObject stopOwnedCapture() {
		var value = ownedSnapshot();
		if (value.get("state").getAsString().equals("RUNNING")) {
			PhotonFrameCapture.stop();
			value = ownedSnapshot();
		}

		activeLabel = null;
		return value;
	}

	private void archive(BenchClientContext context, JsonObject value, String name) throws Exception {
		if (!value.has("saved_path") || value.get("saved_path").isJsonNull()) {
			throw new IllegalStateException("帧窗口没有已保存的原始数据：" + value.get("state"));
		}

		var path = Path.of(value.get("saved_path").getAsString()).toAbsolutePath().normalize();
		var directory = context.minecraft().gameDirectory.toPath().resolve("performance").toAbsolutePath().normalize();
		if (!path.startsWith(directory)) {
			throw new IllegalStateException("帧产物不属于当前基准目录。");
		}

		var bytes = Files.readAllBytes(path);
		context.artifacts().write(name, "application/json", bytes);
		var raw = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		var summary = raw.getAsJsonObject("summary");
		if (!summary.get("label").getAsString().equals(value.get("label").getAsString())
				|| summary.get("started_ns").getAsLong() != value.get("started_ns").getAsLong()) {
			throw new IllegalStateException("原始帧文件不属于当前窗口。");
		}

		var flags = raw.getAsJsonObject("frames").getAsJsonArray("flags");
		var bits = raw.getAsJsonObject("flag_bits");
		int required = bits.get("focused").getAsInt() | bits.get("maximized").getAsInt();
		int iconified = bits.get("iconified").getAsInt();
		if (flags.size() != value.get("frame_count").getAsInt()) {
			throw new IllegalStateException("原始帧数量与窗口 summary 不一致。");
		}

		for (var flag : flags) {
			int state = flag.getAsInt();
			if ((state & required) != required || (state & iconified) != 0) {
				throw new IllegalStateException("正式帧窗口存在失焦、未最大化或最小化帧。");
			}
		}
	}

	private void validateFrames(JsonObject value) {
		if (value == null || !value.has("saved_path") || value.get("saved_path").isJsonNull()
				|| value.get("frame_count").getAsInt() == 0) {
			throw new IllegalStateException("模型基准缺少完整帧与 GPU 记录。");
		}
		if (!value.get("gpu_available").getAsBoolean() || value.get("gpu_completed").getAsInt() == 0
				|| value.get("gpu_errors").getAsInt() != 0 || value.get("gpu_pending").getAsBoolean()) {
			throw new IllegalStateException("模型基准没有完整有效的 GPU 查询，不能通过夹具验收。");
		}
	}

	private JsonObject endOwnedSubmissions() {
		if (!submissionProbes || submissionLabel == null) {
			return null;
		}

		var value = PhotonSubmissionDiagnostics.snapshot();
		if (!value.has("case") || !submissionLabel.equals(value.get("case").getAsString())) {
			throw new IllegalStateException("当前提交诊断不属于此模型场景。");
		}

		submissionLabel = null;
		return PhotonSubmissionDiagnostics.endCase();
	}

	public void start(double seconds, Runnable cast) {
		start(seconds, cast, false);
	}

	public void start(double seconds, Runnable cast, boolean singleCast) {
		if (prewarmTriggeredNs != 0 && !prewarmFinished) {
			throw new IllegalStateException("主测量不能越过尚未完成的 setup 预热窗口。");
		}
		requireIdleCapture();
		naturalDuration = cast != null;
		this.singleCast = singleCast;
		requestedSeconds = seconds;
		if (submissionProbes) {
			setupSubmissions = endOwnedSubmissions();
			submissionLabel = captureLabel + "_measure";
			PhotonSubmissionDiagnostics.beginCase(submissionLabel);
		}
		PhotonFrameCapture.start(captureLabel, seconds);
		activeLabel = captureLabel;
		capturing = true;
		if (submissionProbes) {
			PhotonSubmissionDiagnostics.bindFrameCapture();
		}
		if (cast != null) {
			if (singleCast) {
				PhotonFrameCapture.phase("cast_0", 2, cast);
			} else {
				PhotonFrameCapture.phase("cast_0", 4, cast);
				PhotonFrameCapture.phase("cast_1", 31, cast);
				PhotonFrameCapture.phase("cast_2", 58, cast);
			}
		}
	}

	/// 截图请求点只取轻量实时计数，不在采样中构造逐帧分位快照。
	public JsonObject observation() {
		if (activeLabel != null) {
			var value = PhotonFrameCapture.liveStatus();
			value.addProperty("label", activeLabel);
			return value;
		}

		return capture == null ? new JsonObject() : capture.deepCopy();
	}

	public boolean complete() {
		if (!capturing) {
			return capture != null;
		}

		var status = PhotonFrameCapture.liveStatus();
		if (!status.has("state") || status.get("state").getAsString().equals("RUNNING")) {
			return false;
		}

		capture = ownedSnapshot();
		activeLabel = null;
		measuredSubmissions = endOwnedSubmissions();
		capturing = false;
		validateCapture();
		return true;
	}

	private JsonObject ownedSnapshot() {
		var value = PhotonFrameCapture.snapshot();
		if (!value.has("label") || activeLabel == null || !activeLabel.equals(value.get("label").getAsString())) {
			throw new IllegalStateException("当前帧采集不属于此模型场景。");
		}
		return value;
	}

	private void validateCapture() {
		validateFrames(capture);
		var state = capture.get("state").getAsString();
		if (naturalDuration) {
			int expectedPhases = singleCast ? 1 : 3;
			if (!state.equals("DURATION") || capture.get("elapsed_seconds").getAsDouble() + 0.001 < requestedSeconds
					|| capture.getAsJsonArray("phases").size() != expectedPhases
					|| capture.get("current_phase_id").getAsInt() != expectedPhases) {
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
		if (prewarmLabel.equals(activeLabel)) {
			cancelOwnedCapture();
			return;
		}

		if (!capturing) {
			return;
		}

		capture = stopOwnedCapture();
		measuredSubmissions = endOwnedSubmissions();
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

		report.add("setup_prewarm_capture", prewarm == null ? JsonNull.INSTANCE : prewarm);
		archive(context, capture, label + "-frames.json");
	}

	private void cancelOwnedCapture() {
		if (activeLabel == null) {
			return;
		}

		var value = ownedSnapshot();
		if (value.get("state").getAsString().equals("RUNNING")) {
			PhotonFrameCapture.cancel();
			value = ownedSnapshot();
		}

		if (activeLabel.equals(prewarmLabel)) {
			prewarm = value;
			annotatePrewarm();
		} else {
			capture = value;
		}
		activeLabel = null;
		capturing = false;
	}

	/// 失败／取消也登记独立 summary；没有保存的原始帧明确留空，不伪造正常结束。
	public void close(BenchClientContext context) {
		RuntimeException failure = null;
		try {
			close();
		} catch (RuntimeException problem) {
			failure = problem;
		}

		if (prewarm != null && !prewarmFinished) {
			try {
				prewarm.addProperty("completed_before_measure", false);
				context.artifacts().write(label + "-prewarm-aborted-summary.json", "application/json", prewarm.toString());
				if (prewarm.has("saved_path") && !prewarm.get("saved_path").isJsonNull()) {
					archive(context, prewarm, label + "-prewarm-aborted-frames.json");
				}
			} catch (Exception problem) {
				if (failure == null) {
					failure = new IllegalStateException("预热失败状态归档未完成。", problem);
				} else {
					failure.addSuppressed(problem);
				}
			}
		}

		if (failure != null) {
			throw failure;
		}
	}

	public void close() {
		try {
			cancelOwnedCapture();
		} finally {
			try {
				endOwnedSubmissions();
			} finally {
				PhotonBenchConditions.end();
			}
		}
	}
}
