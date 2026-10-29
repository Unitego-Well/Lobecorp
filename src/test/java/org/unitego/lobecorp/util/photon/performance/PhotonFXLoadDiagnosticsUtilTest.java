package org.unitego.lobecorp.util.photon.performance;

import org.junit.jupiter.api.Test;
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
}
