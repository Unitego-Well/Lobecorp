package org.unitego.lobecorp.util.photon.creation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashMap;
import java.util.Map;
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
		assertEquals(1, actual.materializationCount());
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
