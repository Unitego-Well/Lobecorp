package org.unitego.lobecorp.util.photon.performance;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.jetbrains.annotations.NotNull;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.performance.PhotonCastProfiler;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/// 线程内加载与复制诊断；exclusive 仅扣除已计时子调用，仍包含等待、GC及未计时工作。
public class PhotonFXLoadDiagnosticsUtil {
	public enum Stage {
		QUERY, LOAD, TEMPLATE_PREPARE, TEMPLATE_NEXT, TEMPLATE_CREATE, OPEN, READ_COMPRESSED, VERSION_FIXES, DESERIALIZE,
		GUARD_FX, GUARD_OBJECT, GUARD_TIMELINE, GUARD_TRACK,
		OBJECT, CODEC, OBJECT_VERSION_FIX, FACTORY_CREATE, CLASS_DESERIALIZE, DECLARED_METHODS, DECLARED_FIELDS,
		FIELD_KEY, FIELD_REF, FIELD_WRITE, FIELD_READ, SETTER, BEFORE_DESERIALIZE, ADDITIONAL_NBT, AFTER_DESERIALIZE,
		SUB_CONFIG, VALUE_DESERIALIZE;

		private boolean boundary() {
			return this == QUERY || this == LOAD || this == TEMPLATE_PREPARE || this == TEMPLATE_NEXT || this == OBJECT;
		}
	}

	private static class RuntimeState {
		private static final ThreadLocal<Ledger> LEDGER = ThreadLocal.withInitial(Ledger::new);
		private static final AtomicLong IDS = new AtomicLong();
		private static volatile Capture capture;
	}

	public static Scope begin(Stage stage, Object resource, boolean useCache) {
		if (!PhotonCastProfiler.enabled()) {
			return null;
		}

		var ledger = RuntimeState.LEDGER.get();
		ledger.capture = RuntimeState.capture;
		return ledger.begin(stage, resource, useCache, System.nanoTime(), RuntimeState.IDS.incrementAndGet());
	}

	public static Scope begin(Stage stage) {
		return begin(stage, null, false);
	}

	public static void startDetailedCapture(String label) {
		if (!PhotonCastProfiler.enabled()) {
			return;
		}

		synchronized (RuntimeState.class) {
			if (RuntimeState.capture != null) {
				throw new IllegalStateException("A detailed FX capture is already active");
			}
			RuntimeState.capture = new Capture(label, RuntimeState.IDS.incrementAndGet());
		}
	}

	public static JsonObject finishDetailedCapture() {
		synchronized (RuntimeState.class) {
			var capture = RuntimeState.capture;
			RuntimeState.capture = null;
			if (capture != null) {
				return capture.finish();
			}
		}

		var result = new JsonObject();
		result.addProperty("status", "inactive");
		result.addProperty("diagnosticsEnabled", PhotonCastProfiler.enabled());
		result.addProperty("label", "");
		result.addProperty("dropped", 0);
		result.addProperty("truncated", false);
		result.add("aggregates", new JsonArray());
		result.add("objects", new JsonArray());
		result.addProperty("complete", false);
		return result;
	}

	public static boolean skipDetails() {
		return !PhotonCastProfiler.enabled() || RuntimeState.LEDGER.get().current == null;
	}

	public static Scope beginDetail(Stage stage, String className, String field, boolean root) {
		if (skipDetails()) {
			return null;
		}

		var scope = begin(stage);
		scope.describe(className, field, root);
		return scope;
	}

	public static Scope beginDetail(Stage stage, String className, String field) {
		var scope = beginDetail(stage, className, field, false);
		if (scope != null && scope.parent != null) {
			scope.root = scope.parent.root;
		}
		return scope;
	}

	public static <T> T call(Scope scope, Operation<T> original, Object... arguments) {
		try {
			var result = original.call(arguments);
			if (scope != null) {
				scope.returned(result);
			}
			return result;
		} catch (Throwable failure) {
			if (scope != null) {
				scope.thrown(failure);
			}
			throw failure;
		} finally {
			finish(scope);
		}
	}

	public static Scope beginObject(String type, String name) {
		if (skipDetails()) {
			return null;
		}

		var scope = begin(Stage.OBJECT);
		scope.objectInput(type, name);
		return scope;
	}

	public static void finish(Scope scope) {
		if (scope == null) {
			return;
		}

		scope.finish(System.nanoTime());
		if (scope.stage.boundary()) {
			if (scope.stage == Stage.OBJECT) {
				Lobecorp.LOGGER.info("FXLOAD kind=object id={} parentBoundaryId={} captureId={} ordinal={} type={} name={} resultClass={} startNs={} endNs={} inclusiveNs={} exclusiveNs={} returned={} failed={} fullCapture={} stageCounts={}",
						scope.id, scope.parentBoundaryId(), scope.capture == null ? 0 : scope.capture.id,
						scope.objectOrdinal, token(scope.inputType), token(scope.inputName), scope.resultClass,
						scope.startNs, scope.endNs, scope.endNs - scope.startNs, scope.exclusiveNs,
						scope.returned, scope.failed, scope.capture != null, scope.values(0));
			} else {
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
		private Capture capture;
		private long objectOrdinal;

		public Ledger() {
		}

		public Ledger(Capture capture) {
			this.capture = capture;
		}

		public Scope begin(Stage stage, Object resource, boolean useCache, long startNs, long id) {
			var scope = new Scope(this, current, stage, resource, useCache, startNs, id);
			current = scope;
			if (stage == Stage.OBJECT) {
				scope.objectOrdinal = ++objectOrdinal;
			}
			if (scope.capture != null) {
				scope.capture.started();
			}
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

	/// 仅保存标量快照与聚合；结束后释放容器，未结束边界使 complete=false。
	public static class Capture {
		private final String label;
		private final long id;
		private final JsonArray objects = new JsonArray();
		private final Map<AggregateKey, Totals> aggregates = new LinkedHashMap<>();
		private boolean active = true;
		private long started;
		private long completed;
		private long objectCount;

		public Capture(String label, long id) {
			this.label = label;
			this.id = id;
		}

		private synchronized void started() {
			if (active) {
				started++;
			}
		}

		private synchronized void completed(@NotNull Scope scope) {
			if (!active) {
				return;
			}

			completed++;
			var object = scope;
			while (object != null && object.stage != Stage.OBJECT) {
				object = object.parent;
			}
			long objectId = object == null ? 0 : object.id;
			var key = new AggregateKey(objectId, scope.stage, scope.className, scope.field, scope.path, scope.root, scope.parentBoundaryId(), scope.resultClass);
			aggregates.computeIfAbsent(key, _ -> new Totals())
					.add(scope.endNs - scope.startNs, scope.exclusiveNs, scope.failed);
			if (scope.stage == Stage.OBJECT) {
				objectCount++;
				objects.add(objectJson(scope));
			}
		}

		private JsonObject objectJson(Scope scope) {
			var result = new JsonObject();
			result.addProperty("id", scope.id);
			result.addProperty("captureId", id);
			result.addProperty("ordinal", scope.objectOrdinal);
			result.addProperty("parentId", scope.parentId());
			result.addProperty("parentBoundaryId", scope.parentBoundaryId());
			result.addProperty("rootId", scope.rootId());
			long parentObjectId = 0;
			long fxBoundaryId = 0;
			String resource = "";
			for (var parent = scope.parent; parent != null; parent = parent.parent) {
				if (parentObjectId == 0 && parent.stage == Stage.OBJECT) {
					parentObjectId = parent.id;
				}
				if (fxBoundaryId == 0 && parent.stage.boundary() && parent.stage != Stage.OBJECT) {
					fxBoundaryId = parent.id;
				}
				if (resource.isEmpty() && parent.resource != null) {
					resource = parent.resource.toString();
				}
			}
			result.addProperty("parentObjectId", parentObjectId);
			result.addProperty("fxBoundaryId", fxBoundaryId);
			result.addProperty("threadId", Thread.currentThread().threadId());
			result.addProperty("resource", resource);
			result.addProperty("inputType", scope.inputType);
			result.addProperty("inputName", scope.inputName);
			result.addProperty("resultClass", scope.resultClass);
			result.addProperty("startNs", scope.startNs);
			result.addProperty("endNs", scope.endNs);
			result.addProperty("inclusiveNs", scope.endNs - scope.startNs);
			result.addProperty("exclusiveNs", scope.exclusiveNs);
			result.addProperty("returned", scope.returned);
			result.addProperty("failed", scope.failed);
			result.addProperty("exceptionClass", scope.exceptionClass);
			var observed = new JsonObject();
			for (var entry : scope.totals.entrySet()) {
				observed.addProperty(entry.getKey().name(), entry.getValue().count);
			}
			result.add("observedStageCounts", observed);
			var unobserved = new JsonArray();
			for (var stage : new Stage[]{Stage.CODEC, Stage.OBJECT_VERSION_FIX, Stage.FACTORY_CREATE, Stage.CLASS_DESERIALIZE}) {
				if (!scope.totals.containsKey(stage)) {
					unobserved.add(stage.name());
				}
			}
			result.add("unobservedStages", unobserved);
			return result;
		}

		private JsonObject metadataJson() {
			var result = new JsonObject();
			result.addProperty("label", label);
			result.addProperty("captureId", id);
			result.addProperty("status", active ? "finished" : "already_finished");
			result.addProperty("timeUnit", "ns");
			result.addProperty("startedBoundaries", started);
			result.addProperty("completedBoundaries", completed);
			result.addProperty("pendingBoundaries", started - completed);
			result.addProperty("objectCount", objectCount);
			result.addProperty("dropped", 0);
			result.addProperty("truncated", false);
			result.addProperty("complete", active && started == completed);
			result.addProperty("attributionComplete", false);
			return result;
		}

		private static JsonObject aggregateJson(AggregateKey key, Totals totals) {
			var row = new JsonObject();
			row.addProperty("objectId", key.objectId);
			row.addProperty("stage", key.stage.name());
			row.addProperty("class", key.className);
			row.addProperty("field", key.field);
			row.addProperty("path", key.path);
			row.addProperty("root", key.root);
			row.addProperty("parentBoundaryId", key.parentBoundaryId);
			row.addProperty("resultClass", key.resultClass);
			row.addProperty("count", totals.count);
			row.addProperty("inclusiveNs", totals.inclusiveNs);
			row.addProperty("exclusiveNs", totals.exclusiveNs);
			row.addProperty("maxNs", totals.maxNs);
			row.addProperty("failedCount", totals.failedCount);
			return row;
		}

		private static JsonObject coverageJson() {
			var coverage = new JsonObject();
			coverage.addProperty("codec", "IFXObject dispatch call, including overrides");
			coverage.addProperty("factory", "PersistedParser.createCodec anonymous decode Supplier.get only");
			coverage.addProperty("fixData", "FXObjectType base codec decode call only");
			coverage.addProperty("classes", "PersistedParser.deserializeInternal, superclass and nested config");
			coverage.addProperty("fields", "existing Field metadata; no extra field reads or reflection scans");
			return coverage;
		}

		private static JsonArray missingJson() {
			var missing = new JsonArray();
			missing.add("Custom codecs bypassing PersistedParser.createCodec: factory/fixData not guaranteed covered; absent counters mean unobserved, never zero cost");
			missing.add("CLASS_DESERIALIZE exclusive includes annotation checks, map/ops conversions, access setup, loops and all other unhooked work");
			missing.add("OBJECT exclusive includes codec dispatch/parse/result handling and other unhooked work");
			missing.add("Timings include instrumentation, allocation, logging in enclosing scopes, GC and waits; no overhead subtraction");
			return missing;
		}

		public synchronized JsonObject finish() {
			var result = metadataJson();
			result.add("objects", objects.deepCopy());
			var rows = new JsonArray();
			for (var entry : aggregates.entrySet()) {
				rows.add(aggregateJson(entry.getKey(), entry.getValue()));
			}
			result.add("aggregates", rows);
			result.add("coverage", coverageJson());
			result.add("missing", missingJson());
			active = false;
			objects.asList().clear();
			aggregates.clear();
			return result;
		}
	}

	private record AggregateKey(long objectId, Stage stage, String className, String field, String path, boolean root,
	                            long parentBoundaryId, String resultClass) {
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
		private final Capture capture;
		private String className = "";
		private String field = "";
		private String path = "";
		private boolean root;
		private String inputType = "";
		private String inputName = "";
		private String resultClass = "none";
		private String exceptionClass = "";
		private long objectOrdinal;
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
			capture = ledger.capture;
			if (parent != null) {
				className = parent.className;
				path = parent.path;
				root = parent.root;
			}
			owner = stage.boundary() || parent == null ? this : parent.owner;
			totals = owner == this ? new EnumMap<>(Stage.class) : owner.totals;
		}

		public void describe(String declaringClass, String fieldName, boolean isRoot) {
			if (declaringClass != null && !declaringClass.isEmpty()) {
				className = declaringClass;
			}
			field = fieldName == null ? "" : fieldName;
			root = isRoot;
			if (!field.isEmpty()) {
				path += "/" + className + "#" + field;
			}
		}

		public void objectInput(String type, String name) {
			inputType = type;
			inputName = name;
		}

		public void thrown(Throwable failure) {
			exceptionClass = failure.getClass().getName();
		}

		public void returned(Object value) {
			returned = value == null ? "null" : "value";
			if (stage == Stage.OBJECT || stage == Stage.FACTORY_CREATE) {
				resultClass = value == null ? "null" : value.getClass().getName();
			}
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
			if (capture != null) {
				capture.completed(this);
			}
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
				case OBJECT -> "object";
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
