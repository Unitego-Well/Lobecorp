package org.unitego.lobecorp.util.photon.performance;

import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/// 同 JVM 的有界诊断记录；不引用客户端类，服务端标记不改变业务载荷或执行线程。
public class PhotonPerformanceTraceUtil {
	public static final int MAX_NODES = 4096;
	/// 类初始化时读取 `-Dlobecorp.photon.trace.maxNodes=16384`；默认 4096，钳制到 4096..16384，包含共享节点 0。
	public static final int EFFECTIVE_MAX_NODES = PhotonPerformanceTraceConfigUtil.maxNodes(
			System.getProperty(PhotonPerformanceTraceConfigUtil.MAX_NODES_PROPERTY));
	public static final int MAX_THREADS = 8;
	public static final int EVENTS_PER_THREAD = 32768;
	public static final int MAX_DEPTH = 128;
	public static final int MAX_SPANS_PER_FRAME = 4096;
	public static final int MAX_AGGREGATES_PER_THREAD = 4096;
	/// 类初始化时读取 `-Dlobecorp.photon.trace.maxAggregatesPerThread=16384`；默认 4096，钳制到 4096..65536。
	/// 仅控制每线程 span 聚合，按需增长；报告配套元数据可读取此有效值，计数器与归属预算保持不变。
	public static final int EFFECTIVE_MAX_AGGREGATES_PER_THREAD = PhotonPerformanceTraceConfigUtil.maxAggregatesPerThread(
			System.getProperty(PhotonPerformanceTraceConfigUtil.MAX_AGGREGATES_PROPERTY));
	/// 类初始化时读取计数器独立预算；默认 4096，钳制到 4096..16384，按需增长。
	public static final int EFFECTIVE_MAX_COUNTERS_PER_THREAD = PhotonPerformanceTraceConfigUtil.maxCountersPerThread(
			System.getProperty(PhotonPerformanceTraceConfigUtil.MAX_COUNTERS_PROPERTY));
	public static final int MAX_MARKS = 2048;
	public static final int MAX_IMPORTANT_MARKS = 512;
	private static final int MAX_TEXT = 256;
	private static final int MAX_LOCAL_IDENTITIES = 512;
	private static final int EVENT_MASK = EVENTS_PER_THREAD - 1;
	private static final int EPOCH_SHIFT = 48;
	private static final int THREAD_SHIFT = 40;
	private static final int KEY_MASK = 65535;
	private static final Stage[] STAGES = Stage.values();
	private static final Metric[] METRICS = Metric.values();
	private static final int COMPLETED = 1;
	private static final int FAILED = 2;
	private static final int TRUNCATED = 4;
	private static final long UNAVAILABLE = -1;
	private static final AtomicLong EPOCHS = new AtomicLong();
	private static final ThreadLocal<LocalState> LOCAL = ThreadLocal.withInitial(LocalState::new);
	private static volatile @Nullable Session active;

	public static boolean active() {
		var session = active;
		return session != null && session.accepting && session.problem == null && session.limit == null;
	}

	public static boolean enabled(Stage stage) {
		var session = active;
		return session != null && session.accepting && session.problem == null && session.limit == null
				&& (session.mode != Mode.OVERVIEW || !stage.detailed);
	}

	public static @Nullable Limit limit() {
		var session = active;
		return session == null ? null : session.limit;
	}

	public static long sessionId() {
		var session = active;
		return session == null ? 0 : session.id;
	}

	public static synchronized void start(String label, Mode mode) {
		if (active != null) {
			throw new IllegalStateException("performance_capture_already_active");
		}

		active = new Session(text(label), mode);
	}

	public static void frame(int frame, long frameOriginNs) {
		var session = active;
		if (session != null) {
			session.frameOriginNs = frameOriginNs;
			session.frame = frame;
		}
	}

	public static int currentNode() {
		var buffer = LOCAL.get().buffer.get();
		return buffer == null || buffer.closed || buffer.depth == 0 ? 0 : buffer.stackNodes[buffer.depth - 1];
	}

	/// 沿同步栈查找指定阶段的执行上下文；不将另一个线程的任务当作父调用。
	public static int currentNode(Stage first, Stage second) {
		var buffer = LOCAL.get().buffer.get();
		if (buffer == null || buffer.closed) {
			return 0;
		}
		for (int depth = buffer.depth - 1; depth >= 0; depth--) {
			if (buffer.stackStages[depth] == first || buffer.stackStages[depth] == second) {
				return buffer.stackNodes[depth];
			}
		}
		return 0;
	}

	/// 共享定义可在不同目标和播放上下文中分别归属，查找不临时创建元数据。
	public static int knownNode(Object owner, int parent, int context) {
		var session = active;
		if (session == null || !session.accepting || owner == null) {
			return 0;
		}
		synchronized (session) {
			var scoped = session.scopedIdentities.get(owner);
			return scoped == null ? 0 : scoped.get(((long) parent << 32) | (context & 0xffffffffL));
		}
	}

	public static int node(Object owner, Node description, int context) {
		var session = active;
		if (session == null || !session.accepting || session.problem != null || owner == null) {
			return 0;
		}
		try {
			return session.scopedNode(owner, description, context);
		} catch (RuntimeException | LinkageError failure) {
			session.fail(failure);
			return 0;
		}
	}

	public static int knownNode(Object owner) {
		var session = active;
		if (session == null || !session.accepting || owner == null) {
			return 0;
		}

		var state = local(session);
		if (state.frame != session.frame || state.identities.size() >= MAX_LOCAL_IDENTITIES) {
			state.identities.clear();
			state.frame = session.frame;
		}

		var cached = state.identities.get(owner);
		if (cached != null) {
			return cached;
		}

		synchronized (session) {
			var id = session.identities.get(owner);
			if (id != null) {
				state.identities.put(owner, id);
				return id;
			}
		}

		return 0;
	}

	public static int describe(Object owner, Node description) {
		var session = active;
		if (session == null || !session.accepting) {
			return 0;
		}

		int id = node(owner, description);
		synchronized (session) {
			if (id > 0 && id < session.nodes.size()) {
				session.parents.set(id, description.parent());
				session.nodes.set(id, new Node(id, description.parent(), text(description.kind()),
						text(description.name()), text(description.definitionId()), text(description.resource())));
			}
		}

		return id;
	}

	public static int namedNode(int parent, String kind, String identity, String name, String resource) {
		var session = active;
		if (session == null || !session.accepting) {
			return 0;
		}

		synchronized (session) {
			if (!session.accepting) {
				return 0;
			}
			var existingKinds = session.namedNodes.get(parent);
			var existingIds = existingKinds == null ? null : existingKinds.get(kind);
			var known = existingIds == null ? null : existingIds.get(identity);
			if (known != null) {
				return known;
			}
			if (session.limit != null || session.nodes.size() >= EFFECTIVE_MAX_NODES) {
				session.droppedNodes++;
				session.limit(LimitReason.NODES);
				return 0;
			}

			var byKind = session.namedNodes.computeIfAbsent(parent, _ -> new HashMap<>());
			var byIdentity = byKind.computeIfAbsent(kind, _ -> new HashMap<>());
			var existing = byIdentity.get(identity);
			if (existing != null) {
				return existing;
			}

			int id = session.nodes.size();
			session.nodes.add(new Node(id, parent, text(kind), text(name), text(identity), text(resource)));
			session.parents.set(id, parent);
			byIdentity.put(text(identity), id);
			return id;
		}
	}

	public static int node(Object owner) {
		return node(owner, null);
	}

	public static int node(Object owner, @Nullable Node description) {
		var session = active;
		if (session == null || !session.accepting || owner == null) {
			return 0;
		}

		try {
			var local = local(session);
			if (local.frame != session.frame || local.identities.size() >= MAX_LOCAL_IDENTITIES) {
				local.identities.clear();
				local.frame = session.frame;
			}

			var cached = local.identities.get(owner);
			if (cached != null) {
				return cached;
			}

			int id = session.node(owner, description);
			local.identities.put(owner, id);
			return id;
		} catch (RuntimeException | LinkageError failure) {
			session.fail(failure);
			return 0;
		}
	}

	public static long begin(Stage stage, int node) {
		return begin(stage, node, Double.NaN);
	}

	public static void generation(int node, int generation) {
		var session = active;
		if (session != null && node > 0 && node < EFFECTIVE_MAX_NODES) {
			session.generations.set(node, generation);
		}
	}

	public static long begin(Stage stage, int node, double localTime) {
		var session = active;
		if (session == null || !session.accepting || session.problem != null || session.limit != null
				|| session.mode == Mode.OVERVIEW && stage.detailed) {
			return 0;
		}

		try {
			var buffer = buffer(session);
			if (buffer == null) {
				return 0;
			}

			synchronized (buffer) {
				if (buffer.closed || !session.accepting || session.limit != null) {
					return 0;
				}

				if (buffer.depth == MAX_DEPTH) {
					buffer.dropped++;
					buffer.droppedDepthSpans++;
					session.limit(LimitReason.DEPTH);
					return 0;
				}

				return buffer.begin(stage, node, session.frame, localTime);
			}
		} catch (RuntimeException | LinkageError failure) {
			session.fail(failure);
			return 0;
		}
	}

	public static void end(long token, boolean completed) {
		if (token == 0) {
			return;
		}

		var state = LOCAL.get();
		var buffer = state.buffer.get();
		if (buffer == null || token >>> EPOCH_SHIFT != (buffer.session.id & KEY_MASK)) {
			return;
		}

		synchronized (buffer) {
			if (buffer.closed) {
				return;
			}

			if (buffer.depth == 0 || buffer.tokens[buffer.depth - 1] != token) {
				buffer.incomplete++;
				return;
			}

			try {
				buffer.end(completed);
			} catch (RuntimeException | LinkageError failure) {
				buffer.session.fail(failure);
			}
		}
	}

	public static void counter(int node, Metric metric, long value) {
		var session = active;
		if (session == null || !session.accepting || session.problem != null) {
			return;
		}

		try {
			var buffer = buffer(session);
			if (buffer == null) {
				return;
			}

			synchronized (buffer) {
				if (buffer.closed) {
					return;
				}

				long key = ((long) node << 16) | metric.ordinal();
				var counter = buffer.counters.get(key);
				if (counter == null) {
					if (buffer.counters.size() >= EFFECTIVE_MAX_COUNTERS_PER_THREAD) {
						buffer.dropped++;
						buffer.deniedCounterRequests++;
						session.limit(LimitReason.COUNTERS);
						return;
					}

					counter = new MutableCounter();
					buffer.counters.put(key, counter);
				}

				counter.samples++;
				counter.total += value;
				counter.last = value;
				counter.maximum = Math.max(counter.maximum, value);
			}
		} catch (RuntimeException | LinkageError failure) {
			session.fail(failure);
		}
	}

	public static void mark(Stage stage, int node, String code, String detail, double value, double secondary) {
		recordMark(stage, node, code, detail, value, secondary, false);
	}

	public static void important(Stage stage, int node, String code, String detail, double value, double secondary) {
		recordMark(stage, node, code, detail, value, secondary, true);
	}

	private static void recordMark(Stage stage, int node, String code, String detail, double value, double secondary, boolean important) {
		var session = active;
		if (session == null || !session.accepting || session.problem != null) {
			return;
		}

		synchronized (session) {
			if (!session.accepting) {
				return;
			}

			var mark = new Mark(System.nanoTime(), session.frame, Thread.currentThread().threadId(),
					stage, node, text(code), text(detail), value, secondary);
			if (important) {
				if (session.importantMarks.size() < MAX_IMPORTANT_MARKS) {
					session.importantMarks.add(mark);
				} else {
					session.droppedImportantMarks++;
					session.limit(LimitReason.IMPORTANT_MARKS);
				}
				return;
			}
			if (session.marks.size() < MAX_MARKS) {
				session.marks.add(mark);
			} else {
				session.marks.set(session.markCursor++ % MAX_MARKS, mark);
				session.overwrittenMarks++;
			}
		}
	}

	public static @Nullable View view() {
		var session = active;
		return session == null ? null : session.view();
	}

	public static synchronized @Nullable Capture finish(String reason) {
		var session = active;
		if (session == null) {
			return null;
		}

		List<Buffer> buffers;
		synchronized (session) {
			session.accepting = false;
			active = null;
			buffers = List.copyOf(session.buffers);
		}

		var threads = new ArrayList<ThreadCapture>();
		for (var buffer : buffers) {
			synchronized (buffer) {
				buffer.closed = true;
				buffer.incomplete += buffer.depth;
				buffer.depth = 0;
				threads.add(new ThreadCapture(buffer));
			}
		}

		LOCAL.remove();
		return new Capture(session.view(), text(reason), System.nanoTime(), List.copyOf(threads));
	}

	private static String text(@Nullable String value) {
		return value == null ? "" : value.substring(0, Math.min(value.length(), MAX_TEXT));
	}

	private static LocalState local(Session session) {
		var local = LOCAL.get();
		if (local.session.get() != session) {
			local.session = new WeakReference<>(session);
			local.buffer.clear();
			local.identities.clear();
			local.frame = Integer.MIN_VALUE;
		}

		return local;
	}

	private static @Nullable Buffer buffer(Session session) {
		var local = local(session);
		var existing = local.buffer.get();
		if (existing != null) {
			return existing;
		}

		synchronized (session) {
			if (!session.accepting) {
				return null;
			}
			if (session.buffers.size() == MAX_THREADS) {
				session.droppedThreads++;
				session.limit(LimitReason.THREADS);
				return null;
			}

			var buffer = new Buffer(session, session.buffers.size() + 1);
			local.buffer = new WeakReference<>(buffer);
			session.buffers.add(buffer);
			return buffer;
		}
	}

	public enum Mode {
		OVERVIEW, DETAIL, GPU
	}

	public enum LimitReason {
		NODES, THREADS, AGGREGATES, COUNTERS, IMPORTANT_MARKS, DEPTH
	}

	public enum Stage {
		RENDER(false), EXTRACT(false), FX_CREATE(false), FX_COPY(false), FX_EMIT(false), FX_RESET(false),
		OBJECT_TICK(true), OBJECT_FRAME(true), OBJECT_EXTRACT(true), GEOMETRY(true),
		MODEL_PREPARE(true), MODEL_SAMPLE(true), MODEL_POSE(true), MODEL_PART(true), MATERIAL(true),
		UPLOAD(true), DRAW(true), BLOOM(false), ANIMATION(true), TRACK(true), CURVE(true),
		SEEK(false), REPLAY(false), UI_INPUT(false), UI_LAYOUT(true), UI_RENDER(true),
		MANUAL_INPUT(false), COMMAND_SEND(false), COMMAND_HANDLE(false), SKILL(false),
		CLIENT_PHASE(false), RESOURCE_LOAD(false), PREWARM(false), CLEANUP(false), EXPORT(false), ANIMATION_FRAME(true);

		private final boolean detailed;

		Stage(boolean detailed) {
			this.detailed = detailed;
		}
	}

	public enum Metric {
		LIVE_PARTICLES, BORN_PARTICLES, DRAW_JOBS, VERTICES, TRIANGLES, INPUT_VERTICES, INPUT_TRIANGLES, SUBMITTED_TRIANGLES, UPLOADED_BYTES,
		CACHE_HIT, CACHE_MISS, REQUESTS, COALESCED_REQUESTS, RESET_COUNT,
		REPLAY_REQUESTED_TICKS, REPLAY_TICKS, FULL_SIMULATION_TICKS, FAST_SIMULATION_TICKS,
		PREPARED_REMAINING, PREPARED_HIT, PREPARED_MISS, ACTIVE_TRACKS, KEYFRAMES,
		GUI_ELEMENTS, PIPELINE_CREATES, BUFFER_BYTES, BORROWED_BUFFERS
	}

	public record Limit(LimitReason reason, long timeNs, int frame) {
	}

	public record Quality(long deniedNodeRequests, long deniedThreadRequests, long droppedImportantMarks,
	                      long droppedRawSpans, long droppedDepthSpans, long deniedAggregateRequests,
	                      long deniedCounterRequests, @Nullable Limit limit) {
	}

	public record Node(int id, int parent, String kind, String name, String definitionId, String resource) {
	}

	public record Context(int node, int owner) {
	}

	public record Mark(long timeNs, int frame, long threadId, Stage stage, int node,
	                   String code, String detail, double value, double secondary) {
	}

	public record Event(long id, long parent, int frame, int node, Stage stage, int generation, double localTime,
	                    long startNs, long endNs,
	                    long selfNs, long allocatedBytes, long selfAllocatedBytes, long threadCpuNs, int flags) {
		public boolean completed() {
			return (flags & COMPLETED) != 0;
		}

		public boolean failed() {
			return (flags & FAILED) != 0;
		}
	}

	public record Aggregate(long threadId, int node, Stage stage, long calls, long inclusiveNs, long selfNs,
	                        long maximumNs, long allocatedBytes, long selfAllocatedBytes, long cpuNs,
	                        long allocationSamples, long cpuSamples, long failures, @Nullable Event maximumEvent) {
	}

	public record Counter(long threadId, int node, Metric metric, long samples, long total, long last, long maximum) {
	}

	public record View(long sessionId, String label, Mode mode, long startedNs, Instant startedAt, int frame,
	                   long frameOriginNs, List<Node> nodes, List<Aggregate> aggregates, List<Counter> counters,
	                   List<Mark> marks, long dropped, long overwritten, long incomplete, @Nullable String problem,
	                   Quality quality, List<Context> contexts) {
	}

	public record Capture(View view, String reason, long finishedNs, List<ThreadCapture> threads) {
	}

	public record Retention(long threadId, String threadName, long firstNs, long lastNs,
	                        long completed, long incomplete, long overwritten, long dropped) {
	}

	/// 冻结后只提供逐条读取，导出不额外复制整套事件数组。
	public static class ThreadCapture {
		private final Buffer buffer;

		private ThreadCapture(Buffer buffer) {
			this.buffer = buffer;
		}

		public long threadId() {
			return buffer.threadId;
		}

		public String threadName() {
			return buffer.threadName;
		}

		public void forEachEvent(Consumer<Event> consumer) {
			visit(Long.MIN_VALUE, Long.MAX_VALUE, false, consumer);
		}

		/// 只为相交的冻结事件构建记录；不按开始时帧号猜测跨帧归属。
		public void forEachEvent(long fromNs, long toNs, Consumer<Event> consumer) {
			visit(fromNs, toNs, true, consumer);
		}

		public Retention retention() {
			long firstNs = Long.MAX_VALUE;
			long lastNs = Long.MIN_VALUE;
			long completed = 0;
			long incomplete = 0;
			long first = Math.max(1, buffer.sequence - EVENTS_PER_THREAD + 1);
			for (long sequence = first; sequence <= buffer.sequence; sequence++) {
				int index = (int) sequence & EVENT_MASK;
				if (buffer.ids[index] != buffer.token(sequence)) {
					continue;
				}
				firstNs = Math.min(firstNs, buffer.starts[index]);
				lastNs = Math.max(lastNs, Math.max(buffer.starts[index], buffer.ends[index]));
				if ((buffer.flags[index] & COMPLETED) != 0) {
					completed++;
				} else {
					incomplete++;
				}
			}
			return new Retention(buffer.threadId, buffer.threadName,
					firstNs == Long.MAX_VALUE ? -1 : firstNs, lastNs == Long.MIN_VALUE ? -1 : lastNs,
					completed, incomplete, buffer.overwritten, buffer.dropped);
		}

		private void visit(long fromNs, long toNs, boolean filter, Consumer<Event> consumer) {
			long first = Math.max(1, buffer.sequence - EVENTS_PER_THREAD + 1);

			for (long sequence = first; sequence <= buffer.sequence; sequence++) {
				int index = (int) sequence & EVENT_MASK;
				if (buffer.ids[index] != buffer.token(sequence)) {
					continue;
				}

				if (Thread.currentThread().isInterrupted()) {
					throw new CancellationException();
				}
				if (filter && (buffer.starts[index] >= toNs
						|| (buffer.flags[index] & COMPLETED) != 0 && (buffer.ends[index] < fromNs || buffer.ends[index] == fromNs && buffer.starts[index] < fromNs))) {
					continue;
				}

				consumer.accept(new Event(buffer.ids[index], buffer.parents[index], buffer.frames[index],
						buffer.nodes[index], STAGES[buffer.stages[index]], buffer.generations[index], buffer.localTimes[index], buffer.starts[index],
						buffer.ends[index], buffer.self[index], buffer.allocations[index],
						buffer.selfAllocations[index], buffer.cpu[index], buffer.flags[index]));
			}
		}
	}

	/// 使用对象身份比较的弱键；查找复用临时键，不在每个计时入口分配 WeakReference。
	private static class WeakIdentityTable<V> {
		private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
		private final Map<IdentityReference, V> values = new HashMap<>();
		private final IdentityLookup lookup = new IdentityLookup();

		@SuppressWarnings("SuspiciousMethodCalls") // 临时查找键只参与身份哈希查询，不存入弱键表。
		private @Nullable V get(Object owner) {
			IdentityReference expired;
			while ((expired = (IdentityReference) queue.poll()) != null) {
				values.remove(expired);
			}

			lookup.owner = owner;
			try {
				return values.get(lookup);
			} finally {
				lookup.owner = null;
			}
		}

		private void put(Object owner, V value) {
			values.put(new IdentityReference(owner, queue), value);
		}

		private int size() {
			return values.size();
		}

		private void clear() {
			values.clear();
		}
	}

	private static class IdentityReference extends WeakReference<Object> {
		private final int hash;

		private IdentityReference(Object owner, ReferenceQueue<Object> queue) {
			super(owner, queue);
			hash = System.identityHashCode(owner);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object other) {
			var owner = get();
			return this == other || owner != null && (other instanceof IdentityReference reference && owner == reference.get()
					|| other instanceof IdentityLookup lookup && owner == lookup.owner);
		}
	}

	private static class IdentityLookup {
		private @Nullable Object owner;

		@Override
		public int hashCode() {
			return System.identityHashCode(owner);
		}

		@Override
		public boolean equals(Object other) {
			return this == other || owner != null && (other instanceof IdentityReference reference && owner == reference.get()
					|| other instanceof IdentityLookup lookup && owner == lookup.owner);
		}
	}

	private static class LocalState {
		private WeakReference<Session> session = new WeakReference<>(null);
		private WeakReference<Buffer> buffer = new WeakReference<>(null);
		private int frame;
		private final WeakIdentityTable<Integer> identities = new WeakIdentityTable<>();
	}

	private static class Session {
		private final long id = EPOCHS.incrementAndGet();
		private final String label;
		private final Mode mode;
		private final long startedNs = System.nanoTime();
		private final Instant startedAt = Instant.now();
		private volatile int frame = -1;
		private volatile long frameOriginNs;
		private volatile boolean accepting = true;
		private volatile @Nullable String problem;
		private volatile @Nullable Limit limit;
		private final WeakIdentityTable<Integer> identities = new WeakIdentityTable<>();
		private final WeakIdentityTable<Long2IntOpenHashMap> scopedIdentities = new WeakIdentityTable<>();
		private final List<Context> contexts = new ArrayList<>();
		private final List<Node> nodes = new ArrayList<>();
		private final AtomicIntegerArray parents = new AtomicIntegerArray(EFFECTIVE_MAX_NODES);
		private final AtomicIntegerArray generations = new AtomicIntegerArray(EFFECTIVE_MAX_NODES);
		private final Map<Integer, Map<String, Map<String, Integer>>> namedNodes = new HashMap<>();
		private final List<Buffer> buffers = new ArrayList<>();
		private final List<Mark> marks = new ArrayList<>();
		private final List<Mark> importantMarks = new ArrayList<>();
		private long droppedImportantMarks;
		private long overwrittenMarks;
		private int markCursor;
		private long droppedNodes;
		private long droppedThreads;

		private Session(String label, Mode mode) {
			this.label = label;
			this.mode = mode;
			nodes.add(new Node(0, 0, "shared", "unassigned/shared", "", ""));
		}

		private synchronized int node(Object owner, @Nullable Node description) {
			var existing = identities.get(owner);
			if (existing != null) {
				return existing;
			}

			int id = appendNode(description == null
					? new Node(0, 0, owner.getClass().getSimpleName(), owner.getClass().getSimpleName(), "", "") : description);
			if (id != 0) {
				identities.put(owner, id);
			}
			return id;
		}

		private synchronized int scopedNode(Object owner, Node description, int context) {
			var scoped = scopedIdentities.get(owner);
			long key = ((long) description.parent() << 32) | (context & 0xffffffffL);
			int existing = scoped == null ? 0 : scoped.get(key);
			if (existing != 0) {
				return existing;
			}
			int id = appendNode(description);
			if (id != 0) {
				if (scoped == null) {
					scoped = new Long2IntOpenHashMap();
					scopedIdentities.put(owner, scoped);
				}
				scoped.put(key, id);
				if (context != 0) {
					contexts.add(new Context(id, context));
				}
			}
			return id;
		}

		/// 仅在持有 Session 锁时追加，两种身份表共用字典预算与元数据快照。
		private int appendNode(Node description) {
			if (!accepting) {
				return 0;
			}
			if (limit != null || nodes.size() == EFFECTIVE_MAX_NODES) {
				droppedNodes++;
				limit(LimitReason.NODES);
				return 0;
			}
			int id = nodes.size();
			var node = new Node(id, description.parent(), text(description.kind()), text(description.name()),
					text(description.definitionId()), text(description.resource()));
			nodes.add(node);
			parents.set(id, node.parent());
			return id;
		}

		private int generation(int node) {
			for (int depth = 0; node > 0 && node < EFFECTIVE_MAX_NODES && depth < MAX_DEPTH; depth++) {
				int generation = generations.get(node);
				if (generation != 0) {
					return generation;
				}
				node = parents.get(node);
			}
			return 0;
		}

		/// 首个容量原因锁定；仅停止新增采样，已打开作用域仍沿原业务路径完成。
		private synchronized void limit(LimitReason reason) {
			if (accepting && limit == null) {
				limit = new Limit(reason, System.nanoTime(), frame);
			}
		}

		private void fail(Throwable failure) {
			if (problem == null) {
				problem = text(failure.getClass().getSimpleName() + ": " + failure.getMessage());
			}
		}

		private View view() {
			List<Buffer> current;
			List<Node> nodeView;
			List<Mark> markView;
			List<Context> contextView;
			long dropped;
			synchronized (this) {
				current = List.copyOf(buffers);
				nodeView = List.copyOf(nodes);
				contextView = List.copyOf(contexts);
				var orderedMarks = new ArrayList<>(marks);
				orderedMarks.addAll(importantMarks);
				orderedMarks.sort(Comparator.comparingLong(Mark::timeNs));
				markView = List.copyOf(orderedMarks);
				dropped = droppedNodes + droppedThreads + droppedImportantMarks;
			}

			var aggregates = new ArrayList<Aggregate>();
			var counters = new ArrayList<Counter>();
			long overwritten = overwrittenMarks;
			long incomplete = 0;
			long rawDrops = 0, depthDrops = 0, aggregateDenials = 0, counterDenials = 0;
			for (var buffer : current) {
				synchronized (buffer) {
					buffer.aggregates.long2ObjectEntrySet().fastForEach(entry -> {
						var value = entry.getValue();
						long key = entry.getLongKey();
						if (value.calls == 0) {
							return;
						}
						aggregates.add(new Aggregate(buffer.threadId, (int) (key >>> 16), STAGES[(int) key & KEY_MASK],
								value.calls, value.inclusive, value.self, value.maximum, value.allocated,
								value.selfAllocated, value.cpu, value.allocationSamples, value.cpuSamples, value.failures, value.maximumEvent));
					});
					buffer.counters.long2ObjectEntrySet().fastForEach(entry -> {
						var value = entry.getValue();
						long key = entry.getLongKey();
						counters.add(new Counter(buffer.threadId, (int) (key >>> 16), METRICS[(int) key & KEY_MASK],
								value.samples, value.total, value.last, value.maximum));
					});
					dropped += buffer.dropped;
					overwritten += buffer.overwritten;
					incomplete += buffer.incomplete;
					rawDrops += buffer.droppedRawSpans;
					depthDrops += buffer.droppedDepthSpans;
					aggregateDenials += buffer.deniedAggregateRequests;
					counterDenials += buffer.deniedCounterRequests;
				}
			}

			return new View(id, label, mode, startedNs, startedAt, frame, frameOriginNs, nodeView,
					List.copyOf(aggregates), List.copyOf(counters), markView, dropped, overwritten, incomplete, problem,
					new Quality(droppedNodes, droppedThreads, droppedImportantMarks, rawDrops, depthDrops, aggregateDenials, counterDenials, limit), contextView);
		}
	}

	private static class MutableAggregate {
		private long calls;
		private long inclusive;
		private long self;
		private long maximum;
		private long allocated;
		private long selfAllocated;
		private long cpu;
		private long allocationSamples;
		private long cpuSamples;
		private long failures;
		private @Nullable Event maximumEvent;
	}

	private static class MutableCounter {
		private long samples;
		private long total;
		private long last;
		private long maximum;
	}

	private static class Buffer {
		private final Session session;
		private final int threadIndex;
		private final long threadId = Thread.currentThread().threadId();
		private final String threadName = text(Thread.currentThread().getName());
		private boolean closed;
		private long sequence;
		private int depth;
		private int countedFrame = Integer.MIN_VALUE;
		private int frameSpans;
		private long dropped;
		private long droppedRawSpans, droppedDepthSpans, deniedAggregateRequests, deniedCounterRequests;
		private long overwritten;
		private long incomplete;
		private @Nullable ThreadMXBean bean;
		private boolean cpuAvailable;
		private boolean allocationAvailable;
		private final Long2ObjectOpenHashMap<MutableAggregate> aggregates = new Long2ObjectOpenHashMap<>();
		private final Long2ObjectOpenHashMap<MutableCounter> counters = new Long2ObjectOpenHashMap<>();
		private final long[] tokens = new long[MAX_DEPTH];
		private final long[] stackStarts = new long[MAX_DEPTH];
		private final long[] children = new long[MAX_DEPTH];
		private final long[] stackAllocations = new long[MAX_DEPTH];
		private final long[] childAllocations = new long[MAX_DEPTH];
		private final long[] stackCpu = new long[MAX_DEPTH];
		private final int[] slots = new int[MAX_DEPTH];
		private final int[] stackNodes = new int[MAX_DEPTH];
		private final int[] stackFrames = new int[MAX_DEPTH];
		private final int[] stackGenerations = new int[MAX_DEPTH];
		private final double[] stackLocalTimes = new double[MAX_DEPTH];
		private final Stage[] stackStages = new Stage[MAX_DEPTH];
		private final long[] ids = new long[EVENTS_PER_THREAD];
		private final long[] parents = new long[EVENTS_PER_THREAD];
		private final long[] starts = new long[EVENTS_PER_THREAD];
		private final long[] ends = new long[EVENTS_PER_THREAD];
		private final long[] self = new long[EVENTS_PER_THREAD];
		private final long[] allocations = new long[EVENTS_PER_THREAD];
		private final long[] selfAllocations = new long[EVENTS_PER_THREAD];
		private final long[] cpu = new long[EVENTS_PER_THREAD];
		private final int[] frames = new int[EVENTS_PER_THREAD];
		private final int[] nodes = new int[EVENTS_PER_THREAD];
		private final int[] generations = new int[EVENTS_PER_THREAD];
		private final double[] localTimes = new double[EVENTS_PER_THREAD];
		private final short[] stages = new short[EVENTS_PER_THREAD];
		private final byte[] flags = new byte[EVENTS_PER_THREAD];

		private Buffer(Session session, int threadIndex) {
			this.session = session;
			this.threadIndex = threadIndex;
			if (ManagementFactory.getThreadMXBean() instanceof ThreadMXBean management) {
				bean = management;
				allocationAvailable = management.isThreadAllocatedMemorySupported() && management.isThreadAllocatedMemoryEnabled();
				cpuAvailable = management.isCurrentThreadCpuTimeSupported() && management.isThreadCpuTimeEnabled();
			}
		}

		private long token(long sequence) {
			return (session.id & KEY_MASK) << EPOCH_SHIFT | (long) threadIndex << THREAD_SHIFT | sequence;
		}

		private long begin(Stage stage, int node, int frame, double localTime) {
			// 先为父调用预留聚合项，子调用耗尽容量时仍能保留根调用与最大项。
			long key = ((long) node << 16) | stage.ordinal();
			if (!aggregates.containsKey(key)) {
				if (aggregates.size() >= EFFECTIVE_MAX_AGGREGATES_PER_THREAD) {
					dropped++;
					deniedAggregateRequests++;
					session.limit(LimitReason.AGGREGATES);
					return 0;
				}
				aggregates.put(key, new MutableAggregate());
			}
			int position = depth++;
			long sequence = ++this.sequence;
			long token = token(sequence);
			if (countedFrame != frame) {
				countedFrame = frame;
				frameSpans = 0;
			}

			int index = (int) sequence & EVENT_MASK;
			if (++frameSpans > MAX_SPANS_PER_FRAME || session.mode == Mode.OVERVIEW) {
				index = -1;
				dropped += session.mode == Mode.OVERVIEW ? 0 : 1;
				droppedRawSpans += session.mode == Mode.OVERVIEW ? 0 : 1;
			} else {
				if (ids[index] != 0) {
					overwritten++;
				}
				ids[index] = token;
				parents[index] = position == 0 ? 0 : tokens[position - 1];
				frames[index] = frame;
				nodes[index] = node;
				generations[index] = session.generation(node);
				localTimes[index] = localTime;
				stages[index] = (short) stage.ordinal();
				ends[index] = 0;
				flags[index] = TRUNCATED;
			}

			tokens[position] = token;
			slots[position] = index;
			stackNodes[position] = node;
			stackFrames[position] = frame;
			stackGenerations[position] = session.generation(node);
			stackLocalTimes[position] = localTime;
			stackStages[position] = stage;
			children[position] = 0;
			childAllocations[position] = 0;
			stackAllocations[position] = session.mode == Mode.OVERVIEW ? UNAVAILABLE : allocated();
			stackCpu[position] = !stage.detailed && cpuAvailable && bean != null ? bean.getCurrentThreadCpuTime() : UNAVAILABLE;
			stackStarts[position] = System.nanoTime();
			if (index >= 0) {
				starts[index] = stackStarts[position];
			}
			return token;
		}

		private void end(boolean completed) {
			long now = System.nanoTime();
			int position = --depth;
			long elapsed = Math.max(0, now - stackStarts[position]);
			long own = Math.max(0, elapsed - children[position]);
			long allocation = stackAllocations[position] < 0 ? UNAVAILABLE : allocated() - stackAllocations[position];
			long ownAllocation = allocation < 0 || childAllocations[position] < 0
					? UNAVAILABLE : Math.max(0, allocation - childAllocations[position]);
			long threadCpu = stackCpu[position] < 0 || bean == null ? UNAVAILABLE : bean.getCurrentThreadCpuTime() - stackCpu[position];
			if (position > 0) {
				children[position - 1] += elapsed;
				childAllocations[position - 1] = allocation < 0 || childAllocations[position - 1] < 0
						? UNAVAILABLE : childAllocations[position - 1] + allocation;
			}

			int index = slots[position];
			if (index >= 0 && ids[index] == tokens[position]) {
				ends[index] = now;
				self[index] = own;
				allocations[index] = allocation;
				selfAllocations[index] = ownAllocation;
				cpu[index] = threadCpu;
				flags[index] = (byte) (completed ? COMPLETED : COMPLETED | FAILED);
			}

			long key = ((long) stackNodes[position] << 16) | stackStages[position].ordinal();
			var aggregate = aggregates.get(key);
			aggregate.calls++;
			aggregate.inclusive += elapsed;
			aggregate.self += own;
			if (aggregate.maximumEvent == null || elapsed > aggregate.maximum) {
				aggregate.maximumEvent = new Event(tokens[position], position == 0 ? 0 : tokens[position - 1],
						stackFrames[position], stackNodes[position], stackStages[position], stackGenerations[position],
						stackLocalTimes[position], stackStarts[position], now, own, allocation, ownAllocation, threadCpu,
						completed ? COMPLETED : COMPLETED | FAILED);
			}
			aggregate.maximum = Math.max(aggregate.maximum, elapsed);
			aggregate.failures += completed ? 0 : 1;
			if (allocation >= 0) {
				aggregate.allocationSamples++;
				aggregate.allocated += allocation;
				aggregate.selfAllocated += Math.max(0, ownAllocation);
			}
			if (threadCpu >= 0) {
				aggregate.cpuSamples++;
				aggregate.cpu += threadCpu;
			}
		}

		private long allocated() {
			return allocationAvailable && bean != null ? bean.getThreadAllocatedBytes(threadId) : UNAVAILABLE;
		}
	}
}
