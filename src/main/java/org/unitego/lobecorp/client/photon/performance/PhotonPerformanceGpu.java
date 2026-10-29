package org.unitego.lobecorp.client.photon.performance;

import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceGpuStatsUtil;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

import java.util.ArrayList;
import java.util.List;

/// 独占 timestamp 查询；不嵌套 TIME_ELAPSED、不抢占原版查询、不等待未完成的 GPU。
public class PhotonPerformanceGpu {
	private static final int TIMESTAMPS_PER_FRAME = 64;
	private static final int PENDING_FRAMES = 8;
	// 每个采样占一对 timestamp，槽池按 8 帧、每帧最多 32 个采样配置。
	private static final int SLOTS = TIMESTAMPS_PER_FRAME * PENDING_FRAMES / 2;
	private static final int MAX_SAMPLES = 8192;
	private static @Nullable Queries active;
	private static long serialCounter;
	private static List<JsonObject> lastSummaries = List.of();
	private static long viewNs;
	private static List<JsonObject> cachedSummaries = List.of();
	private static JsonObject lastStatus = new JsonObject();

	public static void resetStatus() {
		RenderSystem.assertOnRenderThread();
		if (active != null) {
			throw new IllegalStateException("gpu_capture_already_active");
		}
		lastStatus = new JsonObject();
		lastSummaries = List.of();
		cachedSummaries = List.of();
		lastStatus.addProperty("requested", false);
		lastStatus.add("supported", JsonNull.INSTANCE);
	}

	public static void start() {
		RenderSystem.assertOnRenderThread();
		finish();
		cachedSummaries = List.of();
		viewNs = 0;
		var queries = new Queries();
		active = queries;
		try {
			if (!"OpenGL".equals(RenderSystem.getDevice().getBackendName()) || !GL.getCapabilities().OpenGL33) {
				queries.problem = "timestamp_queries_require_opengl_33";
				return;
			}
			queries.bits = GL15.glGetQueryi(GL33.GL_TIMESTAMP, GL15.GL_QUERY_COUNTER_BITS);
			if (queries.bits == 0) {
				queries.problem = "timestamp_counter_unavailable";
				return;
			}
			GL15.glGenQueries(queries.ids);
			queries.ready = true;
		} catch (RuntimeException | LinkageError failure) {
			queries.problem = failure.getClass().getSimpleName() + ": " + failure.getMessage();
		}
	}

	public static void frame(int frame) {
		var queries = active;
		if (queries == null) {
			return;
		}
		queries.frame = frame;
		queries.used = 0;
		queries.poll();
	}

	public static long begin(String pass, int owner) {
		return begin(pass, owner, owner == 0);
	}

	public static long begin(String pass, int owner, boolean shared) {
		var queries = active;
		if (queries == null || !queries.ready || queries.problem != null) {
			return 0;
		}
		if (queries.used >= TIMESTAMPS_PER_FRAME / 2) {
			queries.skipped++;
			queries.stats.skipped(queries.frame, pass, owner, shared);
			return 0;
		}
		for (int attempt = 0; attempt < SLOTS; attempt++) {
			int slot = queries.next++ % SLOTS;
			if (queries.states[slot] != 0) {
				continue;
			}
			try {
				queries.states[slot] = 1;
				queries.frames[slot] = queries.frame;
				queries.passes[slot] = pass;
				queries.owners[slot] = owner;
				queries.shared[slot] = shared;
				queries.parents[slot] = queries.currentToken;
				queries.cpuBegins[slot] = System.nanoTime();
				queries.cpuEnds[slot] = 0;
				queries.serials[slot] = ++serialCounter;
				GL33.glQueryCounter(queries.ids[slot * 2], GL33.GL_TIMESTAMP);
				queries.used++;
				queries.started++;
				// 高 48 位保存序号，低 16 位保存槽位加一，用于拒绝过期 token。
				long token = queries.serials[slot] << 16 | slot + 1;
				queries.currentToken = token;
				return token;
			} catch (RuntimeException | LinkageError failure) {
				queries.states[slot] = 0;
				queries.problem = failure.getClass().getSimpleName() + ": " + failure.getMessage();
				return 0;
			}
		}
		queries.skipped++;
		queries.stats.skipped(queries.frame, pass, owner, shared);
		return 0;
	}

	public static void end(long token, boolean completed) {
		var queries = active;
		if (queries == null || token == 0) {
			return;
		}
		int slot = (int) (token & 65535) - 1;
		if (slot < 0 || slot >= SLOTS || queries.serials[slot] != token >>> 16 || queries.states[slot] != 1) {
			return;
		}
		try {
			queries.cpuEnds[slot] = System.nanoTime();
			GL33.glQueryCounter(queries.ids[slot * 2 + 1], GL33.GL_TIMESTAMP);
			queries.completed[slot] = completed;
			queries.states[slot] = 2;
		} catch (RuntimeException | LinkageError failure) {
			queries.states[slot] = 0;
			queries.problem = failure.getClass().getSimpleName() + ": " + failure.getMessage();
		} finally {
			if (queries.currentToken == token) {
				queries.currentToken = queries.parents[slot];
			}
		}
	}

	public static List<JsonObject> finish() {
		var queries = active;
		if (queries == null) {
			return List.of();
		}
		active = null;
		queries.poll();
		for (int slot = 0; slot < SLOTS; slot++) {
			if (queries.states[slot] != 0) {
				queries.sample(slot, "not_reclaimed_at_capture_end", -1, -1);
				queries.states[slot] = 0;
			}
		}
		try {
			if (queries.ready) {
				GL15.glDeleteQueries(queries.ids);
			}
		} catch (RuntimeException | LinkageError failure) {
			queries.problem = failure.getClass().getSimpleName() + ": " + failure.getMessage();
		}
		lastStatus = queries.status();
		var result = new ArrayList<>(queries.samples);
		lastSummaries = queries.stats.summaries();
		result.addAll(lastSummaries);
		return List.copyOf(result);
	}

	/// 仅在 GPU 页请求；有界统计最多每 250 ms 构建一次，不触发 GL 查询或等待。
	public static List<JsonObject> view() {
		RenderSystem.assertOnRenderThread();
		var queries = active;
		if (queries == null) {
			return lastSummaries;
		}
		long now = System.nanoTime();
		if (now - viewNs >= PhotonPerformanceCapture.VIEW_REFRESH_NS) {
			cachedSummaries = queries.stats.summaries();
			viewNs = now;
		}
		return cachedSummaries;
	}

	public static JsonObject status() {
		var queries = active;
		return queries == null ? lastStatus.deepCopy() : queries.status();
	}

	private static class Queries {
		private int[] ids = new int[SLOTS * 2];
		private byte[] states = new byte[SLOTS];
		private int[] frames = new int[SLOTS];
		private int[] owners = new int[SLOTS];
		private long[] serials = new long[SLOTS];
		private long[] parents = new long[SLOTS];
		private long[] cpuBegins = new long[SLOTS];
		private long[] cpuEnds = new long[SLOTS];
		private boolean[] shared = new boolean[SLOTS];
		private long currentToken;
		private PhotonPerformanceGpuStatsUtil stats = new PhotonPerformanceGpuStatsUtil();
		private String[] passes = new String[SLOTS];
		private boolean[] completed = new boolean[SLOTS];
		private List<JsonObject> samples = new ArrayList<>();
		private boolean ready;
		private int bits;
		private int frame;
		private int used;
		private int next;
		private long started;
		private long reclaimed;
		private long skipped;
		private long overwritten;
		private int sampleCursor;
		private @Nullable String problem;

		private void poll() {
			if (!ready || problem != null) {
				return;
			}
			try {
				for (int slot = 0; slot < SLOTS; slot++) {
					if (states[slot] != 2) {
						continue;
					}
					if (GL15.glGetQueryObjecti(ids[slot * 2 + 1], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
						long begin = GL33.glGetQueryObjecti64(ids[slot * 2], GL15.GL_QUERY_RESULT);
						long end = GL33.glGetQueryObjecti64(ids[slot * 2 + 1], GL15.GL_QUERY_RESULT);
						long duration = end - begin;
						if (bits < Long.SIZE) {
							duration &= (1L << bits) - 1;
						}
						sample(slot, completed[slot] && duration >= 0 ? "measured" : "failed_scope", begin, duration);
						reclaimed++;
						states[slot] = 0;
					} else if (frame - frames[slot] >= PENDING_FRAMES) {
						sample(slot, "query_timeout", -1, -1);
						states[slot] = 0;
					}
				}
			} catch (RuntimeException | LinkageError failure) {
				problem = failure.getClass().getSimpleName() + ": " + failure.getMessage();
			}
		}

		private void sample(int slot, String status, long start, long duration) {
			var value = new JsonObject();
			value.addProperty("record_type", "sample");
			value.addProperty("query_id", Long.toString(serials[slot] << 16 | slot + 1));
			value.addProperty("parent_query_id", Long.toString(parents[slot]));
			value.addProperty("cpu_submit_begin_ns", cpuBegins[slot]);
			value.add("cpu_submit_end_ns", cpuEnds[slot] == 0 ? JsonNull.INSTANCE : new JsonPrimitive(cpuEnds[slot]));
			value.addProperty("frame_id", frames[slot]);
			value.addProperty("pass", passes[slot]);
			value.addProperty("node_id", owners[slot]);
			value.addProperty("ownership", shared[slot] ? "shared_or_unattributed" : "linked_scope");
			value.addProperty("source", "OpenGL timestamp pair");
			value.addProperty("status", status);
			value.add("gpu_timestamp_ns", start < 0 ? JsonNull.INSTANCE : new JsonPrimitive(start));
			value.add("duration_ns", duration < 0 ? JsonNull.INSTANCE : new JsonPrimitive(duration));
			value.addProperty("cpu_clock_calibrated", false);
			stats.sample(frames[slot], passes[slot], owners[slot], shared[slot], status, duration, value);
			if (samples.size() < MAX_SAMPLES) {
				samples.add(value);
			} else {
				samples.set(sampleCursor++ % MAX_SAMPLES, value);
				overwritten++;
			}
		}

		private JsonObject status() {
			var value = new JsonObject();
			value.addProperty("requested", true);
			value.addProperty("supported", ready);
			value.addProperty("counter_bits", bits);
			value.addProperty("started", started);
			value.addProperty("reclaimed", reclaimed);
			value.addProperty("skipped_budget_or_pending", skipped);
			value.addProperty("overwritten_samples", overwritten);
			value.addProperty("dropped_aggregates", stats.dropped());
			value.addProperty("error", problem);
			return value;
		}
	}
}
