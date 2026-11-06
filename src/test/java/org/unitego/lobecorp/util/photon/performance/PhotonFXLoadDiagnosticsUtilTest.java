package org.unitego.lobecorp.util.photon.performance;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Ledger;
import org.unitego.lobecorp.util.photon.performance.PhotonFXLoadDiagnosticsUtil.Stage;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonFXLoadDiagnosticsUtilTest {
	@Test
	public void nestedStagesSubtractOnlyDirectChildren() {
		var ledger = new Ledger();
		var load = ledger.begin(Stage.LOAD, "test:fx", false, 0, 1);
		var deserialize = ledger.begin(Stage.DESERIALIZE, null, false, 10, 2);
		var guard = ledger.begin(Stage.GUARD_FX, null, false, 20, 3);
		guard.succeeded();
		guard.finish(40);
		var object = ledger.begin(Stage.GUARD_OBJECT, null, false, 50, 4);
		object.succeeded();
		object.finish(60);
		deserialize.succeeded();
		deserialize.finish(80);
		load.returned("fx");
		load.finish(100);

		assertEquals(2, guard.parentId());
		assertEquals(1, guard.parentBoundaryId());
		assertEquals(30, load.totals(Stage.LOAD).exclusiveNs());
		assertEquals(40, load.totals(Stage.DESERIALIZE).exclusiveNs());
		assertEquals(20, load.totals(Stage.GUARD_FX).exclusiveNs());
		assertEquals(10, load.totals(Stage.GUARD_OBJECT).exclusiveNs());
		assertEquals(100, load.totals(Stage.LOAD).exclusiveNs()
				+ load.totals(Stage.DESERIALIZE).exclusiveNs()
				+ load.totals(Stage.GUARD_FX).exclusiveNs()
				+ load.totals(Stage.GUARD_OBJECT).exclusiveNs());
		assertNull(ledger.current());
	}

	@Test
	public void failedStagesSettleInFinallyAndRestoreParent() {
		var ledger = new Ledger();
		var template = ledger.begin(Stage.TEMPLATE_PREPARE, "test:fx", false, 0, 1);
		var failure = new IllegalArgumentException("unsupported");
		var guard = ledger.begin(Stage.GUARD_FX, null, false, 10, 2);

		var thrown = assertThrows(IllegalArgumentException.class, () -> {
			try {
				throw failure;
			} finally {
				guard.finish(40);
			}
		});

		assertSame(failure, thrown);
		assertSame(template, ledger.current());
		var second = ledger.begin(Stage.GUARD_FX, null, false, 50, 3);
		second.succeeded();
		second.finish(70);
		template.finish(100);
		assertEquals(2, template.totals(Stage.GUARD_FX).count());
		assertEquals(1, template.totals(Stage.GUARD_FX).failedCount());
		assertEquals(50, template.totals(Stage.GUARD_FX).inclusiveNs());
		assertEquals(30, template.totals(Stage.GUARD_FX).maxNs());
		assertEquals(50, template.totals(Stage.TEMPLATE_PREPARE).exclusiveNs());
		assertNull(ledger.current());
	}

	@Test
	public void nestedQueriesKeepTheirLoadClassificationAndLedgersSeparate() {
		var ledger = new Ledger();
		var query = ledger.begin(Stage.QUERY, "test:outer", true, 0, 1);
		var nested = ledger.begin(Stage.QUERY, "test:inner", true, 10, 2);
		var load = ledger.begin(Stage.LOAD, "test:inner", false, 20, 3);
		var read = ledger.begin(Stage.READ_COMPRESSED, null, false, 25, 4);
		read.returned("nbt");
		read.finish(40);
		load.returned("fx");
		load.finish(60);
		nested.returned("fx");
		nested.finish(70);
		query.returned("fx");
		query.finish(100);

		assertFalse(query.enteredLoadFX());
		assertEquals("reuse_possible_wait", query.cacheState());
		assertTrue(nested.enteredLoadFX());
		assertEquals("load_entered", nested.cacheState());
		assertEquals(1, nested.parentId());
		assertEquals(1, load.rootId());
		assertNull(query.totals(Stage.READ_COMPRESSED));
		assertNull(nested.totals(Stage.READ_COMPRESSED));
		assertEquals(15, load.totals(Stage.READ_COMPRESSED).inclusiveNs());
		assertNull(nested.totals(Stage.LOAD));
		assertEquals(40, load.totals(Stage.LOAD).inclusiveNs());
		assertEquals(20, nested.totals(Stage.QUERY).exclusiveNs());
		assertEquals(40, query.totals(Stage.QUERY).exclusiveNs());
		assertEquals(100, query.totals(Stage.QUERY).exclusiveNs()
				+ nested.totals(Stage.QUERY).exclusiveNs()
				+ load.totals(Stage.LOAD).exclusiveNs()
				+ load.totals(Stage.READ_COMPRESSED).exclusiveNs());
		assertNull(ledger.current());
	}

	@Test
	public void consecutiveQueriesAndIndependentLedgersDoNotShareState() {
		var firstLedger = new Ledger();
		var cold = firstLedger.begin(Stage.QUERY, "test:fx", true, 0, 1);
		var load = firstLedger.begin(Stage.LOAD, "test:fx", false, 10, 2);
		load.returned(null);
		load.finish(30);
		cold.returned(null);
		cold.finish(40);
		var reused = firstLedger.begin(Stage.QUERY, "test:fx", true, 50, 3);
		reused.returned("fx");
		reused.finish(60);
		var otherLedger = new Ledger();
		var uncached = otherLedger.begin(Stage.QUERY, "test:fx", false, 0, 4);
		uncached.finish(100);

		assertEquals("load_entered", cold.cacheState());
		assertEquals(0, cold.totals(Stage.QUERY).failedCount());
		assertEquals("reuse_possible_wait", reused.cacheState());
		assertFalse(reused.enteredLoadFX());
		assertEquals("cache_disabled", uncached.cacheState());
		assertEquals(1, uncached.totals(Stage.QUERY).failedCount());
		assertNull(firstLedger.current());
		assertNull(otherLedger.current());
	}

	@Test
	public void detailedNestedExclusiveTimesConserveElapsedTimeAcrossObjectBoundary() {
		var capture = new PhotonFXLoadDiagnosticsUtil.Capture("nested", 90);
		var ledger = new Ledger(capture);
		var load = ledger.begin(Stage.LOAD, "test:fx", false, 0, 1);
		var object = ledger.begin(Stage.OBJECT, null, false, 10, 2);
		object.objectInput("test:emitter", "beam");
		var clazz = ledger.begin(Stage.CLASS_DESERIALIZE, null, false, 20, 3);
		clazz.describe("Emitter", "", true);
		var field = ledger.begin(Stage.FIELD_WRITE, null, false, 25, 4);
		field.describe("Emitter", "size", true);
		field.succeeded();
		field.finish(35);
		var sub = ledger.begin(Stage.SUB_CONFIG, null, false, 40, 5);
		sub.describe("Emitter", "config", true);
		var child = ledger.begin(Stage.CLASS_DESERIALIZE, null, false, 45, 6);
		child.describe("Config", "", true);
		child.succeeded();
		child.finish(60);
		sub.succeeded();
		sub.finish(65);
		clazz.succeeded();
		clazz.finish(80);
		object.returned("object");
		object.finish(90);
		load.returned("fx");
		load.finish(100);

		var json = capture.finish();
		long sum = 0;
		for (var row : json.getAsJsonArray("aggregates")) {
			sum += row.getAsJsonObject().get("exclusiveNs").getAsLong();
		}
		assertEquals(100, sum);
		assertTrue(json.get("complete").getAsBoolean());
		assertEquals(0, json.get("pendingBoundaries").getAsLong());
		assertEquals(0, json.get("dropped").getAsLong());
		var details = json.getAsJsonArray("objects").get(0).getAsJsonObject();
		assertEquals(80, details.get("inclusiveNs").getAsLong());
		assertEquals(20, details.get("exclusiveNs").getAsLong());
		assertEquals(1, details.get("parentBoundaryId").getAsLong());
		assertEquals("test:fx", details.get("resource").getAsString());
		assertEquals("beam", details.get("inputName").getAsString());
		assertEquals(2, details.getAsJsonObject("observedStageCounts").get("CLASS_DESERIALIZE").getAsLong());
		assertEquals(2, child.parentBoundaryId());
		assertNull(ledger.current());
	}

	@Test
	public void sameNamedFieldsRemainDistinctByObjectDeclaringClassAndConfigPath() {
		var capture = new PhotonFXLoadDiagnosticsUtil.Capture("fields", 91);
		var ledger = new Ledger(capture);
		var first = ledger.begin(Stage.OBJECT, null, false, 0, 1);
		var left = ledger.begin(Stage.SUB_CONFIG, null, false, 1, 2);
		left.describe("Emitter", "left", true);
		var leftWrite = ledger.begin(Stage.FIELD_WRITE, null, false, 2, 3);
		leftWrite.describe("Config", "value", true);
		leftWrite.succeeded();
		leftWrite.finish(3);
		left.succeeded();
		left.finish(4);
		var right = ledger.begin(Stage.SUB_CONFIG, null, false, 5, 4);
		right.describe("Emitter", "right", true);
		var rightWrite = ledger.begin(Stage.FIELD_WRITE, null, false, 6, 5);
		rightWrite.describe("Config", "value", true);
		rightWrite.succeeded();
		rightWrite.finish(7);
		right.succeeded();
		right.finish(8);
		var inherited = ledger.begin(Stage.FIELD_WRITE, null, false, 9, 6);
		inherited.describe("BaseConfig", "value", false);
		inherited.succeeded();
		inherited.finish(10);
		first.returned("first");
		first.finish(11);
		var second = ledger.begin(Stage.OBJECT, null, false, 12, 7);
		var secondWrite = ledger.begin(Stage.FIELD_WRITE, null, false, 13, 8);
		secondWrite.describe("Config", "value", true);
		secondWrite.succeeded();
		secondWrite.finish(14);
		second.returned("second");
		second.finish(15);

		var keys = new HashSet<String>();
		for (var element : capture.finish().getAsJsonArray("aggregates")) {
			var row = element.getAsJsonObject();
			if (row.get("stage").getAsString().equals("FIELD_WRITE")) {
				assertEquals(1, row.get("count").getAsLong());
				keys.add(row.get("objectId").getAsString() + ":" + row.get("path").getAsString());
			}
		}
		assertEquals(4, keys.size());
		assertTrue(keys.contains("1:/Emitter#left/Config#value"));
		assertTrue(keys.contains("1:/Emitter#right/Config#value"));
		assertTrue(keys.contains("1:/BaseConfig#value"));
		assertTrue(keys.contains("7:/Config#value"));
	}

	@Test
	public void nullAndThrownObjectsAreDifferentAndNestedObjectsRetainTheirParent() {
		var capture = new PhotonFXLoadDiagnosticsUtil.Capture("outcomes", 92);
		var ledger = new Ledger(capture);
		var outer = ledger.begin(Stage.OBJECT, null, false, 0, 1);
		var nullable = ledger.begin(Stage.OBJECT, null, false, 1, 2);
		nullable.returned(null);
		nullable.finish(2);
		var failed = ledger.begin(Stage.OBJECT, null, false, 3, 3);
		failed.thrown(new IllegalArgumentException("not exported"));
		failed.finish(4);
		outer.returned("outer");
		outer.finish(5);

		var objects = capture.finish().getAsJsonArray("objects");
		var nullObject = objects.get(0).getAsJsonObject();
		var failedObject = objects.get(1).getAsJsonObject();
		assertEquals("null", nullObject.get("returned").getAsString());
		assertEquals("null", nullObject.get("resultClass").getAsString());
		assertFalse(nullObject.get("failed").getAsBoolean());
		assertTrue(failedObject.get("failed").getAsBoolean());
		assertEquals(IllegalArgumentException.class.getName(), failedObject.get("exceptionClass").getAsString());
		assertFalse(failedObject.toString().contains("not exported"));
		assertEquals(1, nullObject.get("parentObjectId").getAsLong());
		assertEquals(1, failedObject.get("parentBoundaryId").getAsLong());
		assertEquals(2, nullObject.get("ordinal").getAsLong());
	}

	@Test
	public void stoppingClearsRecordsAndLateCompletionsCannotContaminateNextCapture() {
		var first = new PhotonFXLoadDiagnosticsUtil.Capture("first", 93);
		var firstLedger = new Ledger(first);
		var old = firstLedger.begin(Stage.OBJECT, null, false, 0, 1);
		var stopped = first.finish();
		assertFalse(stopped.get("complete").getAsBoolean());
		assertEquals(1, stopped.get("pendingBoundaries").getAsLong());

		var next = new PhotonFXLoadDiagnosticsUtil.Capture("next", 94);
		var nextLedger = new Ledger(next);
		var current = nextLedger.begin(Stage.OBJECT, null, false, 2, 2);
		old.returned("old");
		old.finish(3);
		current.returned(null);
		current.finish(4);
		var exported = next.finish();
		assertTrue(exported.get("complete").getAsBoolean());
		assertEquals("next", exported.get("label").getAsString());
		assertEquals(1, exported.getAsJsonArray("objects").size());
		assertEquals(94, exported.getAsJsonArray("objects").get(0).getAsJsonObject().get("captureId").getAsLong());
		assertTrue(first.finish().getAsJsonArray("objects").isEmpty());
		assertTrue(next.finish().getAsJsonArray("aggregates").isEmpty());

		var uncaptured = new Ledger();
		var plain = uncaptured.begin(Stage.OBJECT, null, false, 5, 3);
		plain.returned(null);
		plain.finish(6);
		assertNull(uncaptured.current());
		assertTrue(next.finish().getAsJsonArray("objects").isEmpty());
	}

	@Test
	public void repeatedClassWorkAggregatesCountMaximumAndFailuresWithoutLosingFactoryType() {
		var capture = new PhotonFXLoadDiagnosticsUtil.Capture("aggregate", 95);
		var ledger = new Ledger(capture);
		var object = ledger.begin(Stage.OBJECT, null, false, 0, 1);
		var factory = ledger.begin(Stage.FACTORY_CREATE, null, false, 1, 2);
		factory.returned("created");
		factory.finish(3);
		for (int i = 0; i < 2; i++) {
			var read = ledger.begin(Stage.DECLARED_FIELDS, null, false, 4 + i * 10L, 3 + i);
			read.describe("Config", "", true);
			if (i == 0) {
				read.succeeded();
			}
			read.finish(9 + i * 12L);
		}
		object.returned("created");
		object.finish(30);

		var json = capture.finish();
		for (var element : json.getAsJsonArray("aggregates")) {
			var row = element.getAsJsonObject();
			if (row.get("stage").getAsString().equals("DECLARED_FIELDS")) {
				assertEquals(2, row.get("count").getAsLong());
				assertEquals(12, row.get("inclusiveNs").getAsLong());
				assertEquals(7, row.get("maxNs").getAsLong());
				assertEquals(1, row.get("failedCount").getAsLong());
			} else if (row.get("stage").getAsString().equals("FACTORY_CREATE")) {
				assertEquals(String.class.getName(), row.get("resultClass").getAsString());
			}
		}
		assertFalse(json.get("attributionComplete").getAsBoolean());
		assertTrue(json.getAsJsonArray("objects").get(0).getAsJsonObject().getAsJsonArray("unobservedStages").toString().contains("OBJECT_VERSION_FIX"));
	}

}
