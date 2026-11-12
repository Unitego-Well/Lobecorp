package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.DrawJob;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.InstancedJob;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState.Job;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// 仅显式基准用例开启的提交计数；不修改缓冲容量、顶点写入或原生作业生命周期。
public class PhotonSubmissionDiagnostics {
	/// 高频提交探针的显式对照开关；与 PhotonBenchMixinPlugin 中的同名字面量必须一致。
	public static final String PROBES_PROPERTY = "lobecorp.photon.bench.probes";
	private static @Nullable Sample active;
	private static @Nullable Submission current;
	private static @Nullable Drain currentDrain;
	private static boolean openGlDrawHookObserved;
	private static @Nullable JsonObject lastSnapshot;

	public static void beginCase(String label) {
		Objects.requireNonNull(label);

		if (active != null && active.thread != Thread.currentThread()) {
			throw new IllegalStateException("提交诊断只能由开启用例的线程更新。");
		}

		if (current != null || currentDrain != null) {
			throw new IllegalStateException("不能在提交作用域内切换诊断用例。");
		}

		active = new Sample(label);
		lastSnapshot = null;
	}

	public static JsonObject endCase() {
		var sample = active;

		if (sample == null) {
			return snapshot();
		}

		checkThread(sample);
		try {
			var result = sample.snapshot(false);
			lastSnapshot = result.deepCopy();
			return result;
		} finally {
			active = null;
			current = null;
			currentDrain = null;
		}
	}

	public static JsonObject snapshot() {
		var sample = active;

		if (sample != null) {
			checkThread(sample);
			return sample.snapshot(true);
		}

		if (lastSnapshot != null) {
			return lastSnapshot.deepCopy();
		}

		var result = new JsonObject();
		result.addProperty("enabled", false);
		result.addProperty("state", "idle");
		return result;
	}

	public static boolean enabled() {
		return active != null && active.thread == Thread.currentThread();
	}

	/// 关闭时基准 Mixin 不注入；生产路径不读取该属性，也不改变提交缓冲。
	public static boolean probesRequested() {
		return Boolean.parseBoolean(System.getProperty(PROBES_PROPERTY, "true"));
	}

	public static String probeState() {
		return probesRequested() ? "enabled" : "disabled";
	}

	public static void bindFrameCapture() {
		var sample = Objects.requireNonNull(active, "提交诊断尚未开启。");
		checkThread(sample);
		if (!PhotonFrameCapture.running()) {
			throw new IllegalStateException("帧采集尚未开启，不能绑定提交窗口。");
		}
		sample.frameCaptureBound = true;
	}

	/// 由实际渲染帧入口调用；关闭时不访问 Minecraft，也不分配相机快照。
	public static void beginFrame() {
		var sample = active;

		if (sample == null || sample.thread != Thread.currentThread()) {
			return;
		}

		if (sample.frameCaptureBound && !PhotonFrameCapture.running()) {
			endCase();
			return;
		}

		sample.finishFrame();
		sample.frames++;
		sample.frameReserve = 0;
		sample.frameResizes = 0;
		sample.frameJobs = 0;
		sample.frameDrawCalls = 0;
		sample.captureEnvironment();
	}

	public static @Nullable Drain beginDrain() {
		var sample = active;

		if (sample == null || sample.thread != Thread.currentThread()) {
			return null;
		}

		var drain = new Drain(sample, currentDrain);
		currentDrain = drain;
		sample.drainScopes++;
		return drain;
	}

	public static void endDrain(Drain drain, boolean completed) {
		drain.sample.drainNanos += System.nanoTime() - drain.started;

		if (!completed) {
			drain.sample.failedDrains++;
		}

		if (currentDrain == drain) {
			currentDrain = active == drain.sample ? drain.parent : null;
		} else {
			drain.sample.scopeProtocolErrors++;
			currentDrain = null;
		}
	}

	/// 仅在通过 OpenGL setup 后的实际 drawFromBuffers 成功返回时调用，不能用 Job 数代替。
	public static void drawDispatched(boolean indexed, int elements, int instances) {
		var sample = active;

		if (sample == null || sample.thread != Thread.currentThread()) {
			return;
		}

		if (!openGlDrawHookObserved) {
			openGlDrawHookObserved = true;
		}

		var drain = currentDrain;

		if (drain == null || drain.sample != sample) {
			return;
		}
		long count = Math.max(0, elements);
		long instanceCount = Math.max(0, instances);
		sample.drawCalls++;
		sample.frameDrawCalls++;
		sample.submittedInstances += instanceCount;

		if (instances > 1) {
			sample.instancedDrawCalls++;
		}

		if (indexed) {
			sample.indexedDrawCalls++;
			sample.submittedIndices += count;
			sample.indexInstances += count * instanceCount;
		} else {
			sample.arrayDrawCalls++;
			sample.submittedVertices += count;
			sample.vertexInstances += count * instanceCount;
		}
	}

	public static @Nullable Submission beginSubmission(boolean model) {
		var sample = active;

		if (sample == null || sample.thread != Thread.currentThread()) {
			return null;
		}

		var submission = new Submission(sample, model ? sample.model : sample.other, current);
		current = submission;
		submission.counters.cpuSubmissionCalls++;
		return submission;
	}

	public static @Nullable Submission currentSubmission() {
		var submission = current;
		return submission != null && submission.sample.thread == Thread.currentThread() ? submission : null;
	}

	public static void endSubmission(Submission submission, List<DrawJob> jobs, int start, boolean completed) {
		var sample = submission.sample;
		var counters = submission.counters;
		counters.cpuBakeNanos += System.nanoTime() - submission.started;

		if (!completed) {
			counters.failedCpuSubmissions++;
		}

		try {
			if (active == sample) {
				observeJobs(sample, counters, jobs, start);
			}
		} finally {
			if (current == submission) {
				current = active == sample ? submission.parent : null;
			} else {
				sample.scopeProtocolErrors++;
				current = null;
			}
		}
	}

	public static void observeInstancedJobs(List<DrawJob> jobs, int start, boolean model) {
		var sample = active;

		if (sample == null || sample.thread != Thread.currentThread()) {
			return;
		}

		observeJobs(sample, model ? sample.model : sample.other, jobs, start);
	}

	public static void bufferCreated(Submission submission, Object buffer, long capacity) {
		observeBuffer(submission, buffer, capacity);
		submission.counters.buffersCreated++;
		submission.counters.initialCapacityBytes += capacity;
	}

	public static void reserve(Submission submission, Object buffer, int bytes, long capacity) {
		observeBuffer(submission, buffer, capacity);
		submission.counters.reserveCalls++;
		submission.counters.requestedBytes += Math.max(0, bytes);
		submission.sample.frameReserve++;

		if (bytes < 0) {
			submission.counters.negativeReserveRequests++;
		}
	}

	public static void reserved(Submission submission, long capacity, long writeOffset, boolean completed) {
		var counters = submission.counters;
		counters.maxCapacity = Math.max(counters.maxCapacity, capacity);
		counters.maxWriteOffset = Math.max(counters.maxWriteOffset, writeOffset);

		if (!completed) {
			counters.failedReserveCalls++;
		}
	}

	public static void resized(Submission submission, Object buffer, long oldCapacity, long capacity, boolean completed) {
		observeBuffer(submission, buffer, capacity);

		if (!completed) {
			submission.counters.failedResizeCalls++;
			return;
		}

		submission.counters.resizeCalls++;
		submission.counters.growthBytes += Math.max(0, capacity - oldCapacity);
		submission.sample.frameResizes++;
	}

	private static void observeBuffer(Submission submission, Object buffer, long capacity) {
		if (submission.lastBuffer != buffer) {
			submission.lastBuffer = buffer;

			if (submission.buffers.put(buffer, Boolean.TRUE) == null) {
				submission.counters.bufferObservations++;
			}
		}

		submission.counters.maxCapacity = Math.max(submission.counters.maxCapacity, capacity);
	}

	/// 作业及其网格由 Photon 队列持有并关闭；诊断只借读顶点与索引数量。
	@SuppressWarnings("resource")
	private static void observeJobs(Sample sample, Counters counters, List<DrawJob> jobs, int start) {
		for (int index = Math.max(0, start); index < jobs.size(); index++) {
			var job = jobs.get(index);

			if (job instanceof Job cpu) {
				counters.cpuJobs++;
				counters.cpuVertices += cpu.mesh().drawState().vertexCount();
				counters.cpuIndexCount += cpu.mesh().drawState().indexCount();
			} else if (job instanceof InstancedJob instanced) {
				counters.instancedJobs++;
				counters.instances += instanced.geometry().instanceCount();
			}

			sample.frameJobs++;
		}
	}

	private static void checkThread(Sample sample) {
		if (sample.thread != Thread.currentThread()) {
			throw new IllegalStateException("提交诊断快照必须在用例线程捕获后再交给后台写入。");
		}
	}

	public static class Drain {
		private final Sample sample;
		private final @Nullable Drain parent;
		private final long started = System.nanoTime();

		private Drain(Sample sample, @Nullable Drain parent) {
			this.sample = sample;
			this.parent = parent;
		}
	}

	public static class Submission {
		private final Sample sample;
		private final Counters counters;
		private final @Nullable Submission parent;
		private final long started = System.nanoTime();
		private final Map<Object, Boolean> buffers = new IdentityHashMap<>();
		private @Nullable Object lastBuffer;

		private Submission(Sample sample, Counters counters, @Nullable Submission parent) {
			this.sample = sample;
			this.counters = counters;
			this.parent = parent;
		}
	}

	private static class Counters {
		private long cpuSubmissionCalls;
		private long failedCpuSubmissions;
		private long cpuBakeNanos;
		private long bufferObservations;
		private long buffersCreated;
		private long initialCapacityBytes;
		private long reserveCalls;
		private long requestedBytes;
		private long negativeReserveRequests;
		private long failedReserveCalls;
		private long resizeCalls;
		private long failedResizeCalls;
		private long growthBytes;
		private long maxCapacity;
		private long maxWriteOffset;
		private long cpuJobs;
		private long cpuVertices;
		private long cpuIndexCount;
		private long instancedJobs;
		private long instances;

		private JsonObject snapshot() {
			var result = new JsonObject();
			result.addProperty("cpu_submission_calls", cpuSubmissionCalls);
			result.addProperty("failed_cpu_submissions", failedCpuSubmissions);
			result.addProperty("cpu_bake_ns", cpuBakeNanos);
			result.addProperty("buffer_observations", bufferObservations);
			result.addProperty("buffers_created", buffersCreated);
			result.addProperty("initial_capacity_bytes", initialCapacityBytes);
			result.addProperty("reserve_calls", reserveCalls);
			result.addProperty("requested_bytes", requestedBytes);
			result.addProperty("negative_reserve_requests", negativeReserveRequests);
			result.addProperty("failed_reserve_calls", failedReserveCalls);
			result.addProperty("resize_calls", resizeCalls);
			result.addProperty("failed_resize_calls", failedResizeCalls);
			result.addProperty("growth_bytes", growthBytes);
			result.addProperty("max_capacity_bytes", maxCapacity);
			result.addProperty("max_write_offset_bytes", maxWriteOffset);
			result.addProperty("cpu_jobs_added", cpuJobs);
			result.addProperty("cpu_vertices", cpuVertices);
			result.addProperty("cpu_index_count", cpuIndexCount);
			result.addProperty("instanced_jobs_added", instancedJobs);
			result.addProperty("instances", instances);
			return result;
		}
	}

	private static class Sample {
		private final String label;
		private final Thread thread = Thread.currentThread();
		private final Counters model = new Counters();
		private final Counters other = new Counters();
		private final Matrix4f projection = new Matrix4f();
		private final float[] matrix = new float[16];
		private boolean frameCaptureBound;
		private @Nullable CameraSample cameraFirst;
		private @Nullable CameraSample cameraLast;
		private @Nullable WindowSample windowFirst;
		private @Nullable WindowSample windowLast;
		private long frames;
		private long cameraChanges;
		private long cameraUnavailableFrames;
		private long windowChanges;
		private long frameReserve;
		private long frameResizes;
		private long frameJobs;
		private long frameDrawCalls;
		private long maxFrameDrawCalls;
		private long drainScopes;
		private long failedDrains;
		private long drainNanos;
		private long drawCalls;
		private long indexedDrawCalls;
		private long arrayDrawCalls;
		private long instancedDrawCalls;
		private long submittedIndices;
		private long submittedVertices;
		private long submittedInstances;
		private long indexInstances;
		private long vertexInstances;
		private String backend = "";
		private long maxFrameReserve;
		private long maxFrameResizes;
		private long maxFrameJobs;
		private long scopeProtocolErrors;

		private Sample(String label) {
			this.label = label;
		}

		private void finishFrame() {
			maxFrameReserve = Math.max(maxFrameReserve, frameReserve);
			maxFrameResizes = Math.max(maxFrameResizes, frameResizes);
			maxFrameJobs = Math.max(maxFrameJobs, frameJobs);
			maxFrameDrawCalls = Math.max(maxFrameDrawCalls, frameDrawCalls);
		}

		private void captureEnvironment() {
			var minecraft = Minecraft.getInstance();
			backend = RenderSystem.getDevice().getBackendName();
			var camera = minecraft.gameRenderer.getMainCamera();

			if (camera.isInitialized()) {
				camera.getViewRotationProjectionMatrix(projection).get(matrix);
				var position = camera.position();

				if (cameraLast == null) {
					cameraFirst = new CameraSample();
					cameraLast = new CameraSample();
					cameraFirst.set(position.x, position.y, position.z, camera.xRot(), camera.yRot(), camera.getRoll(), camera.getFov(), matrix);
				} else if (!cameraLast.matches(position.x, position.y, position.z, camera.xRot(), camera.yRot(), camera.getRoll(), camera.getFov(), matrix)) {
					cameraChanges++;
				}

				cameraLast.set(position.x, position.y, position.z, camera.xRot(), camera.yRot(), camera.getRoll(), camera.getFov(), matrix);
			} else {
				cameraUnavailableFrames++;
			}

			var window = minecraft.getWindow();
			int refresh = window.getRefreshRate();

			if (windowLast == null) {
				windowFirst = new WindowSample();
				windowLast = new WindowSample();
				windowFirst.set(window.getWidth(), window.getHeight(), window.getScreenWidth(), window.getScreenHeight(), refresh);
			} else if (!windowLast.matches(window.getWidth(), window.getHeight(), window.getScreenWidth(), window.getScreenHeight(), refresh)) {
				windowChanges++;
			}

			windowLast.set(window.getWidth(), window.getHeight(), window.getScreenWidth(), window.getScreenHeight(), refresh);
		}

		private JsonObject snapshot(boolean running) {
			finishFrame();
			var result = new JsonObject();
			result.addProperty("enabled", true);
			result.addProperty("state", running ? "running" : "complete");
			result.addProperty("case", label);
			result.addProperty("scope", "ByteBufferBuilder inside Photon Emitter.bakeJob; counters include diagnostic overhead");
			result.addProperty("job_scope", "added CPU/instanced jobs; native drain may merge or replay jobs");
			result.addProperty("draw_call_count_available", "OpenGL".equals(backend) && openGlDrawHookObserved);
			result.addProperty("draw_backend", backend);
			result.addProperty("draw_scope", "successful GlCommandEncoder.drawFromBuffers inside Photon private drain(List,...); includes nested Bloom/occlusion/postFX, excludes work after drain returns");
			result.addProperty("draw_semantics", "issued API calls and submitted arguments, not fragments, completed GPU work or hardware executions");
			result.addProperty("draw_model_attribution_available", false);
			result.addProperty("draw_calls", drawCalls);
			result.addProperty("indexed_draw_calls", indexedDrawCalls);
			result.addProperty("array_draw_calls", arrayDrawCalls);
			result.addProperty("instanced_draw_calls", instancedDrawCalls);
			result.addProperty("submitted_indices", submittedIndices);
			result.addProperty("submitted_vertices", submittedVertices);
			result.addProperty("submitted_instances", submittedInstances);
			result.addProperty("submitted_index_instances", indexInstances);
			result.addProperty("submitted_vertex_instances", vertexInstances);
			result.addProperty("drain_scopes", drainScopes);
			result.addProperty("failed_drains", failedDrains);
			result.addProperty("drain_ns", drainNanos);
			result.addProperty("open_drain_scope", currentDrain != null && currentDrain.sample == this);
			result.addProperty("max_frame_draw_calls", maxFrameDrawCalls);
			result.addProperty("frames_observed", frames);
			result.addProperty("frame_capture_bound", frameCaptureBound);
			result.addProperty("max_frame_reserve_calls", maxFrameReserve);
			result.addProperty("max_frame_resize_calls", maxFrameResizes);
			result.addProperty("max_frame_jobs_added", maxFrameJobs);
			result.addProperty("scope_protocol_errors", scopeProtocolErrors);
			result.add("model", model.snapshot());
			result.add("other", other.snapshot());
			result.addProperty("reserve_calls", model.reserveCalls + other.reserveCalls);
			result.addProperty("requested_bytes", model.requestedBytes + other.requestedBytes);
			result.addProperty("resize_calls", model.resizeCalls + other.resizeCalls);
			result.addProperty("growth_bytes", model.growthBytes + other.growthBytes);
			result.addProperty("cpu_jobs_added", model.cpuJobs + other.cpuJobs);
			result.addProperty("instanced_jobs_added", model.instancedJobs + other.instancedJobs);
			result.addProperty("camera_scope", "main game camera at render HEAD; rotation-projection excludes position translation");
			result.addProperty("camera_changes", cameraChanges);
			result.addProperty("camera_unavailable_frames", cameraUnavailableFrames);
			result.addProperty("window_changes", windowChanges);
			result.addProperty("display_refresh_first_hz", windowFirst == null ? -1 : windowFirst.refresh);
			result.addProperty("display_refresh_last_hz", windowLast == null ? -1 : windowLast.refresh);

			if (cameraFirst != null && cameraLast != null) {
				result.add("camera_first", cameraFirst.snapshot());
				result.add("camera_last", cameraLast.snapshot());
			}

			if (windowFirst != null && windowLast != null) {
				result.add("window_first", windowFirst.snapshot());
				result.add("window_last", windowLast.snapshot());
			}

			return result;
		}
	}

	private static class CameraSample {
		private final float[] matrix = new float[16];
		private double x, y, z;
		private float pitch, yaw, roll, fov;

		private static boolean differs(float first, float second) {
			return Float.floatToRawIntBits(first) != Float.floatToRawIntBits(second);
		}

		private void set(double x, double y, double z, float pitch, float yaw, float roll, float fov, float[] matrix) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.pitch = pitch;
			this.yaw = yaw;
			this.roll = roll;
			this.fov = fov;
			System.arraycopy(matrix, 0, this.matrix, 0, this.matrix.length);
		}

		private boolean matches(double x, double y, double z, float pitch, float yaw, float roll, float fov, float[] matrix) {
			if (Double.doubleToRawLongBits(this.x) != Double.doubleToRawLongBits(x)
					|| Double.doubleToRawLongBits(this.y) != Double.doubleToRawLongBits(y)
					|| Double.doubleToRawLongBits(this.z) != Double.doubleToRawLongBits(z)
					|| differs(this.pitch, pitch) || differs(this.yaw, yaw) || differs(this.roll, roll) || differs(this.fov, fov)) {
				return false;
			}

			for (int index = 0; index < matrix.length; index++) {
				if (differs(this.matrix[index], matrix[index])) {
					return false;
				}
			}

			return true;
		}

		private JsonObject snapshot() {
			var result = new JsonObject();
			result.addProperty("x", x);
			result.addProperty("y", y);
			result.addProperty("z", z);
			result.addProperty("pitch", pitch);
			result.addProperty("yaw", yaw);
			result.addProperty("roll", roll);
			result.addProperty("fov", fov);
			var values = new JsonArray();
			long fingerprint = 0xcbf29ce484222325L;
			fingerprint = (fingerprint ^ Double.doubleToRawLongBits(x)) * 0x100000001b3L;
			fingerprint = (fingerprint ^ Double.doubleToRawLongBits(y)) * 0x100000001b3L;
			fingerprint = (fingerprint ^ Double.doubleToRawLongBits(z)) * 0x100000001b3L;

			for (float value : new float[]{pitch, yaw, roll, fov}) {
				fingerprint = (fingerprint ^ Float.floatToRawIntBits(value)) * 0x100000001b3L;
			}

			for (float value : matrix) {
				values.add(value);
				fingerprint = (fingerprint ^ Float.floatToRawIntBits(value)) * 0x100000001b3L;
			}

			result.add("view_rotation_projection_matrix", values);
			result.addProperty("fingerprint", Long.toUnsignedString(fingerprint, 16));
			return result;
		}
	}

	private static class WindowSample {
		private int framebufferWidth, framebufferHeight, windowWidth, windowHeight, refresh;

		private void set(int framebufferWidth, int framebufferHeight, int windowWidth, int windowHeight, int refresh) {
			this.framebufferWidth = framebufferWidth;
			this.framebufferHeight = framebufferHeight;
			this.windowWidth = windowWidth;
			this.windowHeight = windowHeight;
			this.refresh = refresh;
		}

		private boolean matches(int framebufferWidth, int framebufferHeight, int windowWidth, int windowHeight, int refresh) {
			return this.framebufferWidth == framebufferWidth && this.framebufferHeight == framebufferHeight
					&& this.windowWidth == windowWidth && this.windowHeight == windowHeight && this.refresh == refresh;
		}

		private JsonObject snapshot() {
			var result = new JsonObject();
			result.addProperty("framebuffer_width", framebufferWidth);
			result.addProperty("framebuffer_height", framebufferHeight);
			result.addProperty("window_width", windowWidth);
			result.addProperty("window_height", windowHeight);
			result.addProperty("display_refresh_hz", refresh);
			return result;
		}
	}
}
