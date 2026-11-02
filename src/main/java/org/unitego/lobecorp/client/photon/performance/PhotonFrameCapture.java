package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.TimerQuery;
import com.sun.management.ThreadMXBean;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceReportUtil.Frames;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.regex.Pattern;

/// 显式启动的有界渲染线程采样；不擅自改变配置、窗口焦点或相机，阶段回调由调用方提供。
public class PhotonFrameCapture {
	private static final int MAX_FRAMES = 131072;
	private static final int MAX_PHASES = 64;
	private static final double MIN_SECONDS = 10;
	private static final double MAX_SECONDS = 120;
	private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;
	private static final double NANOSECONDS_PER_MILLISECOND = 1_000_000d;
	private static final long UNAVAILABLE = -1L;
	private static final int MAX_ERROR_CHARACTERS = 512;
	private static final int FOCUSED = 1;
	private static final int MAXIMIZED = 2;
	private static final int ICONIFIED = 4;
	private static final int FULLSCREEN = 8;
	private static final int VSYNC = 16;
	private static final String OUTPUT_DIRECTORY = "performance";
	private static final String INVALID_LABEL = "Capture label must match [a-z0-9_]+";
	private static final String INVALID_DURATION = "Capture duration must be finite and between 10 and 120 seconds";
	private static final String INVALID_PHASE = "Phase time must be finite, nonnegative and within the active capture duration";
	private static final String NO_ACTIVE_CAPTURE = "No active frame capture";
	private static final String EXPIRED_CAPTURE = "Frame capture has expired";
	private static final String PHASE_LIMIT = "Frame capture supports at most 64 phases";
	private static final String ALLOCATION_UNAVAILABLE = "Thread allocation tracking unsupported or disabled";
	private static final String RENDER_FAILED = "Original GameRenderer.render did not complete";
	private static final String GPU_SCOPE = "GameRenderer.render only; excludes ticks, extraction, presentation, swap and limiter";
	private static final String INTERVAL_SCOPE = "Adjacent GameRenderer.render HEAD timestamps; includes intervening ticks, extraction, swap and limiter";
	private static final String MINECRAFT_SCOPE = "Previous Minecraft.getFrameTimeNs value; excludes swap and limiter";
	private static final String PHASE_SCOPE = "interval_phase_id belongs to the preceding render HEAD; phase_id labels the current render body";
	private static final String ALLOCATION_SCOPE = "Render-thread allocation deltas between adjacent render HEADs, including capture overhead";
	private static final String GPU_BUSY = "Global TimerQuery busy; no completed capture GPU query";
	private static final String GPU_NO_RESULT = "No completed capture GPU query";
	private static final Gson GSON = new Gson();
	private static final Pattern LABEL = Pattern.compile("[a-z0-9_]+");
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
			.withZone(ZoneOffset.UTC);
	private static @Nullable Capture active;
	private static @Nullable JsonObject lastSnapshot;

	private enum State {
		IDLE, RUNNING, DURATION, CAPACITY, STOPPED, CANCELLED, ERROR
	}

	public static double minSeconds() {
		return MIN_SECONDS;
	}

	public static double maxSeconds() {
		return MAX_SECONDS;
	}

	/// 供同线程采集适配器对齐结束边界，不创建状态快照。
	public static boolean running() {
		return active != null;
	}

	public static void start(String label, double seconds) {
		RenderSystem.assertOnRenderThread();

		if (label == null || !LABEL.matcher(label).matches()) {
			throw new IllegalArgumentException(INVALID_LABEL);
		}

		if (!Double.isFinite(seconds) || seconds < MIN_SECONDS || seconds > MAX_SECONDS) {
			throw new IllegalArgumentException(INVALID_DURATION);
		}

		cancel();
		active = new Capture(label, seconds);
	}

	/// 按累计时间与登记顺序在渲染线程执行一次；回调不由采样器决定特效生命周期。
	public static void phase(String label, double atSeconds, Runnable action) {
		RenderSystem.assertOnRenderThread();
		var capture = active;

		if (capture == null) {
			throw new IllegalStateException(NO_ACTIVE_CAPTURE);
		}

		if (label == null || !LABEL.matcher(label).matches()) {
			throw new IllegalArgumentException(INVALID_LABEL);
		}

		if (!Double.isFinite(atSeconds) || atSeconds < 0 || atSeconds > capture.requestedSeconds || action == null) {
			throw new IllegalArgumentException(INVALID_PHASE);
		}

		if (capture.state != State.RUNNING || capture.startedNs != 0 && System.nanoTime() >= capture.deadlineNs) {
			throw new IllegalStateException(EXPIRED_CAPTURE);
		}

		if (capture.phaseCount == MAX_PHASES) {
			throw new IllegalStateException(PHASE_LIMIT);
		}

		var phase = new Phase(label, atSeconds, capture.phaseCount + 1, action);
		int index = capture.phaseCount++;

		while (index > 0 && capture.phases[index - 1].atSeconds > atSeconds) {
			capture.phases[index] = capture.phases[index - 1];
			index--;
		}

		capture.phases[index] = phase;
	}

	public static JsonObject liveStatus() {
		var capture = active;
		if (capture == null) {
			return lastSnapshot == null ? new JsonObject() : lastSnapshot.deepCopy();
		}
		var value = new JsonObject();
		value.addProperty("state", capture.state.name());
		value.addProperty("frames", capture.frames);
		value.addProperty("started_ns", capture.startedNs);
		value.addProperty("elapsed_seconds", capture.startedNs == 0 ? 0
				: (System.nanoTime() - capture.startedNs) / (double) NANOSECONDS_PER_SECOND);
		int last = capture.frames - 1;
		if (last >= 0) {
			value.addProperty("latest_interval_ns", capture.intervalsNs[last]);
			value.addProperty("latest_render_cpu_ns", capture.renderCpuNs[last]);
			value.addProperty("latest_render_gpu_ns", capture.renderGpuNs[last]);
		}
		return value;
	}

	/// 只读借用当前帧数组；UI 在渲染线程取视图，后台任务仅接收已冻结的数据。
	public static @Nullable Frames liveFrames() {
		RenderSystem.assertOnRenderThread();
		var capture = active;
		return capture == null ? null : capture.framesSnapshot(liveStatus());
	}

	public static JsonObject snapshot() {
		RenderSystem.assertOnRenderThread();
		var capture = active;

		if (capture != null) {
			return capture.summary();
		}

		if (lastSnapshot != null) {
			return lastSnapshot.deepCopy();
		}

		var result = new JsonObject();
		result.addProperty("state", State.IDLE.name());
		return result;
	}

	public static void stop() {
		RenderSystem.assertOnRenderThread();
		var capture = active;

		if (capture != null) {
			completeSafely(capture, State.STOPPED, true);
		}
	}

	public static void cancel() {
		RenderSystem.assertOnRenderThread();
		var capture = active;

		if (capture != null) {
			completeSafely(capture, State.CANCELLED, false);
		}
	}

	/// 未采样时只读取空状态；帧间隔取相邻入口，不能用 render 方法耗时代替。
	public static void beginFrame() {
		var capture = active;

		if (capture != null) {
			try {
				capture.beginFrame();
			} catch (Throwable failure) {
				failSafely(capture, failure);
			}
		}
	}

	public static void endFrame(boolean rendered) {
		var capture = active;

		if (capture != null) {
			try {
				capture.endFrame();

				if (!rendered) {
					capture.problem = RENDER_FAILED;
					completeSafely(capture, State.ERROR, false);
				}
			} catch (Throwable failure) {
				failSafely(capture, failure);
			}
		}
	}

	private static void completeSafely(Capture capture, State state, boolean save) {
		try {
			capture.finishedNs = System.nanoTime();
			capture.state = state;
			capture.clearActions();
			capture.pollGpu();
			capture.releaseGpu();

			if (active == capture) {
				active = null;
			}

			capture.poolEnd = poolSnapshot();

			var detailed = PhotonPerformanceCapture.active() ? capture.framesSnapshot() : null;
			boolean managed = detailed != null && PhotonPerformanceCapture.complete(detailed, save);
			if (save && !managed) {
				capture.save();
			}

			lastSnapshot = managed ? detailed.summary() : capture.summary();
		} catch (Throwable failure) {
			failSafely(capture, failure);
		}
	}

	/// 采样失败不得覆盖原 render 异常，清理也不向原绘制链抛出。
	private static void failSafely(Capture capture, Throwable failure) {
		try {
			PhotonPerformanceCapture.abort(describe(failure));
			capture.state = State.ERROR;
			capture.finishedNs = System.nanoTime();
			capture.clearActions();
			capture.releaseGpu();
			capture.problem = describe(failure);
			lastSnapshot = capture.summary();
		} catch (Throwable ignored) {
			// 保留原绘制链；设备或内存已不可用时不再分配诊断对象。
		} finally {
			if (active == capture) {
				active = null;
			}
		}
	}

	private static String describe(Throwable failure) {
		var value = failure.getClass().getSimpleName() + ": " + failure.getMessage();
		return value.substring(0, Math.min(value.length(), MAX_ERROR_CHARACTERS));
	}

	private static JsonObject poolSnapshot() {
		var result = new JsonObject();
		result.addProperty("available", false);
		result.addProperty("reason", "custom_vertex_pool_removed");
		return result;
	}

	private static JsonObject hardwareSnapshot() {
		var result = new JsonObject();

		try {
			var device = RenderSystem.getDevice();
			result.addProperty("vendor", device.getVendor());
			result.addProperty("renderer", device.getRenderer());
			result.addProperty("backend", device.getBackendName());
			result.addProperty("version", device.getVersion());
			result.addProperty("implementation", device.getImplementationInformation());
		} catch (RuntimeException | LinkageError failure) {
			result.addProperty("unavailable", describe(failure));
		}

		return result;
	}

	private static JsonObject statistics(long[] source, int length, double divisor) {
		var values = new long[length];
		int count = 0;
		double sum = 0;

		for (int index = 0; index < length; index++) {
			if (source[index] >= 0) {
				values[count++] = source[index];
				sum += source[index];
			}
		}

		var result = new JsonObject();
		result.addProperty("count", count);

		if (count == 0) {
			for (var key : new String[]{"mean", "p50", "p95", "p99", "max"}) {
				result.add(key, JsonNull.INSTANCE);
			}

			return result;
		}

		Arrays.sort(values, 0, count);
		result.addProperty("mean", sum / count / divisor);
		result.addProperty("p50", percentile(values, count, 0.50) / divisor);
		result.addProperty("p95", percentile(values, count, 0.95) / divisor);
		result.addProperty("p99", percentile(values, count, 0.99) / divisor);
		result.addProperty("max", values[count - 1] / divisor);
		return result;
	}

	private static long percentile(long[] values, int count, double percentile) {
		return values[(int) Math.ceil(count * percentile) - 1];
	}

	private static class Phase {
		private final String label;
		private final double atSeconds;
		private final int id;
		private @Nullable Runnable action;
		private long triggeredNs = UNAVAILABLE;
		private @Nullable String problem;

		private Phase(String label, double atSeconds, int id, Runnable action) {
			this.label = label;
			this.atSeconds = atSeconds;
			this.id = id;
			this.action = action;
		}
	}

	private static class Capture {
		private final String label;
		private final double requestedSeconds;
		private long startedNs;
		private Instant startedAt = Instant.now();
		private long deadlineNs;
		private final long threadId = Thread.currentThread().threadId();
		private final JsonObject hardware = hardwareSnapshot();
		private final JsonObject poolStart = poolSnapshot();
		private final boolean vsync = Minecraft.getInstance().options.enableVsync().get();
		private final int configuredLimit = Minecraft.getInstance().options.framerateLimit().get();
		private final long[] frameHeadNs = new long[MAX_FRAMES];
		private final long[] intervalsNs = new long[MAX_FRAMES];
		private final long[] minecraftNs = new long[MAX_FRAMES];
		private final long[] renderCpuNs = new long[MAX_FRAMES];
		private final long[] renderGpuNs = new long[MAX_FRAMES];
		private final long[] allocatedBytes = new long[MAX_FRAMES];
		private final int[] framebufferWidth = new int[MAX_FRAMES];
		private final int[] framebufferHeight = new int[MAX_FRAMES];
		private final int[] windowWidth = new int[MAX_FRAMES];
		private final int[] windowHeight = new int[MAX_FRAMES];
		private final int[] limits = new int[MAX_FRAMES];
		private final int[] flags = new int[MAX_FRAMES];
		private final int[] phaseIds = new int[MAX_FRAMES];
		private final int[] intervalPhaseIds = new int[MAX_FRAMES];
		private final Phase[] phases = new Phase[MAX_PHASES];
		private final String[] throttleReasons = new String[MAX_FRAMES];
		private @Nullable ThreadMXBean allocationBean;
		private @Nullable String allocationProblem;
		private @Nullable TimerQuery.FrameProfile pendingGpu;
		private @Nullable String gpuProblem;
		private @Nullable JsonObject poolEnd;
		private @Nullable Path savedPath;
		private @Nullable String problem;
		private State state = State.RUNNING;
		private long finishedNs;
		private long previousHeadNs;
		private long previousAllocated = UNAVAILABLE;
		private long renderStartedNs;
		private int frames;
		private int phaseCount;
		private int currentPhase;
		private int previousPhase;
		private int depth;
		private int pendingGpuFrame;
		private int gpuStarted;
		private int gpuCompleted;
		private int gpuSkippedBusy;
		private int gpuSkippedPending;
		private int gpuCancelled;
		private int gpuErrors;
		private boolean gpuRecording;

		private Capture(String label, double seconds) {
			this.label = label;
			requestedSeconds = seconds;
			Arrays.fill(intervalsNs, UNAVAILABLE);
			Arrays.fill(renderCpuNs, UNAVAILABLE);
			Arrays.fill(renderGpuNs, UNAVAILABLE);
			Arrays.fill(allocatedBytes, UNAVAILABLE);

			try {
				if (ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean && bean.isThreadAllocatedMemorySupported()
						&& bean.isThreadAllocatedMemoryEnabled()) {
					allocationBean = bean;
				} else {
					allocationProblem = ALLOCATION_UNAVAILABLE;
				}
			} catch (RuntimeException | LinkageError failure) {
				allocationProblem = describe(failure);
			}
		}

		private void beginFrame() {
			if (++depth != 1) {
				return;
			}

			var now = System.nanoTime();

			if (startedNs == 0) {
				startedNs = now;
				startedAt = Instant.now();
				deadlineNs = startedNs + (long) (requestedSeconds * NANOSECONDS_PER_SECOND);
                if (FMLEnvironment.isProduction()) {
                    Minecraft.getInstance().getFramerateLimitTracker().onInputReceived();
                }
			}

			// 在递增 frames 前冻结，容量结束不追加未填充的零值帧，也不再执行后续采样阶段。
			if (frames == MAX_FRAMES || PhotonPerformanceCapture.active() && PhotonPerformanceTraceUtil.limit() != null) {
				completeSafely(this, State.CAPACITY, true);
				return;
			}

			boolean endpoint = now >= deadlineNs;
			var allocated = endpoint ? UNAVAILABLE : allocatedNow();
			pollGpu();
			if (!endpoint) {
				runPhases(now);
			}

			if (active != this) {
				return;
			}

			boolean collectorLimit = PhotonPerformanceCapture.active() && PhotonPerformanceTraceUtil.limit() != null;
			if (collectorLimit) {
				completeSafely(this, State.CAPACITY, true);
				return;
			}

			int index = frames++;
			if (endpoint) {
				PhotonPerformanceTraceUtil.frame(index, startedNs);
			} else {
				PhotonPerformanceCapture.frame(index, startedNs);
			}
			if (active != this) {
				return;
			}
			frameHeadNs[index] = now - startedNs;
			phaseIds[index] = currentPhase;
			intervalPhaseIds[index] = previousPhase;
			previousPhase = currentPhase;

			if (previousHeadNs != 0) {
				intervalsNs[index] = now - previousHeadNs;
			}

			previousHeadNs = now;
			var minecraft = Minecraft.getInstance();
			minecraftNs[index] = minecraft.getFrameTimeNs();
			var window = minecraft.getWindow();
			framebufferWidth[index] = window.getWidth();
			framebufferHeight[index] = window.getHeight();
			windowWidth[index] = window.getScreenWidth();
			windowHeight[index] = window.getScreenHeight();
			limits[index] = minecraft.gameRenderer.getGameRenderState().framerateLimit;
			throttleReasons[index] = minecraft.getFramerateLimitTracker().getThrottleReason().name();
			flags[index] = (window.isFocused() ? FOCUSED : 0)
					| (GLFW.glfwGetWindowAttrib(window.handle(), GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE ? MAXIMIZED : 0)
					| (window.isIconified() ? ICONIFIED : 0) | (window.isFullscreen() ? FULLSCREEN : 0)
					| (minecraft.options.enableVsync().get() ? VSYNC : 0);

			// 终点仅闭合相邻 HEAD 间隔；未执行的 body、GPU 和分配采样保留不可用值。
			if (endpoint) {
				completeSafely(this, State.DURATION, true);
				return;
			}

			if (allocated >= 0 && previousAllocated >= 0) {
				allocatedBytes[index] = allocated - previousAllocated;
			}

			previousAllocated = allocated;
			beginGpu();
			renderStartedNs = System.nanoTime();
		}

		private void endFrame() {
			if (depth == 0 || --depth != 0) {
				return;
			}

			renderCpuNs[frames - 1] = System.nanoTime() - renderStartedNs;

			if (gpuRecording) {
				try {
					pendingGpu = TimerQuery.getInstance().endProfile();
					pendingGpuFrame = frames - 1;
				} catch (RuntimeException | LinkageError failure) {
					gpuFailure(failure);
					releaseGpu();
				} finally {
					gpuRecording = false;
				}
			}
		}

		private void runPhases(long now) {
			for (int index = 0; index < phaseCount; index++) {
				var phase = phases[index];

				if (phase.atSeconds * NANOSECONDS_PER_SECOND > now - startedNs) {
					break;
				}

				var action = phase.action;

				if (action == null) {
					continue;
				}

				phase.action = null;
				phase.triggeredNs = System.nanoTime() - startedNs;
				currentPhase = phase.id;

				try {
					action.run();
				} catch (Throwable failure) {
					phase.problem = describe(failure);
					failSafely(this, failure);
					return;
				}

				if (active != this) {
					return;
				}
			}
		}

		private void clearActions() {
			for (int index = 0; index < phaseCount; index++) {
				phases[index].action = null;
			}
		}

		private JsonArray phaseSnapshot() {
			var result = new JsonArray();

			for (int index = 0; index < phaseCount; index++) {
				var phase = phases[index];
				var value = new JsonObject();
				value.addProperty("id", phase.id);
				value.addProperty("label", phase.label);
				value.addProperty("scheduled_seconds", phase.atSeconds);

				if (phase.triggeredNs >= 0) {
					value.addProperty("triggered_seconds", phase.triggeredNs / (double) NANOSECONDS_PER_SECOND);
				} else {
					value.add("triggered_seconds", JsonNull.INSTANCE);
				}

				value.addProperty("error", phase.problem);
				result.add(value);
			}

			return result;
		}

		private long allocatedNow() {
			if (allocationBean == null) {
				return UNAVAILABLE;
			}

			try {
				return allocationBean.getThreadAllocatedBytes(threadId);
			} catch (RuntimeException | LinkageError failure) {
				allocationProblem = describe(failure);
				allocationBean = null;
				return UNAVAILABLE;
			}
		}

		private void beginGpu() {
			if (gpuProblem != null) {
				return;
			}

			if (pendingGpu != null) {
				gpuSkippedPending++;
				return;
			}

			try {
				var timer = TimerQuery.getInstance();

				if (timer.isRecording()) {
					gpuSkippedBusy++;
					return;
				}

				timer.beginProfile();
				gpuRecording = true;
				gpuStarted++;
			} catch (RuntimeException | LinkageError failure) {
				gpuFailure(failure);
			}
		}

		private void pollGpu() {
			if (pendingGpu == null) {
				return;
			}

			try {
				if (pendingGpu.isDone()) {
					renderGpuNs[pendingGpuFrame] = pendingGpu.get();
					gpuCompleted++;
					pendingGpu = null;
				}
			} catch (RuntimeException | LinkageError failure) {
				gpuFailure(failure);
				releaseGpu();
			}
		}

		private void gpuFailure(Throwable failure) {
			gpuErrors++;

			if (gpuProblem == null) {
				gpuProblem = describe(failure);
			}
		}

		private void releaseGpu() {
			if (gpuRecording) {
				try {
					TimerQuery.getInstance().endProfile().cancel();
					gpuCancelled++;
				} catch (RuntimeException | LinkageError failure) {
					gpuFailure(failure);
				} finally {
					gpuRecording = false;
				}
			}

			if (pendingGpu != null) {
				try {
					pendingGpu.cancel();
					gpuCancelled++;
				} catch (RuntimeException | LinkageError failure) {
					gpuFailure(failure);
				} finally {
					pendingGpu = null;
				}
			}
		}

		private JsonObject environment(int index) {
			var result = new JsonObject();

			if (index < 0) {
				return result;
			}

			result.addProperty("focused", (flags[index] & FOCUSED) != 0);
			result.addProperty("maximized", (flags[index] & MAXIMIZED) != 0);
			result.addProperty("iconified", (flags[index] & ICONIFIED) != 0);
			result.addProperty("fullscreen", (flags[index] & FULLSCREEN) != 0);
			result.addProperty("vsync", (flags[index] & VSYNC) != 0);
			result.addProperty("framebuffer_width", framebufferWidth[index]);
			result.addProperty("framebuffer_height", framebufferHeight[index]);
			result.addProperty("window_width", windowWidth[index]);
			result.addProperty("window_height", windowHeight[index]);
			result.addProperty("actual_framerate_limit", limits[index]);
			result.addProperty("throttle_reason", throttleReasons[index]);
			return result;
		}

		private JsonObject summary() {
			var result = new JsonObject();
			result.addProperty("schema_version", 1);
			result.addProperty("state", state.name());
			result.addProperty("label", label);
			result.addProperty("started_at", startedAt.toString());
			result.addProperty("started_ns", startedNs);
			result.addProperty("requested_seconds", requestedSeconds);
			result.addProperty("elapsed_seconds", startedNs == 0 ? 0
					: ((finishedNs == 0 ? System.nanoTime() : finishedNs) - startedNs) / (double) NANOSECONDS_PER_SECOND);
			result.addProperty("frame_count", frames);
			result.addProperty("frame_capacity", MAX_FRAMES);
			result.addProperty("vsync", vsync);
			result.addProperty("configured_framerate_limit", configuredLimit);
			result.addProperty("configuration_changed_by_capture", false);
			result.addProperty("current_phase_id", currentPhase);
			result.add("phases", phaseSnapshot());
			result.addProperty("interval_phase_scope", PHASE_SCOPE);
			result.addProperty("gpu_scope", GPU_SCOPE);
			result.addProperty("frame_interval_scope", INTERVAL_SCOPE);
			result.addProperty("previous_minecraft_scope", MINECRAFT_SCOPE);
			var intervals = statistics(intervalsNs, frames, NANOSECONDS_PER_MILLISECOND);
			result.add("frame_interval_ms", intervals);

			if (intervals.get("count").getAsInt() > 0 && intervals.get("mean").getAsDouble() > 0) {
				result.addProperty("mean_fps", 1000d / intervals.get("mean").getAsDouble());
			} else {
				result.add("mean_fps", JsonNull.INSTANCE);
			}

			result.add("previous_minecraft_frame_ms", statistics(minecraftNs, frames, NANOSECONDS_PER_MILLISECOND));
			result.add("render_cpu_ms", statistics(renderCpuNs, frames, NANOSECONDS_PER_MILLISECOND));
			result.add("render_gpu_ms", statistics(renderGpuNs, frames, NANOSECONDS_PER_MILLISECOND));
			result.add("render_thread_allocated_bytes", statistics(allocatedBytes, frames, 1d));
			result.addProperty("allocation_scope", ALLOCATION_SCOPE);
			result.addProperty("allocation_unavailable", allocationProblem);
			result.addProperty("gpu_unavailable", gpuProblem == null && gpuCompleted == 0
					? gpuSkippedBusy > 0 ? GPU_BUSY : GPU_NO_RESULT : gpuProblem);
			result.addProperty("gpu_started", gpuStarted);
			result.addProperty("gpu_completed", gpuCompleted);
			result.addProperty("gpu_skipped_busy", gpuSkippedBusy);
			result.addProperty("gpu_skipped_pending", gpuSkippedPending);
			result.addProperty("gpu_cancelled", gpuCancelled);
			result.addProperty("gpu_errors", gpuErrors);
			result.addProperty("gpu_pending", pendingGpu != null || gpuRecording);
			result.addProperty("gpu_available", gpuCompleted > 0 && gpuProblem == null);
			result.add("hardware", hardware.deepCopy());
			result.add("environment_first", environment(frames == 0 ? -1 : 0));
			result.add("environment_last", environment(frames - 1));
			result.add("pool_start", poolStart.deepCopy());
			result.add("pool_current", poolEnd == null ? poolSnapshot() : poolEnd.deepCopy());
			result.addProperty("saved_path", savedPath == null ? null : savedPath.toString());
			result.addProperty("error", problem);
			return result;
		}

		private Frames framesSnapshot() {
			return framesSnapshot(summary());
		}

		private Frames framesSnapshot(JsonObject summary) {
			return new Frames(summary, frames, startedNs, frameHeadNs, intervalsNs, minecraftNs, renderCpuNs, renderGpuNs,
					allocatedBytes, framebufferWidth, framebufferHeight, flags, phaseIds, intervalPhaseIds, limits, throttleReasons);
		}

		private void save() throws IOException {
			var directory = Minecraft.getInstance().gameDirectory.toPath().resolve(OUTPUT_DIRECTORY);
			Files.createDirectories(directory);
			var path = directory.resolve(label + "-" + FILE_TIME.format(Instant.now()) + "-" + System.nanoTime() + ".json");
			savedPath = path.toAbsolutePath().normalize();

			try (var writer = new JsonWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8,
					StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
				writer.beginObject();
				writer.name("summary");
				GSON.toJson(summary(), writer);
				writer.name("flag_bits").beginObject();
				writer.name("focused").value(FOCUSED);
				writer.name("maximized").value(MAXIMIZED);
				writer.name("iconified").value(ICONIFIED);
				writer.name("fullscreen").value(FULLSCREEN);
				writer.name("vsync").value(VSYNC);
				writer.endObject();
				writer.name("frames").beginObject();
				write(writer, "head_offset_ns", frameHeadNs);
				write(writer, "interval_ns", intervalsNs);
				write(writer, "previous_minecraft_ns", minecraftNs);
				write(writer, "render_cpu_ns", renderCpuNs);
				write(writer, "render_gpu_ns", renderGpuNs);
				write(writer, "render_thread_allocated_bytes", allocatedBytes);
				write(writer, "framebuffer_width", framebufferWidth);
				write(writer, "framebuffer_height", framebufferHeight);
				write(writer, "window_width", windowWidth);
				write(writer, "window_height", windowHeight);
				write(writer, "actual_framerate_limit", limits);
				write(writer, "flags", flags);
				write(writer, "phase_id", phaseIds);
				write(writer, "interval_phase_id", intervalPhaseIds);
				writer.name("throttle_reason").beginArray();

				for (int index = 0; index < frames; index++) {
					writer.value(throttleReasons[index]);
				}

				writer.endArray().endObject().endObject();
			} catch (IOException | RuntimeException failure) {
				savedPath = null;
				throw failure;
			}
		}

		private void write(JsonWriter writer, String name, long[] values) throws IOException {
			writer.name(name).beginArray();

			for (int index = 0; index < frames; index++) {
				writer.value(values[index]);
			}

			writer.endArray();
		}

		private void write(JsonWriter writer, String name, int[] values) throws IOException {
			writer.name(name).beginArray();

			for (int index = 0; index < frames; index++) {
				writer.value(values[index]);
			}

			writer.endArray();
		}
	}
}
