package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HordeSplitMergeTest {
	private static final Vec3 ORIGIN = new Vec3(100, 60, -100);

	@Test
	public void nearbySurfacesConnectWithoutChangingEitherSource() {
		try (var split = settled(pair(5, 0, 2))) {
			var circles = split.circles();
			assertEquals(3, circles.size());
			var first = circles.get(0);
			var second = circles.get(1);
			var bridge = circles.get(2);
			assertEquals(0, first.centerX());
			assertEquals(5, second.centerX());
			assertEquals(2, first.radius(), 1.0e-10);
			assertEquals(2, second.radius(), 1.0e-10);
			assertEquals(2.5, bridge.centerX(), 1.0e-10);
			assertEquals(0, bridge.centerZ());
			assertTrue(first.radius() + bridge.radius() > bridge.centerX());
			assertTrue(second.radius() + bridge.radius() > second.centerX() - bridge.centerX());
			assertTrue(bridge.radius() <= 1.5);
			assertEquals(0, bridge.minimumY());
			assertEquals(2, bridge.maximumY());
		}
	}

	@Test
	public void distantContainedAndDisjointHeightPairsNeedNoBridge() {
		for (var regions : List.of(pair(8, 0, 2), pair(0, 0, 2), pair(5, 2, 2), pair(5, 3, 2))) {
			try (var split = settled(regions)) {
				assertEquals(2, split.circles().size());
			}
		}
	}

	@Test
	public void bridgeUsesOnlyTheSharedHeightAndFadesAtHeightContact() {
		try (var split = settled(pair(5, 1, 1))) {
			var bridge = split.circles().get(2);
			assertEquals(1, bridge.minimumY());
			assertEquals(2, bridge.maximumY());
		}

		try (var thin = settled(pair(5, 1.99999, 2));
		     var touching = settled(pair(5, 2, 2))) {
			assertTrue(thin.circles().get(2).radius() < 1.0e-8);
			assertEquals(2, touching.circles().size());
		}
	}

	@Test
	public void distanceThresholdIsContinuousFromBothDirections() {
		double atBoundary = bridgeRadius(6);
		assertEquals(0, atBoundary);
		assertEquals(0, bridgeRadius(6.00001));
		assertTrue(bridgeRadius(5.99999) < 1.0e-8);
		assertTrue(bridgeRadius(5.9) > bridgeRadius(5.99999));
		assertTrue(bridgeRadius(5) > bridgeRadius(5.9));

		try (var split = settled(pair(5, 0, 2))) {
			var previous = split.circles().get(2);
			split.updateCircles(pair(8, 0, 2), ORIGIN, 200);
			assertEquals(previous, split.circles().get(2));
			split.updateCircles(pair(8, 0, 2), ORIGIN, 200.000001);
			assertEquals(previous.radius(), split.circles().get(2).radius(), 1.0e-5);
			assertNotEquals(previous.centerX(), split.circles().get(2).centerX());
			var separating = split.circles().get(2);
			split.updateCircles(pair(5, 0, 2), ORIGIN, 200.000001);
			assertEquals(separating, split.circles().get(2));
			split.updateCircles(pair(5, 0, 2), ORIGIN, 200.000002);
			assertEquals(separating.radius(), split.circles().get(2).radius(), 1.0e-5);
		}
	}

	@Test
	public void sourceRetirementAndReentryKeepTheMergedContourContinuous() {
		var regions = pair(5, 0, 2);
		try (var split = settled(regions)) {
			double initial = split.circles().get(2).radius();
			split.updateCircles(Map.of(), ORIGIN, 200);
			assertEquals(initial, split.circles().get(2).radius());
			split.updateCircles(Map.of(), ORIGIN, 200.00001);
			assertEquals(initial, split.circles().get(2).radius(), 1.0e-8);
			var retiring = split.circles();
			split.updateCircles(regions, ORIGIN, 200.00001);
			assertEquals(retiring, split.circles());

			split.updateCircles(Map.of(), ORIGIN, 200.00001);
			split.updateCircles(Map.of(), ORIGIN, 240);
			assertEquals(2, split.circles().size());
			for (var circle : split.circles()) {
				assertTrue(circle.radius() < 1.0e-8);
			}
			split.updateCircles(Map.of(), ORIGIN, 240.00001);
			assertTrue(split.circles().isEmpty());
		}
	}

	@Test
	public void tinySourceCannotLeaveAFullStrengthConnection() {
		var regions = new LinkedHashMap<String, HordeClient.RegionBox>();
		regions.put("large", box(0, 2, 0, 2));
		regions.put("small", box(2.0001, 0.0001, 0, 2));
		try (var split = settled(regions)) {
			var bridge = split.circles().get(2);
			assertTrue(bridge.radius() > 0 && bridge.radius() < 1.0e-10);
			assertTrue(HordeSplitState.influence(bridge.radius()) < 1.0e-15);
		}
	}

	@Test
	public void chainsKeepLocalBridgesWithoutCoveringTheEmptyInterior() {
		var regions = new LinkedHashMap<String, HordeClient.RegionBox>();
		regions.put("left", box(0, 2, 0, 2));
		regions.put("middle", box(5, 2, 0, 2));
		regions.put("right", box(10, 2, 0, 2));
		try (var split = settled(regions)) {
			var circles = split.circles();
			assertEquals(5, circles.size());
			assertEquals(2.5, circles.get(3).centerX(), 1.0e-10);
			assertEquals(7.5, circles.get(4).centerX(), 1.0e-10);
			for (var circle : circles) {
				assertTrue(circle.radius() <= 2);
				double emptyDistance = Math.hypot(circle.centerX() - 5, circle.centerZ() - 4);
				assertTrue(emptyDistance > circle.radius());
			}
		}
	}

	@Test
	public void allRealSourcesHavePriorityOverConnectionsAtTheTotalBudget() {
		for (int size : new int[]{63, 64, 80}) {
			var regions = new LinkedHashMap<String, HordeClient.RegionBox>();
			for (int index = 0; index < size; index++) {
				regions.put("source:" + index, box(index * 5, 2, 0, 2));
			}

			try (var split = settled(regions)) {
				var circles = split.circles();
				assertEquals(HordeSplitState.MAXIMUM_CIRCLES, circles.size());
				for (int index = 0; index < Math.min(size, HordeSplitState.MAXIMUM_CIRCLES); index++) {
					assertEquals(index * 5, circles.get(index).centerX());
					assertEquals(2, circles.get(index).radius(), 1.0e-10);
				}
			}
		}
	}

	@Test
	public void realSourcePreemptsBridgeWhenTheTotalBudgetIsFull() {
		var regions = new LinkedHashMap<String, HordeClient.RegionBox>();
		for (int index = 0; index < 63; index++) {
			regions.put("source:" + index, box(index < 2 ? index * 5 : index * 100, 2, 0, 2));
		}
		try (var split = settled(regions)) {
			assertEquals(64, split.circles().size());
			assertTrue(split.circles().getLast().radius() > 0.5);
			regions.put("new", box(10000, 2, 0, 2));
			split.updateCircles(regions, ORIGIN, 200);
			assertEquals(64, split.circles().size());
			assertEquals(10000, split.circles().getLast().centerX());
			assertEquals(0, split.circles().getLast().radius());
		}
	}

	@Test
	public void oversizedInputKeepsTheLargestRealCircles() {
		var regions = new LinkedHashMap<String, HordeClient.RegionBox>();
		for (int index = 1; index <= 80; index++) {
			regions.put("source:" + index, box(index * 200, index, 0, 2));
		}

		try (var split = settled(regions)) {
			var circles = split.circles();
			assertEquals(64, circles.size());
			assertEquals(80, circles.getFirst().radius(), 1.0e-9);
			assertEquals(17, circles.getLast().radius(), 1.0e-9);
		}
	}

	@Test
	public void snapshotsAreImmutableAndDoNotChangeAfterAnotherUpdateOrClose() {
		var split = settled(pair(5, 0, 2));
		var snapshot = split.circles();
		assertThrows(UnsupportedOperationException.class, snapshot::clear);
		split.updateCircles(pair(8, 0, 2), ORIGIN, 220);
		assertEquals(3, snapshot.size());
		assertEquals(2, split.circles().size());
		split.close();
		assertTrue(split.circles().isEmpty());
		assertEquals(3, snapshot.size());
	}

	@Test
	public void freedBudgetAdmitsNewConnectionsFromZeroInsteadOfPoppingIn() {
		var regions = new LinkedHashMap<String, HordeClient.RegionBox>();
		for (int index = 0; index < 63; index++) {
			var center = index < 6 ? (index / 2) * 100 + (index % 2) * 5 : index * 100;
			regions.put("source:" + index, box(center, 2, 0, 2));
		}

		try (var split = settled(regions)) {
			regions.remove("source:0");
			split.updateCircles(regions, ORIGIN, 200);
			split.updateCircles(regions, ORIGIN, 239.999);
			var previous = split.circles();
			assertEquals(64, previous.size());
			split.updateCircles(regions, ORIGIN, 240);
			var current = split.circles();
			assertEquals(64, current.size());
			assertEquals(previous.getLast().centerX(), current.get(62).centerX());
			assertTrue(current.get(62).radius() > 0.5);
			assertTrue(current.getLast().radius() > 0 && current.getLast().radius() < 0.001);
		}
	}

	@Test
	public void worstCasePairScanStaysWithinTheCircleBudgetWithoutInventingConnections() {
		var regions = new LinkedHashMap<String, HordeClient.RegionBox>();
		for (int index = 0; index < 63; index++) {
			regions.put("source:" + index, box(index * 20, 2, 0, 2));
		}

		try (var split = settled(regions)) {
			assertEquals(63, split.circles().size());
		}
	}

	private static double bridgeRadius(double distance) {
		try (var split = settled(pair(distance, 0, 2))) {
			return split.circles().size() > 2 ? split.circles().get(2).radius() : 0;
		}
	}

	private static HordeSplitState settled(Map<String, HordeClient.RegionBox> regions) {
		var split = new HordeSplitState();
		split.updateCircles(regions, ORIGIN, 0);
		split.updateCircles(regions, ORIGIN, 200);
		return split;
	}

	private static Map<String, HordeClient.RegionBox> pair(double distance, double minimumY, double height) {
		var regions = new LinkedHashMap<String, HordeClient.RegionBox>();
		regions.put("first", box(0, 2, 0, 2));
		regions.put("second", box(distance, 2, minimumY, height));
		return regions;
	}

	private static HordeClient.RegionBox box(double centerX, double radius, double minimumY, double height) {
		return new HordeClient.RegionBox(ORIGIN.x + centerX - radius, ORIGIN.y + minimumY,
				ORIGIN.z - radius, radius * 2, height, radius * 2);
	}
}
