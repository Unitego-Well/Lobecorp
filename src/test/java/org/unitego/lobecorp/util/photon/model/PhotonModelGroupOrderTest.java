package org.unitego.lobecorp.util.photon.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PhotonModelGroupOrderTest {
	@Test
	public void placesMissingAndNonOverlayGroupsBeforeOverlayGroups() {
		var prepared = prepared(Map.of("overlay", true, "base", false),
				List.of(group("overlay"), group("missing"), group("base"), group("overlay")));

		assertEquals(List.of(1, 2, 0, 3), prepared.groupOrder());
	}

	@Test
	public void keepsOriginalOrderWithinSameOverlayLayer() {
		var prepared = prepared(Map.of("overlay", true, "base", false),
				List.of(group("overlay"), group("base"), group("overlay")));

		assertEquals(List.of(1, 0, 2), prepared.groupOrder());
	}

	@Test
	public void returnsEmptyOrderForEmptyGroups() {
		var prepared = prepared(Map.of("overlay", true), List.of());

		assertTrue(prepared.groupOrder().isEmpty());
	}

	@Test
	public void reusesTheSameSuccessfulOrderForOnePrepared() {
		var prepared = prepared(Map.of("overlay", true, "base", false),
				List.of(group("overlay"), group("base")));

		assertSame(prepared.groupOrder(), prepared.groupOrder());
	}

	@Test
	public void exposesUnmodifiableOrder() {
		var order = prepared(Map.of("base", false), List.of(group("base"))).groupOrder();

		assertThrows(UnsupportedOperationException.class, () -> order.add(1));
		assertThrows(UnsupportedOperationException.class, order::clear);
	}

	@Test
	public void calculatesOrderIndependentlyForNewDefinitions() {
		var overlayFirst = prepared(Map.of("overlay", true, "base", false),
				List.of(group("overlay"), group("base")));
		var baseFirst = prepared(Map.of("overlay", true, "base", false),
				List.of(group("base"), group("overlay")));

		assertEquals(List.of(1, 0), overlayFirst.groupOrder());
		assertEquals(List.of(0, 1), baseFirst.groupOrder());
	}

	@Test
	public void equalPreparedValuesDoNotChangeEqualityOrHashThroughLazyOrder() {
		var definition = definition(Map.of("overlay", true, "base", false));
		var groups = List.of(group("overlay"), group("base"));
		var first = new PhotonModelTopologyUtil.Prepared(definition, groups);
		var second = new PhotonModelTopologyUtil.Prepared(definition, groups);
		var hash = first.hashCode();

		assertEquals(List.of(1, 0), first.groupOrder());
		assertEquals(List.of(1, 0), second.groupOrder());
		assertEquals(first, second);
		assertEquals(hash, first.hashCode());
		assertEquals(hash, second.hashCode());
	}

	@Test
	public void differentPreparedValuesRemainDifferentAfterOrderCalculation() {
		var first = prepared(Map.of("overlay", true), List.of(group("overlay")));
		var second = prepared(Map.of("overlay", false), List.of(group("overlay")));

		first.groupOrder();
		second.groupOrder();

		assertNotEquals(first, second);
	}

	private static PhotonModelTopologyUtil.Prepared prepared(Map<String, Boolean> overlays,
	                                                          List<PhotonModelTopologyUtil.Group> groups) {
		return new PhotonModelTopologyUtil.Prepared(definition(overlays), groups);
	}

	private static PhotonModelDefinition definition(Map<String, Boolean> overlays) {
		var materials = new LinkedHashMap<String, PhotonModelDefinition.Material>();

		overlays.forEach((id, overlay) -> materials.put(id,
				new PhotonModelDefinition.Material("", "", PhotonModelDefinition.Tint.NONE, overlay)));
		return new PhotonModelDefinition(new JsonObject(), 1, 1, 0,
				List.of(), List.of(), List.of(), materials, List.of(), Map.of(), List.of(), List.of());
	}

	private static PhotonModelTopologyUtil.Group group(String material) {
		return new PhotonModelTopologyUtil.Group(0, material, List.of(), List.of());
	}
}
