package org.unitego.lobecorp.util.photon.creation;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PhotonIndexedMapTest {
	@Test
	@SuppressWarnings("SuspiciousMethodCalls")
	public void canonicalConstructionMatchesEveryLinkedHashMapPrefixIncludingRootNullAndDuplicates() {
		var keys = keys(100);
		keys.addFirst(new UUID(0, 0));
		keys.add(null);
		keys.add(keys.get(5));
		var layout = PhotonIndexedMap.compile(keys);
		var actual = new PhotonIndexedMap<>(layout, String[]::new);
		var expected = new LinkedHashMap<UUID, String>();
		for (int index = 0; index < keys.size(); index++) {
			var key = keys.get(index);
			String value = index % 3 == 0 ? null : "v" + index;
			assertEquals(expected.put(key, value), actual.put(key, value));
			equivalent(expected, actual);
			assertFalse(actual.isMaterialized());
		}
		assertTrue(actual.containsKey(null));
		assertNull(actual.get(new UUID(-1, -1)));
		assertFalse(actual.containsKey("wrong type"));
		assertEquals(0, actual.materializationCount());
	}

	@Test
	public void collisionTreeBinsAndResizePrefixesHaveNativeIterationOrder() {
		var keys = new ArrayList<UUID>();
		for (int index = 0; index < 100; index++) {
			keys.add(new UUID(index, index));
		}
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		var expected = new LinkedHashMap<UUID, String>();
		for (var key : keys) {
			expected.put(key, key.toString());
			actual.put(key, key.toString());
			equivalent(expected, actual);
		}
		var a = actual.entrySet().iterator();
		var e = expected.entrySet().iterator();
		for (int index = 0; index < 30; index++) {
			assertEquals(e.next(), a.next());
			e.remove();
			a.remove();
		}
		equivalent(expected, actual);
		assertEquals(0, actual.materializationCount());
	}

	@Test
	public void sharedLayoutNeverSharesMutableValuesOrPresence() {
		var keys = keys(10);
		var layout = PhotonIndexedMap.compile(keys);
		var first = new PhotonIndexedMap<>(layout, String[]::new);
		var second = new PhotonIndexedMap<>(layout, String[]::new);
		first.put(keys.getFirst(), null);
		assertTrue(first.containsKey(keys.getFirst()));
		assertFalse(second.containsKey(keys.getFirst()));
		second.put(keys.getFirst(), "second");
		assertNull(first.get(keys.getFirst()));
		assertEquals("second", second.get(keys.getFirst()));
	}

	@Test
	public void dynamicEditsMaterializeOnceAndKeepObjectsViewsAndOrder() {
		var keys = keys(30);
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		var expected = new LinkedHashMap<UUID, String>();
		for (var key : keys) {
			String value = new String(key.toString().toCharArray());
			expected.put(key, value);
			actual.put(key, value);
		}
		var values = actual.values();
		var entries = actual.entrySet();
		var before = actual.get(keys.get(4));
		var external = new UUID(500, 600);
		actual.put(external, null);
		expected.put(external, null);
		assertSame(before, actual.get(keys.get(4)));
		assertEquals(expected.size(), values.size());
		assertEquals(expected.entrySet(), entries);
		equivalent(expected, actual);
		assertEquals(expected.remove(keys.get(2)), actual.remove(keys.get(2)));
		expected.put(keys.get(2), "again");
		actual.put(keys.get(2), "again");
		equivalent(expected, actual);
		assertEquals(1, actual.materializationCount());
		actual.clear();
		expected.clear();
		for (var key : keys.reversed()) {
			actual.put(key, "after");
			expected.put(key, "after");
		}
		equivalent(expected, actual);
	}

	@Test
	@SuppressWarnings({"RedundantCollectionOperation", "ConstantValue"})
	public void entriesSetValueNullValuesIteratorRemoveAndFailFastMatchNative() {
		var keys = keys(20);
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		var expected = new LinkedHashMap<UUID, String>();
		keys.forEach(key -> {
			actual.put(key, null);
			expected.put(key, null);
		});
		var ai = actual.entrySet().iterator();
		var ei = expected.entrySet().iterator();
		var ae = ai.next();
		var ee = ei.next();
		assertEquals(ee.setValue("updated"), ae.setValue("updated"));
		assertEquals(ee, ae);
		assertEquals(ee.hashCode(), ae.hashCode());
		ai.remove();
		ei.remove();
		assertThrows(IllegalStateException.class, ai::remove);
		assertThrows(IllegalStateException.class, ei::remove);
		equivalent(expected, actual);
		assertEquals(expected.values().remove(null), actual.values().remove(null));
		equivalent(expected, actual);
		var failFast = actual.entrySet().iterator();
		actual.put(new UUID(400, 500), "new");
		assertThrows(ConcurrentModificationException.class, failFast::next);
		actual.entrySet().clear();
		assertTrue(actual.isEmpty());
	}

	@Test
	public void randomizedMapDefaultMethodsAndDynamicMutationRemainEquivalent() {
		var keys = keys(80);
		keys.add(null);
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		var expected = new LinkedHashMap<UUID, String>();
		var random = new Random(310_2026);
		for (int step = 0; step < 2000; step++) {
			var key = keys.get(random.nextInt(keys.size()));
			String value = step % 4 == 0 ? null : "value" + step;
			switch (random.nextInt(8)) {
				case 0 -> assertEquals(expected.put(key, value), actual.put(key, value));
				case 1 -> assertEquals(expected.remove(key), actual.remove(key));
				case 2 -> assertEquals(expected.putIfAbsent(key, value), actual.putIfAbsent(key, value));
				case 3 -> assertEquals(expected.compute(key, (_, _) -> value),
						actual.compute(key, (_, _) -> value));
				case 4 -> assertEquals(expected.replace(key, value), actual.replace(key, value));
				case 5 -> assertEquals(expected.remove(key, value), actual.remove(key, value));
				case 6 -> {
					expected.replaceAll((_, previous) -> previous == null ? "filled" : previous);
					actual.replaceAll((_, previous) -> previous == null ? "filled" : previous);
				}
				case 7 -> {
					if (step % 31 == 0) {
						expected.clear();
						actual.clear();
					}
				}
			}
			equivalent(expected, actual);
		}
		assertEquals(1, actual.materializationCount());
	}

	@Test
	@SuppressWarnings("RedundantCollectionOperation")
	public void dynamicComputeMergeBulkAndViewRemovalMatchNativeCollisionOrder() {
		var keys = new ArrayList<UUID>();
		for (int index = 0; index < 100; index++) {
			keys.add(new UUID(index, index));
		}
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		var expected = new LinkedHashMap<UUID, String>();
		for (int index = 0; index < 40; index++) {
			expected.put(keys.get(index), "v");
			actual.put(keys.get(index), "v");
		}
		assertEquals(expected.compute(keys.get(50), (_, _) -> "computed"),
				actual.compute(keys.get(50), (_, _) -> "computed"));
		equivalent(expected, actual);
		assertEquals(expected.computeIfAbsent(keys.get(51), _ -> "absent"),
				actual.computeIfAbsent(keys.get(51), _ -> "absent"));
		equivalent(expected, actual);
		assertEquals(expected.computeIfPresent(keys.get(51), (_, _) -> null),
				actual.computeIfPresent(keys.get(51), (_, _) -> null));
		assertEquals(expected.merge(keys.get(52), "merge", String::concat),
				actual.merge(keys.get(52), "merge", String::concat));
		equivalent(expected, actual);
		assertEquals(expected.keySet().remove(keys.get(20)), actual.keySet().remove(keys.get(20)));
		equivalent(expected, actual);
		var entry = Map.entry(keys.get(21), "v");
		assertEquals(expected.entrySet().remove(entry), actual.entrySet().remove(entry));
		equivalent(expected, actual);
		var additions = new LinkedHashMap<UUID, String>();
		keys.forEach(key -> additions.put(key, "bulk"));
		expected.putAll(additions);
		actual.putAll(additions);
		equivalent(expected, actual);
	}

	@Test
	public void shuffledKnownInsertionsAndReplacementsKeepNativeOrderWithoutMaterializing() {
		var keys = keys(24);
		keys.add(null);
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		var expected = new LinkedHashMap<UUID, String>();
		var random = new Random(26129);
		for (int step = 0; step < 1000; step++) {
			var key = keys.get(random.nextInt(keys.size()));
			String value = step % 3 == 0 ? null : "v" + step;
			if (step % 4 == 0) {
				assertEquals(expected.remove(key), actual.remove(key));
			} else {
				assertEquals(expected.put(key, value), actual.put(key, value));
			}

			equivalent(expected, actual);
			assertFalse(actual.isMaterialized());
		}
		assertEquals(0, actual.materializationCount());
	}

	@Test
	public void childBeforeParentDetachAndReattachMatchesNativeAcrossLeases() {
		var keys = keys(4);
		var root = keys.get(0);
		var child = keys.get(1);
		var sibling = keys.get(2);
		var parent = keys.get(3);
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		for (int lease = 0; lease < 20; lease++) {
			var expected = new LinkedHashMap<UUID, String>();
			for (var key : keys) {
				assertEquals(expected.put(key, key.toString()), actual.put(key, key.toString()));
			}
			var oldIterator = actual.entrySet().iterator();
			assertEquals(expected.remove(child), actual.remove(child));
			assertThrows(ConcurrentModificationException.class, oldIterator::next);
			equivalent(expected, actual);
			assertEquals(expected.put(child, "reattached"), actual.put(child, "reattached"));
			assertEquals(expected.put(parent, "scene"), actual.put(parent, "scene"));
			assertEquals(List.of(root, sibling, parent, child), new ArrayList<>(actual.keySet()));
			equivalent(expected, actual);
			assertFalse(actual.isMaterialized());
			actual.resetForLease();
		}
		assertEquals(0, actual.materializationCount());
	}

	@Test
	@SuppressWarnings("RedundantCollectionOperation")
	public void indexedViewsRemoveAndSetValueKeepTraversalAndOrder() {
		var keys = keys(12);
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		var expected = new LinkedHashMap<UUID, String>();
		for (var key : keys.reversed()) {
			actual.put(key, null);
			expected.put(key, null);
		}
		var ai = actual.entrySet().iterator();
		var ei = expected.entrySet().iterator();
		while (ei.hasNext()) {
			var ee = ei.next();
			var ae = ai.next();
			assertEquals(ee, ae);
			assertEquals(ee.setValue("updated"), ae.setValue("updated"));
			ai.remove();
			ei.remove();
			equivalent(expected, actual);
		}
		assertFalse(ai.hasNext());
		assertThrows(NoSuchElementException.class, ai::next);
		for (var key : keys.reversed()) {
			actual.put(key, null);
			expected.put(key, null);
		}
		assertEquals(expected.values().remove(null), actual.values().remove(null));
		assertEquals(expected.keySet().remove(keys.get(4)), actual.keySet().remove(keys.get(4)));
		assertEquals(expected.entrySet().remove(Map.entry(keys.get(5), "absent")),
				actual.entrySet().remove(Map.entry(keys.get(5), "absent")));
		equivalent(expected, actual);
		assertEquals(0, actual.materializationCount());
	}

	@Test
	public void fallbackPreservesReorderedEntriesAndExistingIteratorAfterValueOnlyCompute() {
		var keys = keys(5);
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		var expected = new LinkedHashMap<UUID, String>();
		for (var key : keys.reversed()) {
			actual.put(key, "before");
			expected.put(key, "before");
		}
		actual.remove(keys.get(2));
		expected.remove(keys.get(2));
		actual.put(keys.get(2), null);
		expected.put(keys.get(2), null);
		var ai = actual.entrySet().iterator();
		var ei = expected.entrySet().iterator();
		assertEquals(expected.compute(keys.get(4), (_, _) -> "after"),
				actual.compute(keys.get(4), (_, _) -> "after"));
		while (ei.hasNext()) {
			var ae = ai.next();
			var ee = ei.next();
			assertEquals(ee, ae);
			assertEquals(ee.setValue("entry"), ae.setValue("entry"));
			ai.remove();
			ei.remove();
		}
		equivalent(expected, actual);
		assertEquals(1, actual.materializationCount());
	}

	@Test
	public void resetInvalidatesOldViewsEntriesAndIteratorsInBothStorageModes() {
		var keys = keys(3);
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		for (boolean fallback : new boolean[]{false, true}) {
			actual.put(keys.get(2), "old");
			if (fallback) {
				actual.put(new UUID(999, 999), "unknown");
			}
			var oldKeys = actual.keySet();
			var oldValues = actual.values();
			var oldEntries = actual.entrySet();
			var iterator = oldEntries.iterator();
			var entry = iterator.next();
			actual.resetForLease();
			actual.put(keys.get(2), "new");
			assertThrows(ConcurrentModificationException.class, iterator::hasNext);
			assertThrows(ConcurrentModificationException.class, iterator::next);
			assertThrows(ConcurrentModificationException.class, iterator::remove);
			assertThrows(ConcurrentModificationException.class, oldKeys::size);
			assertThrows(ConcurrentModificationException.class, oldValues::iterator);
			assertThrows(ConcurrentModificationException.class, oldEntries::clear);
			assertThrows(ConcurrentModificationException.class, () -> entry.setValue("stale"));
			assertThrows(ConcurrentModificationException.class, entry::getValue);
			assertEquals("new", actual.values().iterator().next());
			assertEquals(1, actual.size());
			assertFalse(actual.isMaterialized());
			actual.resetForLease();
		}
	}


	@Test
	public void valueOnlyFallbackTransfersPartlyConsumedIteratorsAndRemoveToNativeStorage() {
		var keys = keys(4);
		for (int operation = 0; operation < 6; operation++) {
			var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
			var expected = new LinkedHashMap<UUID, String>();
			for (var key : keys) {
				actual.put(key, "before");
				expected.put(key, "before");
			}

			var ai = actual.entrySet().iterator();
			var ei = expected.entrySet().iterator();
			assertEquals(ei.next(), ai.next());
			for (Map<UUID, String> map : List.of(expected, actual)) {
				switch (operation) {
					case 0 -> map.compute(keys.getLast(), (_, _) -> "after");
					case 1 -> map.computeIfAbsent(keys.getLast(), _ -> "unused");
					case 2 -> map.computeIfPresent(keys.getLast(), (_, _) -> "after");
					case 3 -> map.merge(keys.getLast(), "after", String::concat);
					case 4 -> map.putAll(Map.of(keys.getLast(), "after"));
					case 5 -> map.computeIfAbsent(new UUID(99, 99), _ -> null);
				}
			}

			assertTrue(actual.isMaterialized());
			ai.remove();
			ei.remove();
			while (ei.hasNext()) {
				assertEquals(ei.next(), ai.next());
				ai.remove();
				ei.remove();
			}
			assertFalse(ai.hasNext());
			assertThrows(NoSuchElementException.class, ai::next);
			assertThrows(IllegalStateException.class, ai::remove);
			equivalent(expected, actual);
		}
	}

	@Test
	public void viewsAndHeldNullEntryStayBackedAcrossValueOnlyMaterialization() {
		var keys = keys(3);
		keys.add(null);
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		var expected = new LinkedHashMap<UUID, String>();
		for (var key : keys.reversed()) {
			actual.put(key, null);
			expected.put(key, null);
		}
		var actualKeys = actual.keySet();
		var actualValues = actual.values();
		var actualEntries = actual.entrySet();
		var held = actualEntries.iterator().next();
		var nativeHeld = expected.entrySet().iterator().next();
		var ai = actualValues.iterator();
		var ei = expected.values().iterator();
		assertEquals(ei.next(), ai.next());
		actual.putAll(Map.of());
		expected.putAll(Map.of());
		assertEquals(nativeHeld.setValue("updated"), held.setValue("updated"));
		assertEquals(nativeHeld, held);
		ai.remove();
		ei.remove();
		assertEquals(expected.keySet().remove(keys.getFirst()), actualKeys.remove(keys.getFirst()));
		assertEquals(expected.entrySet().remove(new AbstractMap.SimpleEntry<UUID, String>(keys.get(1), null)),
				actualEntries.remove(new AbstractMap.SimpleEntry<UUID, String>(keys.get(1), null)));
		assertEquals(new ArrayList<>(expected.values()), new ArrayList<>(actualValues));
		equivalent(expected, actual);
	}

	@Test
	public void oldViewSpliteratorsAndEntryComparisonsCannotRebindAfterLeaseReset() {
		var keys = keys(2);
		var actual = new PhotonIndexedMap<>(PhotonIndexedMap.compile(keys), String[]::new);
		for (boolean fallback : new boolean[]{false, true}) {
			actual.put(keys.getFirst(), "old");
			if (fallback) {
				actual.putAll(Map.of());
			}
			var oldKeys = actual.keySet().spliterator();
			var oldValues = actual.values().spliterator();
			var oldEntries = actual.entrySet().spliterator();
			var entry = actual.entrySet().iterator().next();
			actual.resetForLease();
			actual.put(keys.getLast(), "new");
			assertThrows(ConcurrentModificationException.class, () -> oldKeys.tryAdvance(_ -> fail("stale key")));
			assertThrows(ConcurrentModificationException.class, () -> oldValues.tryAdvance(_ -> fail("stale value")));
			assertThrows(ConcurrentModificationException.class, () -> oldEntries.tryAdvance(_ -> fail("stale entry")));
			assertThrows(ConcurrentModificationException.class, () -> entry.equals(Map.entry(keys.getLast(), "new")));
			assertThrows(ConcurrentModificationException.class, entry::hashCode);
			assertEquals(Map.of(keys.getLast(), "new"), actual);
			actual.resetForLease();
		}
	}

	private static ArrayList<UUID> keys(int count) {
		var result = new ArrayList<UUID>();
		var random = new Random(31);
		for (int index = 0; index < count; index++) {
			result.add(new UUID(random.nextLong(), random.nextLong()));
		}
		return result;
	}

	@SuppressWarnings("SimplifiableAssertion")
	private static void equivalent(Map<UUID, String> expected, Map<UUID, String> actual) {
		assertEquals(expected, actual);
		assertTrue(actual.equals(expected));
		assertEquals(expected.hashCode(), actual.hashCode());
		assertEquals(expected.size(), actual.size());
		assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(actual.keySet()));
		assertEquals(new ArrayList<>(expected.values()), new ArrayList<>(actual.values()));
		assertEquals(new ArrayList<>(expected.entrySet()), new ArrayList<>(actual.entrySet()));
		expected.forEach((key, value) -> {
			assertTrue(actual.containsKey(key));
			assertEquals(value, actual.get(key));
		});
	}
}
