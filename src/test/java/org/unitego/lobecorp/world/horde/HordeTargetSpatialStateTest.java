package org.unitego.lobecorp.world.horde;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class HordeTargetSpatialStateTest {
	@Test
	public void squareUsesLongestHorizontalSideAndKeepsRealHeight() {
		var region = HordeCombatRegion.around(new AABB(2, 70, 4, 4, 75, 10), 8);
		assertEquals(22, region.bounds().getXsize());
		assertEquals(22, region.bounds().getZsize());
		assertEquals(70, region.bounds().minY);
		assertEquals(75, region.bounds().maxY);
		assertTrue(region.containsHorizontal(new Vec3(-8, -500, -4)));
		assertFalse(region.containsHorizontal(new Vec3(-8.01, 72, 7)));
		assertEquals(new Vec3(13, 99, -3), region.clampHorizontal(new Vec3(100, 99, -100), 1));
	}

	@Test
	public void firstSnapshotIsImmediateAndFarUpdatesNeverExceedTenTicks() {
		for (int phase = 0; phase < 100; phase++) {
			var state = new HordeTargetSpatialState(new UUID(0, phase));
			assertTrue(state.due(7, false));
			long previous = 7;
			state.refresh(7, Vec3.ZERO, new AABB(0, 70, 0, 1, 72, 1), 8);
			for (long tick = 8; tick <= 200; tick++) {
				if (state.due(tick, false)) {
					assertTrue(tick - previous <= HordeTargetSpatialState.UPDATE_INTERVAL);
					state.refresh(tick, new Vec3(tick, 70, 0), new AABB(tick, 70, 0, tick + 1, 72, 1), 8);
					previous = tick;
				}
			}
		}
	}

	@Test
	public void nearPromotionIsImmediateAndDemotionStillHasBoundedDeadline() {
		var state = new HordeTargetSpatialState(new UUID(0, 9));
		state.refresh(0, Vec3.ZERO, new AABB(0, 0, 0, 1, 2, 1), 8);
		assertFalse(state.due(1, false));
		assertTrue(state.due(1, true));
		state.refresh(1, new Vec3(4, 7, 9), new AABB(4, 7, 9, 5, 9, 10), 8);
		assertEquals(new Vec3(4, 7, 9), state.position());
		assertTrue(state.due(2, true));
		assertTrue(state.due(11, false));
	}

	@Test
	public void collectionChurnCannotStarveAnExistingDeadline() {
		var survivor = new HordeTargetSpatialState(new UUID(0, 3));
		var states = new ArrayList<HordeTargetSpatialState>();
		states.add(survivor);
		long previous = -1;
		for (long tick = 0; tick < 100; tick++) {
			states.addFirst(new HordeTargetSpatialState(new UUID(tick, tick + 100)));
			if (states.size() > 5) {
				states.remove(1);
			}
			for (var state : states) {
				if (state.due(tick, false)) {
					state.refresh(tick, Vec3.ZERO, new AABB(0, 0, 0, 1, 2, 1), 8);
					if (state == survivor) {
						if (previous >= 0) {
							assertTrue(tick - previous <= 10);
						}
						previous = tick;
					}
				}
			}
			assertTrue(previous >= tick - 10);
		}
	}

	@Test
	public void stationarySnapshotDoesNotDirtyGroupingAndReloadStartsUninitialized() {
		var id = UUID.randomUUID();
		var state = new HordeTargetSpatialState(id);
		var bounds = new AABB(0, 0, 0, 1, 2, 1);
		assertTrue(state.refresh(1, Vec3.ZERO, bounds, 8));
		assertFalse(state.refresh(2, Vec3.ZERO, bounds, 8));
		assertTrue(new HordeTargetSpatialState(id).due(2, false));
	}

	@Test
	public void repeatedPlayerBoundaryCrossingsDoNotDelayFarDeadline() {
		var state = new HordeTargetSpatialState(new UUID(0, 7));
		long previous = -1;
		for (long tick = 0; tick < 100; tick++) {
			boolean near = HordeTargetSpatialState.near(tick % 7 < 3 ? 1024 : 1024.01);
			if (state.due(tick, near)) {
				if (previous >= 0) {
					assertTrue(tick - previous <= 10);
				}
				state.refresh(tick, new Vec3(tick, 70, 0), new AABB(tick, 70, 0, tick + 1, 72, 1), 8);
				previous = tick;
			}
			if (near) {
				assertEquals(tick, previous);
			}
		}
		assertTrue(HordeTargetSpatialState.near(32 * 32));
		assertFalse(HordeTargetSpatialState.near(32 * 32 + 0.001));
	}

	@Test
	public void discoveriesImmediatelyAfterFullScanWaitExactlyTenTicksAtMost() {
		for (long arrival = 1; arrival < 100; arrival++) {
			long discovery = arrival;
			while (!HordeTargetSpatialState.fullScanDue(discovery)) {
				discovery++;
			}
			assertTrue(discovery - arrival < HordeTargetSpatialState.UPDATE_INTERVAL);
		}
		assertTrue(HordeTargetSpatialState.fullScanDue(10));
		assertFalse(HordeTargetSpatialState.fullScanDue(9));
	}

}
