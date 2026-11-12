package org.unitego.lobecorp.util.photon.performance;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceReportUtil.Frames;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Capture;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceWindowUtil.Result;

import java.util.List;
import java.util.concurrent.*;

/// 冻结快照的专用有界分析线程；每个面板只留一个运行任务和一个最新等待目标。
public class PhotonPerformanceWindowAnalysisUtil implements AutoCloseable {
	private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
			new ArrayBlockingQueue<>(8), Thread.ofPlatform().daemon().name("lobecorp-photon-window-analysis").factory(),
			new ThreadPoolExecutor.AbortPolicy());
	private final Executor executor;
	private volatile long revision;
	private volatile State state = new State(false, null, null);
	private @Nullable Request pending;
	private boolean running;
	private boolean closed;

	public PhotonPerformanceWindowAnalysisUtil() {
		this(WORKER);
	}

	PhotonPerformanceWindowAnalysisUtil(Executor executor) {
		this.executor = executor;
	}

	public synchronized void request(Capture capture, Frames frames, List<JsonObject> gpu, int first, int last) {
		if (closed) {
			return;
		}
		PhotonPerformanceWindowUtil.bounds(frames, first, last);
		pending = new Request(++revision, capture, frames, gpu, first, last);
		state = new State(true, null, null);
		if (!running) {
			running = true;
			try {
				executor.execute(this::pump);
			} catch (RejectedExecutionException failure) {
				running = false;
				pending = null;
				state = new State(false, null, "analysis_queue_full");
			}
		}
	}

	public State state() {
		return state;
	}

	public synchronized void clear() {
		revision++;
		pending = null;
		state = new State(false, null, null);
	}

	@Override
	public synchronized void close() {
		closed = true;
		clear();
	}

	private void pump() {
		while (true) {
			Request request;
			synchronized (this) {
				if (pending == null || closed) {
					running = false;
					return;
				}
				request = pending;
				pending = null;
			}
			Result result = null;
			String error = null;
			try {
				result = PhotonPerformanceWindowUtil.analyze(request.capture(), request.frames(), request.gpu(),
						request.first(), request.last(), () -> revision != request.revision());
			} catch (CancellationException ignored) {
				// 新请求、清除或关闭撤销发布资格；不等待旧结果后再显示。
			} catch (RuntimeException failure) {
				error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
			}
			synchronized (this) {
				if (!closed && revision == request.revision()) {
					state = new State(false, result, error);
				}
			}
		}
	}

	public record State(boolean working, @Nullable Result result, @Nullable String error) {
	}

	private record Request(long revision, Capture capture, Frames frames, List<JsonObject> gpu, int first, int last) {
	}
}
