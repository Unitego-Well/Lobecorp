package org.unitego.lobecorp.client.bench;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/// 仅在显式基准期间异步记录环境；外部只读探针有超时，并只清理本对象启动的进程。
public class HordeBenchmarkEnvironment implements AutoCloseable {
	private static final int SAMPLE_SECONDS = 5;
	private static final int PROBE_TIMEOUT_SECONDS = 2;
	private static final int PROBE_CLEANUP_MILLIS = 200;
	private static final int MAX_OUTPUT_BYTES = 65536;
	private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
			Thread.ofPlatform().daemon().name("Horde benchmark environment").factory());
	private final JsonObject revision = new JsonObject();
	private final JsonArray samples = new JsonArray();
	private final Path project;
	private final String ownerId = UUID.randomUUID().toString();
	private final String createdUtc = Instant.now().toString();
	private final List<OwnedProbe> probes = new ArrayList<>();
	private String workerFailure = "";
	private boolean closed;

	public HordeBenchmarkEnvironment() {
		project = findProject();
		worker.scheduleAtFixedRate(this::sample, 0, SAMPLE_SECONDS, TimeUnit.SECONDS);
	}

	private static Path findProject() {
		for (var path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
			if (Files.exists(path.resolve(".git")) && Files.exists(path.resolve("build.gradle.kts"))) {
				return path;
			}
		}
		return null;
	}

	private synchronized boolean stopping() {
		return closed || Thread.currentThread().isInterrupted();
	}

	private void sample() {
		try {
			if (stopping()) {
				return;
			}
			var sample = new JsonObject();
			sample.addProperty("utc", Instant.now().toString());
			sample.addProperty("source", "nvidia-smi");
			sample.add("probe", probe(List.of("nvidia-smi",
					"--query-gpu=index,uuid,clocks.current.graphics,power.draw,temperature.gpu,memory.used,utilization.gpu",
					"--format=csv,noheader,nounits")));
			if (stopping()) {
				return;
			}
			synchronized (this) {
				if (closed) {
					return;
				}
				if (!revision.isEmpty()) {
					samples.add(sample);
					return;
				}
			}
			var code = new JsonObject();
			if (project == null) {
				code.addProperty("unavailable", "Project root not found");
			} else {
				code.addProperty("project", project.toString());
				code.add("head", probe(List.of("git", "--no-optional-locks", "-c", "core.fsmonitor=false",
						"-C", project.toString(), "rev-parse", "HEAD")));
				if (stopping()) {
					return;
				}
				code.add("dirty", probe(List.of("git", "--no-optional-locks", "-c", "core.fsmonitor=false",
						"-C", project.toString(), "status", "--porcelain=v1")));
			}
			synchronized (this) {
				if (!closed) {
					revision.add("code", code);
					samples.add(sample);
				}
			}
		} catch (RuntimeException failure) {
			synchronized (this) {
				workerFailure = failure.toString();
				var sample = new JsonObject();
				sample.addProperty("utc", Instant.now().toString());
				sample.addProperty("unavailable", failure.toString());
				samples.add(sample);
			}
		}
	}

	private JsonObject probe(List<String> command) {
		var result = new JsonObject();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROBE_TIMEOUT_SECONDS);
		long workDeadline = deadline - TimeUnit.MILLISECONDS.toNanos(PROBE_CLEANUP_MILLIS);
		OwnedProbe owned = null;
		boolean interrupted = false;
		try {
			synchronized (this) {
				if (closed || Thread.currentThread().isInterrupted()) {
					result.addProperty("unavailable", "Probe cancelled before start");
					return result;
				}
				// 本地进程启动不可抢占；启动耗时计入同一截止时间，后续等待不重新计时。
				var process = new ProcessBuilder(command).redirectErrorStream(true).start();
				owned = new OwnedProbe(ownerId, command, process, workDeadline);
				probes.add(owned);
				owned.reader.start();
			}
			if (!awaitExit(owned.process, workDeadline)) {
				result.addProperty("unavailable", "Probe timed out");
			} else {
				result.addProperty("exit_code", owned.process.exitValue());
			}
			if (!awaitReader(owned.reader, workDeadline) || !owned.drainComplete) {
				result.addProperty("unavailable", "Probe output deadline exceeded");
			}
		} catch (IOException failure) {
			result.addProperty("unavailable", failure.toString());
		} catch (InterruptedException failure) {
			result.addProperty("unavailable", "Probe interrupted");
			interrupted = true;
		} finally {
			if (owned != null) {
				cleanup(owned, deadline);
				owned.describe(result);
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
		return result;
	}

	private static boolean awaitExit(Process process, long deadline) throws InterruptedException {
		long remaining = deadline - System.nanoTime();
		return !process.isAlive() || remaining > 0 && process.waitFor(remaining, TimeUnit.NANOSECONDS);
	}

	private static boolean awaitReader(Thread reader, long deadline) throws InterruptedException {
		long remaining = deadline - System.nanoTime();
		if (reader.isAlive() && remaining > 0) {
			TimeUnit.NANOSECONDS.timedJoin(reader, remaining);
		}
		return !reader.isAlive();
	}

	private static void cleanup(OwnedProbe owned, long deadline) {
		boolean interrupted = Thread.interrupted();
		try {
			if (owned.process.isAlive()) {
				owned.process.destroy();
				long grace = Math.clamp(deadline - System.nanoTime(), 0L, TimeUnit.MILLISECONDS.toNanos(200));
				if (grace > 0) {
					owned.process.waitFor(grace, TimeUnit.NANOSECONDS);
				}
				if (owned.process.isAlive()) {
					owned.process.destroyForcibly();
					awaitExit(owned.process, deadline);
				}
			}
		} catch (InterruptedException failure) {
			interrupted = true;
			try {
				if (owned.process.isAlive()) {
					owned.process.destroyForcibly();
				}
			} catch (RuntimeException cleanupFailure) {
				owned.cleanupFailure(cleanupFailure.toString());
			}
		} catch (RuntimeException failure) {
			owned.cleanupFailure(failure.toString());
		} finally {
			if (owned.reader.isAlive()) {
				owned.reader.interrupt();
				try {
					awaitReader(owned.reader, deadline);
				} catch (InterruptedException failure) {
					interrupted = true;
				}
			}
			closeStream(owned, owned.process.getErrorStream());
			closeStream(owned, owned.process.getOutputStream());
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private static void closeStream(OwnedProbe owned, Closeable stream) {
		try {
			stream.close();
		} catch (IOException | RuntimeException failure) {
			owned.cleanupFailure(failure.toString());
		}
	}

	private static class OwnedProbe {
		private final String owner;
		private final List<String> command;
		private final Process process;
		private final String startedUtc;
		private final long deadline;
		private final Thread reader;
		private final ByteArrayOutputStream output = new ByteArrayOutputStream();
		private boolean truncated;
		private volatile boolean drainComplete;
		private volatile boolean readerFinished;
		private volatile String readFailure = "";
		private String cleanupError = "";

		private OwnedProbe(String owner, List<String> command, Process process, long deadline) {
			this.owner = owner;
			this.command = List.copyOf(command);
			this.process = process;
			this.deadline = deadline;
			startedUtc = process.info().startInstant().map(Instant::toString).orElse("unknown");
			reader = Thread.ofPlatform().daemon().name("Horde probe output " + process.pid()).unstarted(this::drain);
		}

		private void drain() {
			// 持续排空但只保留有界前缀；只读取 available 字节，避免继承管道未 EOF 阻塞关闭。
			try (var input = process.getInputStream()) {
				var bytes = new byte[4096];
				while (!Thread.currentThread().isInterrupted() && System.nanoTime() < deadline) {
					int available = input.available();
					if (available > 0) {
						int count = input.read(bytes, 0, Math.min(available, bytes.length));
						if (count < 0) {
							drainComplete = true;
							return;
						}
						synchronized (this) {
							int kept = Math.min(count, MAX_OUTPUT_BYTES - output.size());
							output.write(bytes, 0, kept);
							truncated |= kept < count;
						}
					} else if (!process.isAlive()) {
						drainComplete = true;
						return;
					} else {
						TimeUnit.MILLISECONDS.sleep(2);
					}
				}
			} catch (IOException failure) {
				readFailure = failure.toString();
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
			} finally {
				readerFinished = true;
			}
		}

		private synchronized void cleanupFailure(String message) {
			cleanupError = cleanupError.isEmpty() ? message : cleanupError + "; " + message;
		}

		private synchronized void describe(JsonObject value) {
			value.addProperty("owner_id", owner);
			value.add("command", new Gson().toJsonTree(command));
			value.addProperty("pid", process.pid());
			value.addProperty("started_utc", startedUtc);
			value.addProperty("process_alive", process.isAlive());
			value.addProperty("reader_alive", reader.isAlive());
			value.addProperty("reader_thread_id", reader.threadId());
			value.addProperty("reader_thread_name", reader.getName());
			value.addProperty("reader_finished", readerFinished);
			value.addProperty("drain_complete", drainComplete);
			value.addProperty("truncated", truncated);
			value.addProperty("output", output.toString(StandardCharsets.UTF_8).trim());
			value.addProperty("cleanup_complete", !process.isAlive() && !reader.isAlive() && readerFinished
					&& cleanupError.isEmpty() && readFailure.isEmpty());
			if (!readFailure.isEmpty()) {
				value.addProperty("read_error", readFailure);
			}
			if (!cleanupError.isEmpty()) {
				value.addProperty("cleanup_error", cleanupError);
			}
		}
	}

	public synchronized JsonObject snapshot() {
		var value = new JsonObject();
		value.addProperty("owner_id", ownerId);
		value.addProperty("created_utc", createdUtc);
		value.addProperty("sample_period_seconds", SAMPLE_SECONDS);
		value.addProperty("probe_total_deadline_seconds", PROBE_TIMEOUT_SECONDS);
		value.addProperty("probe_cleanup_reserve_millis", PROBE_CLEANUP_MILLIS);
		value.addProperty("gpu_units", "index,uuid,MHz,W,C,MiB,percent; one CSV row per GPU");
		value.addProperty("scope", "Asynchronous probes; no inferred zero values or automatic pass");
		value.addProperty("output_scope", "Owned process output, bounded prefix; drain ends after process exit and available bytes exhausted");
		value.add("revision", revision.deepCopy());
		value.add("samples", samples.deepCopy());
		var ownership = new JsonArray();
		var pending = new JsonArray();
		for (var probe : probes) {
			var state = new JsonObject();
			probe.describe(state);
			ownership.add(state);
			if (!state.get("cleanup_complete").getAsBoolean()) {
				pending.add(state.deepCopy());
			}
		}
		value.add("owned_probes", ownership);
		value.add("cleanup_pending", pending);
		value.addProperty("closed", closed);
		value.addProperty("worker_terminated", worker.isTerminated());
		value.addProperty("cleanup_complete", closed && worker.isTerminated() && pending.isEmpty());
		if (!workerFailure.isEmpty()) {
			value.addProperty("worker_failure", workerFailure);
		}
		return value;
	}

	@Override
	public void close() {
		List<OwnedProbe> owned;
		synchronized (this) {
			// 与 check/start 共用此锁；置位后任何探针都不能再创建进程。
			closed = true;
			owned = List.copyOf(probes);
		}
		worker.shutdownNow();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROBE_TIMEOUT_SECONDS + 1);
		boolean interrupted = Thread.interrupted();
		try {
			for (var probe : owned) {
				cleanup(probe, deadline);
			}
			long remaining = deadline - System.nanoTime();
			if (remaining > 0) {
				if (!worker.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
					synchronized (this) {
						workerFailure = "Environment worker did not terminate before cleanup deadline";
					}
				}
			}
		} catch (InterruptedException failure) {
			interrupted = true;
		} finally {
			// 已过截止时间仍执行无等待终止；未释放的句柄和线程继续保存在归属清单中。
			for (var probe : owned) {
				if (probe.process.isAlive() || probe.reader.isAlive()) {
					cleanup(probe, deadline);
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
