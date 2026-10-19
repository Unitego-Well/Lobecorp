package org.unitego.lobecorp.util.photon.performance;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceReportUtil.Frames;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Capture;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Event;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mark;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Mode;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Retention;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/// 只分析冻结捕获；边界调用只计墙钟交集，不按比例伪造自身耗时、分配或 GPU 时间。
public class PhotonPerformanceWindowUtil {
	public record Bounds(int firstFrame, int lastFrame, long fromNs, long toNs) {
	}

	public record Distribution(int samples, int missing, @Nullable Long sum, @Nullable Long maximum,
	                           @Nullable Long p95, @Nullable Long p99) {
	}

	public record FrameMetrics(Distribution intervals, Distribution cpu, Distribution gpu, Distribution allocations) {
	}

	public record CpuRow(long threadId, String threadName, int node, Stage stage, long fullCalls, long partialCalls,
	                     long incomplete, long failed, long inclusiveOverlapNs, long knownSelfNs,
	                     long maximumFullNs, long allocationSamples, long knownSelfAllocatedBytes,
	                     long cpuSamples, long knownInclusiveThreadCpuNs) {
	}

	public record GpuRow(String pass, int node, String ownership, long measured, long unavailable, long sumNs,
	                     long maximumNs) {
	}

	public record Result(long sessionId, Bounds bounds, FrameMetrics frames, boolean detailedCpu,
	                     List<CpuRow> cpu, List<GpuRow> gpu, List<Mark> marks, List<Retention> retention,
	                     List<Event> cpuPeakHints, List<JsonObject> gpuPeakHints, long gpuPartial,
	                     long gpuUnknownSubmission,
	                     long globalDropped, long globalOverwritten, long globalIncomplete) {
	}

	private record Key(long thread, int node, Stage stage) {
	}

	private record GpuKey(String pass, int node, String ownership) {
	}

	public static Bounds bounds(Frames frames, int firstFrame, int lastFrame) {
		if (firstFrame < 1 || lastFrame < firstFrame || lastFrame >= frames.count()) {
			throw new IllegalArgumentException("invalid_frame_window");
		}
		// intervals[i] 属于 heads[i-1] → heads[i]；不能错用事件开始时的帧号。
		return new Bounds(firstFrame, lastFrame, frames.originNs() + frames.heads()[firstFrame - 1],
				frames.originNs() + frames.heads()[lastFrame]);
	}

	public static FrameMetrics frameMetrics(Frames frames, int firstFrame, int lastFrame) {
		bounds(frames, firstFrame, lastFrame);
		return new FrameMetrics(distribution(frames.intervals(), firstFrame, lastFrame),
				distribution(frames.cpu(), firstFrame - 1, lastFrame - 1),
				distribution(frames.gpu(), firstFrame - 1, lastFrame - 1),
				distribution(frames.allocations(), firstFrame, lastFrame));
	}

	public static Result analyze(Capture capture, Frames frames, List<JsonObject> gpuSamples,
	                             int firstFrame, int lastFrame, BooleanSupplier cancelled) {
		var bounds = bounds(frames, firstFrame, lastFrame);
		var cpu = new LinkedHashMap<Key, CpuAccumulator>();
		var retention = new ArrayList<Retention>();
		boolean detailed = capture.view().mode() != Mode.OVERVIEW;
		for (var thread : capture.threads()) {
			check(cancelled);
			retention.add(thread.retention());
			if (detailed) {
				thread.forEachEvent(bounds.fromNs(), bounds.toNs(), event -> {
					check(cancelled);
					var key = new Key(thread.threadId(), event.node(), event.stage());
					cpu.computeIfAbsent(key, _ -> new CpuAccumulator(thread.threadId(), thread.threadName(), event.node(), event.stage()))
							.add(event, bounds);
				});
			}
		}
		var cpuRows = cpu.values().stream().map(CpuAccumulator::row)
				.sorted(Comparator.comparingLong(CpuRow::knownSelfNs).reversed()).toList();
		var peaks = capture.view().aggregates().stream().map(PhotonPerformanceTraceUtil.Aggregate::maximumEvent)
				.filter(event -> event != null && event.completed() && event.startNs() < bounds.toNs() && event.endNs() > bounds.fromNs()).toList();
		var gpu = new LinkedHashMap<GpuKey, GpuAccumulator>();
		var gpuPeaks = new ArrayList<JsonObject>();
		long partial = 0;
		long unknown = 0;
		for (var sample : gpuSamples) {
			check(cancelled);
			if ("aggregate".equals(string(sample, "record_type"))) {
				var maximum = sample.get("maximum_sample");
				if (maximum != null && maximum.isJsonObject() && overlapsSubmission(maximum.getAsJsonObject(), bounds)) {
					gpuPeaks.add(maximum.getAsJsonObject().deepCopy());
				}
				continue;
			}
			if (!"sample".equals(string(sample, "record_type"))) {
				continue;
			}
			long from = number(sample, "cpu_submit_begin_ns", -1);
			long to = number(sample, "cpu_submit_end_ns", -1);
			if (from < 0 || to < from) {
				unknown++;
				continue;
			}
			if (from >= bounds.toNs() || to <= bounds.fromNs()) {
				continue;
			}
			if (from < bounds.fromNs() || to > bounds.toNs()) {
				partial++;
				continue;
			}
			var key = new GpuKey(string(sample, "pass"), (int) number(sample, "node_id", 0), string(sample, "ownership"));
			gpu.computeIfAbsent(key, GpuAccumulator::new).add(sample);
		}
		var gpuRows = gpu.values().stream().map(GpuAccumulator::row)
				.sorted(Comparator.comparingLong(GpuRow::sumNs).reversed()).toList();
		var marks = capture.view().marks().stream()
				.filter(mark -> mark.timeNs() >= bounds.fromNs() && mark.timeNs() < bounds.toNs()).toList();
		check(cancelled);
		return new Result(capture.view().sessionId(), bounds, frameMetrics(frames, firstFrame, lastFrame), detailed,
				cpuRows, gpuRows, marks, List.copyOf(retention), peaks, List.copyOf(gpuPeaks), partial, unknown,
				capture.view().dropped(), capture.view().overwritten(), capture.view().incomplete());
	}

	/// 图表每个时间桶保留最高帧间隔，避免均值掩盖一次长帧；返回对应的原始帧索引。
	public static int[] chartFrames(Frames frames, int buckets) {
		if (frames.count() < 2 || buckets <= 0) {
			return new int[0];
		}
		int count = Math.min(buckets, frames.count() - 1);
		int[] result = new int[count];
		long first = frames.heads()[0];
		long duration = Math.max(1, frames.heads()[frames.count() - 1] - first);
		for (int frame = 1; frame < frames.count(); frame++) {
			int bucket = Math.min(count - 1, (int) ((frames.heads()[frame] - first) / (double) duration * count));
			if (result[bucket] == 0 || frames.intervals()[frame] > frames.intervals()[result[bucket]]) {
				result[bucket] = frame;
			}
		}
		return Arrays.stream(result).filter(value -> value > 0).toArray();
	}

	private static Distribution distribution(long[] values, int first, int last) {
		long[] retained = new long[last - first + 1];
		int count = 0;
		long sum = 0;
		for (int index = first; index <= last; index++) {
			if (values[index] >= 0) {
				retained[count++] = values[index];
				sum += values[index];
			}
		}
		if (count == 0) {
			return new Distribution(0, last - first + 1, null, null, null, null);
		}
		Arrays.sort(retained, 0, count);
		return new Distribution(count, last - first + 1 - count, sum, retained[count - 1],
				retained[(int) Math.ceil(count * .95) - 1], retained[(int) Math.ceil(count * .99) - 1]);
	}

	private static void check(BooleanSupplier cancelled) {
		if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
			throw new CancellationException();
		}
	}

	private static boolean overlapsSubmission(JsonObject value, Bounds bounds) {
		long from = number(value, "cpu_submit_begin_ns", -1);
		long to = number(value, "cpu_submit_end_ns", -1);
		return from >= 0 && to >= from && from < bounds.toNs() && to > bounds.fromNs();
	}

	private static String string(JsonObject object, String name) {
		var value = object.get(name);
		return value == null || value.isJsonNull() ? "" : value.getAsString();
	}

	private static long number(JsonObject object, String name, long fallback) {
		var value = object.get(name);
		return value == null || value.isJsonNull() ? fallback : value.getAsLong();
	}

	static class CpuAccumulator {
		private final long thread;
		private final String name;
		private final int node;
		private final Stage stage;
		private long full, partial, incomplete, failed, overlap, self, maximum, allocationSamples, allocation, cpuSamples, cpu;

		CpuAccumulator(long thread, String name, int node, Stage stage) {
			this.thread = thread;
			this.name = name;
			this.node = node;
			this.stage = stage;
		}

		void add(Event event, Bounds bounds) {
			if (event.startNs() >= bounds.toNs()) {
				return;
			}
			if (!event.completed()) {
				incomplete++;
				return;
			}
			if (event.endNs() < bounds.fromNs() || event.endNs() == bounds.fromNs() && event.startNs() < bounds.fromNs()
					|| event.endNs() < event.startNs()) {
				return;
			}
			overlap += Math.min(event.endNs(), bounds.toNs()) - Math.max(event.startNs(), bounds.fromNs());
			if (event.failed()) {
				failed++;
			}
			if (event.startNs() < bounds.fromNs() || event.endNs() > bounds.toNs()) {
				partial++;
				return;
			}
			full++;
			self += event.selfNs();
			maximum = Math.max(maximum, event.endNs() - event.startNs());
			if (event.selfAllocatedBytes() >= 0) {
				allocationSamples++;
				allocation += event.selfAllocatedBytes();
			}
			if (event.threadCpuNs() >= 0) {
				cpuSamples++;
				cpu += event.threadCpuNs();
			}
		}

		CpuRow row() {
			return new CpuRow(thread, name, node, stage, full, partial, incomplete, failed, overlap, self,
					maximum, allocationSamples, allocation, cpuSamples, cpu);
		}
	}

	private static class GpuAccumulator {
		private final GpuKey key;
		private long measured, unavailable, sum, maximum;

		private GpuAccumulator(GpuKey key) {
			this.key = key;
		}

		private void add(JsonObject sample) {
			long duration = number(sample, "duration_ns", -1);
			if (!"measured".equals(string(sample, "status")) || duration < 0) {
				unavailable++;
				return;
			}
			measured++;
			sum += duration;
			maximum = Math.max(maximum, duration);
		}

		private GpuRow row() {
			return new GpuRow(key.pass(), key.node(), key.ownership(), measured, unavailable, sum, maximum);
		}
	}
}
