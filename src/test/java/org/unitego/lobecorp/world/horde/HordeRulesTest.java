package org.unitego.lobecorp.world.horde;

import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HordeRulesTest {
	private static HordeRules rules() {
        return new HordeRules(6000, 64, 8, 1, 3, 100, 100, 100,
				40, 20, 5, 20, 8, 40, 4);
	}

	@Test
	public void openGroundReachabilityDoesNotExhaustTheBudgetBeforeSixteenBlocks() {
		assertTrue(HordeSpawnPlanner.reachable(new Vec3(16.6, 0, 0), Vec3.ZERO, point -> point, _ -> true));
	}

	@Test
	public void reachabilityCanDetourAndRejectCompletelyBlockedGround() {
		assertTrue(HordeSpawnPlanner.reachable(new Vec3(16, 0, 0), Vec3.ZERO, point -> point,
				point -> Math.abs(point.x - 8) > 0.001 || Math.abs(point.z) >= 4));
		assertFalse(HordeSpawnPlanner.reachable(new Vec3(16, 0, 0), Vec3.ZERO, point -> point, _ -> false));
	}

	@Test
	public void coarseRoutePreparationInterpolatesAndKeepsNearestQueriesInsideTheActiveWindow() {
		var route = HordeRoute.create(List.of(Vec3.ZERO, new Vec3(64, 0, 0)));
		assertTrue(route.prepare(rules(), point -> new Vec3(point.x, point.x == 16 ? 32 : 0, point.z)));
		assertEquals(16, route.node(8).at().y, 0.00001);
		assertEquals(8, route.nearest(new Vec3(8, 16, 0), 0, 64), 0.00001);
		assertEquals(10, route.nearest(new Vec3(8, 16, 0), 10, 12), 0.00001);
	}

	@Test
	public void missingGroundCannotProduceAPreparedRoute() {
		var route = HordeRoute.create(List.of(Vec3.ZERO, new Vec3(64, 0, 0)));
		assertFalse(route.prepare(rules(), _ -> null));
	}

	@Test
	public void deferredGroundCoversVisualDisplacementWithoutZeroHeightSamples() {
		var route = HordeRoute.create(List.of(new Vec3(0, 56, 0), new Vec3(64, 56, 0)));
		assertTrue(route.prepareDeferred(rules(), _ -> null));
		assertTrue(route.node(0).sampleWidth() > 192);
		for (int index = 0; index < route.size(); index++) {
			assertEquals(56, route.node(index).height(-96), 0.00001);
			assertEquals(56, route.node(index).height(96), 0.00001);
		}
	}

	@Test
	public void nonfiniteGeometryIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new HordeRules(6000, 64, 8,
				1, 3, 100, 100, 100, 40, 20, 5, 20, Double.NaN, 40, 4));
	}

	@Test
	public void invalidStoredSampleWidthIsRejected() {
		var route = HordeRoute.create(List.of(Vec3.ZERO, new Vec3(16, 0, 0)));
		var saved = route.save();
		var node = assertInstanceOf(CompoundTag.class, saved.getListOrEmpty("nodes").getFirst());
		node.putDouble("width", Double.NaN);
		assertThrows(IllegalArgumentException.class, () -> HordeRoute.load(saved));
	}

	@Test
	public void perTargetHardCapIncludesInitialQuotaReinforcementAndReservations() {
		var high = new HordeRules(6000, 64, 8, 16, 10, 100, 100, 100,
				40, 1, 5, 20, 8, 40, 4);
		var target = new HordeTargetAssignment("target", "domain", UUID.randomUUID(), Vec3.ZERO, high);
		assertEquals(2, HordeRules.maximumQuota(20));
		assertEquals(3, HordeRules.maximumQuota(100));
		assertEquals(5, HordeRules.maximumQuota(200));
		assertEquals(20, HordeRules.maximumQuota(800));
		target.maximumQuota = HordeRules.maximumQuota(800);
		assertEquals(16, target.quota);
		for (int index = 0; index < 20; index++) {
			target.death();
		}
		assertEquals(20, target.quota);
		for (int index = 0; index < 19; index++) {
			target.members.add(UUID.randomUUID());
		}
		target.reservations = 1;
		assertEquals(0, target.missing());
		target.quota = 100;
		assertEquals(0, target.missing());
		assertEquals(100, HordeTargetAssignment.load(target.save()).quota);
	}

	@Test
	public void deathRaisesQuotaAndResetsReinforcementClock() {
		var target = new HordeTargetAssignment("target", "domain", UUID.randomUUID(), Vec3.ZERO, rules());
		target.maximumQuota = HordeRules.maximumQuota(100);
		target.fightTicks = 99;
		target.death();
		assertEquals(2, target.quota);
		assertEquals(0, target.fightTicks);
		target.death();
		target.death();
		assertEquals(3, target.quota);
	}

	@Test
	public void transferredExcessDoesNotRequestMoreOrRemoveMembers() {
		var target = new HordeTargetAssignment("target", "domain", UUID.randomUUID(), Vec3.ZERO, rules());
		target.maximumQuota = HordeRules.maximumQuota(100);
		target.quota = 3;
		for (int i = 0; i < 5; i++) {
			target.members.add(UUID.randomUUID());
		}
		assertEquals(0, target.missing());
		assertEquals(5, target.members.size());
		target.death();
		assertEquals(3, target.quota);
		assertEquals(5, target.members.size());
	}

	@Test
	public void reservationOccupiesTheLastAvailableSlot() {
		var target = new HordeTargetAssignment("target", "domain", UUID.randomUUID(), Vec3.ZERO, rules());
		target.reservations = 1;
		assertEquals(0, target.missing());
	}

	@Test
	public void stateRoundTripPreservesQuotaAndMembersButNotTransientReservations() {
		var target = new HordeTargetAssignment("target", "domain", UUID.randomUUID(), new Vec3(1, 2, 3), rules());
		target.members.add(UUID.randomUUID());
		target.quota = 3;
		target.engaged = true;
		target.fightTicks = 42;
		target.retryUntil = 900;
		target.reservations = 1;
		var restored = HordeTargetAssignment.load(target.save());
		assertEquals(target.members, restored.members);
		assertEquals(target.quota, restored.quota);
		assertEquals(42, restored.fightTicks);
		assertEquals(900, restored.retryUntil);
		assertEquals(0, restored.reservations);
	}

	@Test
	public void serverRulesRoundTripExcludesClientSpacing() {
		var original = rules();
		var restored = HordeRules.load(original.save());
		assertEquals(original, restored);
		assertFalse(original.save().contains("row_spacing"));
		assertFalse(original.save().contains("column_spacing"));
	}

    @Test
    public void openPathKeepsEndpointsAndRejectsCoincidentAnchors() {
        var end = new Vec3(48, 4, 32);
        var path = HordeSamplingUtil.path(List.of(Vec3.ZERO, Vec3.ZERO, new Vec3(32, 2, 0), end));
        assertEquals(Vec3.ZERO, path.getFirst());
        assertEquals(end, path.getLast());
        assertNotEquals(path.getFirst(), path.getLast());
        assertTrue(path.stream().allMatch(point -> Double.isFinite(point.lengthSqr())));
        assertThrows(IllegalArgumentException.class, () -> HordeSamplingUtil.path(List.of(Vec3.ZERO, Vec3.ZERO)));
    }

    @Test
    public void automaticLoopClosesAndHasNoZeroLengthSegments() {
		var loop = HordeSamplingUtil.loop(List.of(Vec3.ZERO, new Vec3(300, 0, 0)), 128);
		assertEquals(loop.getFirst(), loop.getLast());
		double length = 0;
		for (int index = 1; index < loop.size(); index++) {
			double distance = loop.get(index - 1).distanceTo(loop.get(index));
			assertTrue(distance > 0);
			length += distance;
		}
		assertTrue(length > 1000);
	}

	@Test
	public void explicitLoopDoesNotRepeatTheClosingAnchor() {
		var loop = HordeSamplingUtil.loop(List.of(Vec3.ZERO, new Vec3(300, 0, 0), new Vec3(300, 0, 300), Vec3.ZERO), 128);
		assertTrue(loop.size() > 4);
		assertEquals(2, loop.stream().filter(loop.getFirst()::equals).count());
		assertEquals(loop.getFirst(), loop.getLast());
	}

	@Test
	public void invalidServerWidthIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new HordeRules(6000, 0, 8, 1, 3,
				100, 100, 100, 40, 20, 5, 20, 8, 40, 4));
	}

	@Test
	public void fadingIsPresentationOnlyAndHasFiniteDuration() {
		var state = new HordeParticipant(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 0, 10, 5);
		assertEquals(1, state.alpha(10));
		assertEquals(0.5f, state.alpha(12.5));
		assertEquals(0, state.alpha(15));
		assertEquals(state, HordeParticipant.load(state.save()));
	}
}
