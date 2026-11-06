package org.unitego.lobecorp.util.photon.playback;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhotonSpawnSnapshotUtilTest {
	@Test
	public void matchesLegacyUuidListLookupAndIterationOrder() {
		var instances = new LinkedHashMap<UUID, String>();
		instances.put(UUID.fromString("00000000-0000-0000-0000-000000000004"), new String("fourth"));
		instances.put(UUID.fromString("00000000-0000-0000-0000-000000000001"), new String("first"));
		instances.put(UUID.fromString("00000000-0000-0000-0000-000000000003"), new String("third"));
		instances.put(UUID.fromString("00000000-0000-0000-0000-000000000002"), new String("second"));

		var legacy = new HashMap<UUID, String>();

		for (var id : List.copyOf(instances.keySet())) {
			legacy.put(id, instances.get(id));
		}

		var actual = PhotonSpawnSnapshotUtil.snapshot(instances, value -> value, "缺少：");
		assertNotEquals(List.copyOf(instances.keySet()), List.copyOf(actual.keySet()));
		assertEquals(legacy.keySet(), actual.keySet());
		assertEquals(legacy.size(), actual.size());
		assertIterableEquals(entries(legacy), entries(actual));

		for (var entry : legacy.entrySet()) {
			assertSame(entry.getValue(), actual.get(entry.getKey()));
		}
	}

	@Test
	public void returnsEmptyHashMapWithoutResolving() {
		var calls = new int[1];
		var actual = PhotonSpawnSnapshotUtil.snapshot(Map.<UUID, String>of(), value -> {
			calls[0]++;
			return value;
		}, "缺少：");
		assertTrue(actual.isEmpty());
		assertEquals(0, calls[0]);
		assertEquals(HashMap.class, actual.getClass());
	}

	@Test
	public void failsAtFirstNullWithOriginalPrefixAndStopsResolving() {
		var first = UUID.fromString("00000000-0000-0000-0000-000000000010");
		var missing = UUID.fromString("00000000-0000-0000-0000-000000000011");
		var later = UUID.fromString("00000000-0000-0000-0000-000000000012");
		var instances = new LinkedHashMap<UUID, String>();
		instances.put(first, "first");
		instances.put(missing, null);
		instances.put(later, "later");
		var resolved = new ArrayList<UUID>();

		var failure = assertThrows(IllegalArgumentException.class, () ->
				PhotonSpawnSnapshotUtil.snapshot(instances, value -> {
					resolved.add(instances.entrySet().stream()
							.filter(entry -> entry.getValue() == value)
							.findFirst().orElseThrow().getKey());
					return value;
				}, "子动画生成实例缺少控制器："));

		assertEquals("子动画生成实例缺少控制器：" + missing, failure.getMessage());
		assertEquals(List.of(first, missing), resolved);
	}

	@Test
	public void resolvesEachEntryExactlyOnce() {
		var instances = new LinkedHashMap<UUID, String>();
		instances.put(UUID.fromString("00000000-0000-0000-0000-000000000020"), "a");
		instances.put(UUID.fromString("00000000-0000-0000-0000-000000000021"), "b");
		instances.put(UUID.fromString("00000000-0000-0000-0000-000000000022"), "c");
		var calls = new HashMap<UUID, Integer>();
		var byValue = new HashMap<String, UUID>();

		for (var entry : instances.entrySet()) {
			byValue.put(entry.getValue(), entry.getKey());
		}

		var actual = PhotonSpawnSnapshotUtil.snapshot(instances, value -> {
			var id = byValue.get(value);
			calls.merge(id, 1, Integer::sum);
			return value;
		}, "缺少：");

		assertEquals(3, actual.size());
		assertEquals(3, calls.size());

		for (var count : calls.values()) {
			assertEquals(1, count);
		}
	}

	@Test
	public void snapshotStructureSurvivesLaterSourceChanges() {
		var first = UUID.fromString("00000000-0000-0000-0000-000000000030");
		var second = UUID.fromString("00000000-0000-0000-0000-000000000031");
		var instances = new LinkedHashMap<UUID, String>();
		instances.put(first, "first");
		instances.put(second, "second");
		var actual = PhotonSpawnSnapshotUtil.snapshot(instances, value -> value, "缺少：");
		instances.put(UUID.fromString("00000000-0000-0000-0000-000000000032"), "third");
		instances.remove(first);
		assertEquals(Map.of(first, "first", second, "second"), actual);
	}

	@Test
	public void propagatesResolverFailuresUnchanged() {
		var failure = new IllegalStateException("resolver failed");
		var first = UUID.fromString("00000000-0000-0000-0000-000000000040");
		var instances = new LinkedHashMap<UUID, String>();
		instances.put(first, "first");
		var thrown = assertThrows(IllegalStateException.class, () ->
				PhotonSpawnSnapshotUtil.snapshot(instances, value -> {
					throw failure;
				}, "缺少："));
		assertSame(failure, thrown);
	}

	private static List<String> entries(Map<UUID, String> map) {
		var result = new ArrayList<String>();

		for (var entry : map.entrySet()) {
			result.add(entry.getKey() + "=" + entry.getValue());
		}

		return result;
	}
}
