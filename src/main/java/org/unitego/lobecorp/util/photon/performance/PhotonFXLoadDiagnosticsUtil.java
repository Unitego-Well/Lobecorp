package org.unitego.lobecorp.util.photon.performance;

import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/// 线程内加载与复制诊断；exclusive 仅扣除已计时子调用，仍包含等待、GC及未计时工作。
public class PhotonFXLoadDiagnosticsUtil {
	public enum Stage {
		QUERY, LOAD, TEMPLATE_PREPARE, TEMPLATE_NEXT, TEMPLATE_CREATE, OPEN, READ_COMPRESSED, VERSION_FIXES, DESERIALIZE,
		GUARD_FX, GUARD_OBJECT, GUARD_TIMELINE, GUARD_TRACK;

		private boolean boundary() {
			return this == QUERY || this == LOAD || this == TEMPLATE_PREPARE || this == TEMPLATE_NEXT;
		}
	}

	private static class RuntimeState {
		private static final ThreadLocal<Ledger> LEDGER = ThreadLocal.withInitial(Ledger::new);
		private static final AtomicLong IDS = new AtomicLong();
	}

	public static Scope begin(Stage stage, Object resource, boolean useCache) {
		if (!PhotonCastProfiler.enabled()) {
			return null;
		}

		return RuntimeState.LEDGER.get().begin(stage, resource, useCache, System.nanoTime(),
				RuntimeState.IDS.incrementAndGet());
	}

	public static Scope begin(Stage stage) {
		return begin(stage, null, false);
	}

	public static void finish(Scope scope) {
		if (scope == null) {
			return;
		}

		scope.finish(System.nanoTime());
		if (scope.stage.boundary()) {
			var thread = Thread.currentThread();
			Lobecorp.LOGGER.info("FXLOAD kind={} id={} parentId={} parentBoundaryId={} parentStage={} rootId={} resource={} openedResource={} threadId={} thread={} startNs={} endNs={} inclusiveNs={} exclusiveNs={} useCache={} enteredLoadFX={} cacheState={} returned={} failed={} stageCounts={} stageInclusiveNs={} stageExclusiveNs={} stageMaxNs={} stageFailedCounts={}",
					scope.kind(), scope.id, scope.parentId(), scope.parentBoundaryId(),
					scope.parent == null ? "none" : scope.parent.stage,
					scope.rootId(), token(scope.resource),
					token(scope.openedResource), thread.threadId(), token(thread.getName()), scope.startNs, scope.endNs,
					scope.endNs - scope.startNs, scope.exclusiveNs, scope.useCache, scope.enteredLoadFX,
					scope.cacheState(), scope.returned, scope.failed, scope.values(0), scope.values(1),
					scope.values(2), scope.values(3), scope.values(4));
		}

		if (scope.ledger.current == null) {
			RuntimeState.LEDGER.remove();
		}
	}

	public static void openedResource(Object resource) {
		if (!PhotonCastProfiler.enabled()) {
			return;
		}

		var current = RuntimeState.LEDGER.get().current;
		if (current != null) {
			current.owner.openedResource = resource;
		}
	}

	private static String token(Object value) {
		return value == null ? "none" : value.toString().replaceAll("\\s", "_");
	}

	/// 纯账本不读取时钟、注册表或运行配置；显式时间戳也用于确定性单元检验。
	public static class Ledger {
		private Scope current;

		public Scope begin(Stage stage, Object resource, boolean useCache, long startNs, long id) {
			var scope = new Scope(this, current, stage, resource, useCache, startNs, id);
			current = scope;
			if (stage == Stage.LOAD) {
				scope.enteredLoadFX = true;
				boolean queryFound = false;
				for (var parent = scope.parent; parent != null; parent = parent.parent) {
					if (parent.stage != Stage.QUERY || !queryFound) {
						parent.enteredLoadFX = true;
					}
					if (parent.stage == Stage.QUERY) {
						queryFound = true;
					}
				}
			}
			return scope;
		}

		public Scope current() {
			return current;
		}
	}

	public static class Totals {
		private long count;
		private long inclusiveNs;
		private long exclusiveNs;
		private long maxNs;
		private long failedCount;

		private void add(long inclusive, long exclusive, boolean failed) {
			count++;
			inclusiveNs += inclusive;
			exclusiveNs += exclusive;
			maxNs = Math.max(maxNs, inclusive);
			if (failed) {
				failedCount++;
			}
		}

		public long count() {
			return count;
		}

		public long inclusiveNs() {
			return inclusiveNs;
		}

		public long exclusiveNs() {
			return exclusiveNs;
		}

		public long maxNs() {
			return maxNs;
		}

		public long failedCount() {
			return failedCount;
		}
	}

	public static class Scope {
		private final Ledger ledger;
		private final Scope parent;
		private final Scope owner;
		private final Stage stage;
		private final Object resource;
		private final boolean useCache;
		private final long startNs;
		private final long id;
		private final Map<Stage, Totals> totals;
		private long childrenNs;
		private long endNs;
		private long exclusiveNs;
		private boolean enteredLoadFX;
		private boolean failed = true;
		private String returned = "none";
		private Object openedResource;

		private Scope(Ledger ledger, Scope parent, Stage stage, Object resource, boolean useCache, long startNs, long id) {
			this.ledger = ledger;
			this.parent = parent;
			this.stage = stage;
			this.resource = resource;
			this.useCache = useCache;
			this.startNs = startNs;
			this.id = id;
			owner = stage.boundary() || parent == null ? this : parent.owner;
			totals = owner == this ? new EnumMap<>(Stage.class) : owner.totals;
		}

		public void returned(Object value) {
			returned = value == null ? "null" : "value";
			failed = false;
		}

		public void succeeded() {
			returned = "void";
			failed = false;
		}

		public void finish(long ended) {
			endNs = ended;
			long inclusive = endNs - startNs;
			exclusiveNs = inclusive - childrenNs;
			totals.computeIfAbsent(stage, _ -> new Totals()).add(inclusive, exclusiveNs, failed);
			if (parent != null) {
				parent.childrenNs += inclusive;
				// 子边界单独输出并通过 parentBoundaryId 关联，不将其明细重复汇入父账本。
			}
			ledger.current = parent;
		}

		public Totals totals(Stage stage) {
			return totals.get(stage);
		}

		public long id() {
			return id;
		}

		public long parentId() {
			return parent == null ? 0 : parent.id;
		}

		public long parentBoundaryId() {
			var boundary = parent;
			while (boundary != null && !boundary.stage.boundary()) {
				boundary = boundary.parent;
			}
			return boundary == null ? 0 : boundary.id;
		}

		public long rootId() {
			var root = this;
			while (root.parent != null) {
				root = root.parent;
			}
			return root.id;
		}

		public boolean enteredLoadFX() {
			return enteredLoadFX;
		}

		private String kind() {
			return switch (stage) {
				case QUERY -> "query";
				case LOAD -> "load";
				case TEMPLATE_PREPARE -> "template_prepare";
				case TEMPLATE_NEXT -> "template_next";
				default -> "stage";
			};
		}

		public String cacheState() {
			if (stage != Stage.QUERY) {
				return "not_query";
			}
			if (!useCache) {
				return "cache_disabled";
			}
			return enteredLoadFX ? "load_entered" : "reuse_possible_wait";
		}

		private String values(int field) {
			var value = new StringBuilder();
			for (var entry : totals.entrySet()) {
				if (!value.isEmpty()) {
					value.append(',');
				}
				var total = entry.getValue();
				value.append(entry.getKey()).append(':').append(switch (field) {
					case 0 -> total.count;
					case 1 -> total.inclusiveNs;
					case 2 -> total.exclusiveNs;
					case 3 -> total.maxNs;
					default -> total.failedCount;
				});
			}
			return value.toString();
		}
	}
}
