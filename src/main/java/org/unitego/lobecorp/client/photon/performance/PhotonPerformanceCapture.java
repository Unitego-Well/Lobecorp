package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceWindowUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOperationUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTextUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceReportUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceReportUtil.Frames;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mode;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.View;

import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.List;

/// 将逐对象记录与既有整帧采样共用生命周期；冻结后单个后台任务导出。
public class PhotonPerformanceCapture {
	public static final String COMMAND_NAME = "photon_profile";
	public static final double DEFAULT_SECONDS = 40;
	public static final String WORLD_LABEL = "manual";
	public static final String EDITOR_LABEL = "editor";
	private static long ownedSessionId;
	// 面板视图刷新间隔，避免每帧重建统计。
	public static final long VIEW_REFRESH_NS = 250_000_000L;
	private static long lastViewNs;
	private static final String OUTPUT_DIRECTORY = "performance";
	private static final Gson GSON = new Gson();
	private static boolean managed;
	private static WeakReference<ClientLevel> level = new WeakReference<>(null);
	private static JsonObject environment = new JsonObject();
	private static @Nullable View lastView;
	private static JsonObject lastFrames = new JsonObject();
	private static @Nullable Snapshot frozen;

	public record Snapshot(PhotonPerformanceTraceUtil.Capture capture, Frames frames,
	                       List<JsonObject> gpuSamples, JsonObject metadata) {
	}

	private static volatile boolean exporting;
	private static volatile @Nullable Path output;
	private static volatile @Nullable String failure;
	private static volatile long exportNs;

	public static void start(String label, double seconds, Mode mode) {
		RenderSystem.assertOnRenderThread();
		if (!(Minecraft.getInstance().gameRenderer instanceof IPhotonPerformanceInstrumentation)) {
			throw new IllegalStateException(PhotonPerformanceTextUtil.key("restart_required"));
		}
		if (managed || PhotonPerformanceTraceUtil.active()) {
			throw new IllegalStateException("performance_capture_already_active");
		}
		if (exporting) {
			throw new IllegalStateException("performance_export_in_progress");
		}

		long setup = System.nanoTime();
		PhotonFrameCapture.start(label, seconds);
		try {
			PhotonPerformanceTraceUtil.start(label, mode);
			ownedSessionId = PhotonPerformanceTraceUtil.sessionId();
			PhotonPerformanceTraceUtil.counter(0, PhotonPerformanceTraceUtil.Metric.REQUESTS, 0);
			if (mode == Mode.GPU) {
				PhotonPerformanceGpu.start();
			} else {
				PhotonPerformanceGpu.resetStatus();
			}
			managed = true;
			PhotonPerformanceOperationUtil.clear();
			lastView = null;
			lastFrames = new JsonObject();
			frozen = null;
			output = null;
			failure = null;
			level = new WeakReference<>(Minecraft.getInstance().level);
			environment = environment();
			PhotonPerformanceTraceUtil.mark(Stage.PREWARM, 0, "collector_setup_ns", "diagnostic_setup_not_effect_prewarm",
					System.nanoTime() - setup, 0);
		} catch (RuntimeException | LinkageError problem) {
			managed = false;
			ownedSessionId = 0;
			PhotonPerformanceGpu.finish();
			PhotonPerformanceTraceUtil.finish("start_failed");
			managed = false;
			PhotonFrameCapture.cancel();
			throw problem;
		}
	}

	public static void stop() {
		RenderSystem.assertOnRenderThread();
		if (managed) {
			PhotonFrameCapture.stop();
		}
	}

	public static void cancel() {
		RenderSystem.assertOnRenderThread();
		if (managed) {
			PhotonFrameCapture.cancel();
		}
	}

	public static void abort(String problem) {
		if (ownedSessionId == 0) {
			return;
		}
		long owned = ownedSessionId;
		ownedSessionId = 0;
		managed = false;
		failure = problem;
		try {
			PhotonPerformanceGpu.finish();
		} finally {
			PhotonPerformanceOperationUtil.clear();
			PhotonPerformanceOwnerUtil.clearJobs();
			if (PhotonPerformanceTraceUtil.sessionId() == owned) {
				var capture = PhotonPerformanceTraceUtil.finish("capture_error");
				if (capture != null) {
					lastView = capture.view();
				}
			}
		}
	}

	public static boolean active() {
		return managed;
	}

	public static void frame(int index, long originNs) {
		if (!managed) {
			return;
		}
		PhotonPerformanceTraceUtil.frame(index, originNs);
		PhotonPerformanceGpu.frame(index);
		PhotonPerformanceOwnerUtil.clearJobs();
		if (Minecraft.getInstance().level != level.get()) {
			PhotonPerformanceTraceUtil.mark(Stage.CLEANUP, 0, "world_changed", "capture_cancelled", 0, 0);
			PhotonFrameCapture.cancel();
		}
	}

	public static boolean complete(Frames frames, boolean save) {
		if (!managed) {
			return false;
		}
		managed = false;
		var gpuSamples = PhotonPerformanceGpu.finish();
		var commandSummary = PhotonPerformanceOperationUtil.summary();
		PhotonPerformanceOperationUtil.clear();
		var capture = PhotonPerformanceTraceUtil.finish(frames.summary().get("state").getAsString());
		ownedSessionId = 0;
		PhotonPerformanceOwnerUtil.clearJobs();
		if (capture == null) {
			failure = "missing_object_capture";
			return true;
		}
		lastView = capture.view();
		lastFrames = frames.summary().deepCopy();
		var metadata = environment.deepCopy();
		metadata.add("gpu_query_status", PhotonPerformanceGpu.status());
		metadata.add("command_correlation", GSON.toJsonTree(commandSummary));
		frozen = new Snapshot(capture, frames, gpuSamples, metadata);
		if (!save) {
			return true;
		}

		var directory = Minecraft.getInstance().gameDirectory.toPath().resolve(OUTPUT_DIRECTORY)
				.resolve(capture.view().label() + "-" + capture.view().sessionId() + "-" + System.nanoTime());
		exporting = true;
		try {
			Thread.ofVirtual().name("lobecorp-photon-profile-export").start(() -> {
				long started = System.nanoTime();
				try {
					output = PhotonPerformanceReportUtil.write(directory, capture, frames, metadata, gpuSamples);
				} catch (Exception problem) {
					failure = problem.getClass().getSimpleName() + ": " + problem.getMessage();
				} finally {
					exportNs = System.nanoTime() - started;
					exporting = false;
				}
			});
		} catch (RuntimeException | Error problem) {
			exporting = false;
			failure = "export_start_failed: " + problem.getClass().getSimpleName();
			throw problem;
		}
		return true;
	}

	/// 导出冻结区间；与整会话导出共用忙碌状态，避免无界并发文件任务。
	public static boolean exportWindow(PhotonPerformanceWindowUtil.Result result) {
		var snapshot = frozen;
		if (managed || exporting || snapshot == null || snapshot.capture().view().sessionId() != result.sessionId()) {
			return false;
		}
		var directory = Minecraft.getInstance().gameDirectory.toPath().resolve(OUTPUT_DIRECTORY)
				.resolve(snapshot.capture().view().label() + "-window-" + result.sessionId() + "-" + System.nanoTime());
		exporting = true;
		failure = null;
		try {
			Thread.ofVirtual().name("lobecorp-photon-window-export").start(() -> {
				long started = System.nanoTime();
				try {
					output = PhotonPerformanceReportUtil.writeWindow(directory, snapshot.capture(), snapshot.metadata(), result);
				} catch (Exception problem) {
					failure = problem.getClass().getSimpleName() + ": " + problem.getMessage();
				} finally {
					exportNs = System.nanoTime() - started;
					exporting = false;
				}
			});
		} catch (RuntimeException | Error problem) {
			exporting = false;
			failure = "window_export_start_failed: " + problem.getClass().getSimpleName();
			throw problem;
		}
		return true;
	}

	public static @Nullable Snapshot finished() {
		return frozen;
	}

	public static @Nullable Frames frames() {
		return managed ? PhotonFrameCapture.liveFrames() : frozen == null ? null : frozen.frames();
	}

	public static @Nullable View view() {
		if (managed && (lastView == null || System.nanoTime() - lastViewNs >= VIEW_REFRESH_NS)) {
			lastView = PhotonPerformanceTraceUtil.view();
			lastViewNs = System.nanoTime();
		}
		return lastView;
	}

	public static JsonObject status() {
		var result = new JsonObject();
		result.addProperty("active", managed);
		result.addProperty("instrumentation_ready", Minecraft.getInstance().gameRenderer instanceof IPhotonPerformanceInstrumentation);
		result.addProperty("exporting", exporting);
		result.addProperty("output", output == null ? null : output.toString());
		result.addProperty("error", failure);
		result.addProperty("export_ns", exportNs);
		result.add("gpu_query_status", PhotonPerformanceGpu.status());
		var view = view();
		if (view != null) {
			result.addProperty("session_id", view.sessionId());
			result.addProperty("mode", view.mode().name());
			result.addProperty("nodes", view.nodes().size());
			result.addProperty("frame", view.frame());
			result.addProperty("dropped", view.dropped());
			result.addProperty("overwritten", view.overwritten());
			result.addProperty("incomplete", view.incomplete());
			result.addProperty("collector_error", view.problem());
			result.add("collector_quality", GSON.toJsonTree(view.quality()));
		}
		result.add("frame_summary", managed ? PhotonFrameCapture.liveStatus() : lastFrames.deepCopy());
		return result;
	}

	private static JsonObject environment() {
		var minecraft = Minecraft.getInstance();
		var result = new JsonObject();
		result.addProperty("java", System.getProperty("java.version"));
		result.addProperty("os", System.getProperty("os.name"));
		result.addProperty("mod_version", ModList.get().getModContainerById(Lobecorp.NAMESPACE)
				.map(container -> container.getModInfo().getVersion().toString()).orElse("unavailable"));
		result.addProperty("resource_revision", "unavailable_not_hashed");
		result.addProperty("resource_revision_comparable", false);
		result.addProperty("dimension", minecraft.level == null ? null : minecraft.level.dimension().toString());
		result.addProperty("width", minecraft.getWindow().getWidth());
		result.addProperty("height", minecraft.getWindow().getHeight());
		result.addProperty("vsync", minecraft.options.enableVsync().get());
		result.addProperty("configured_fps", minecraft.options.framerateLimit().get());
		return result;
	}
}
