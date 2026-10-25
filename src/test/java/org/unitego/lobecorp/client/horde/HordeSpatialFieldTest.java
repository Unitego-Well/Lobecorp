package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HordeSpatialFieldTest {
	@Test
	public void smallBodyKeepsAtLeastThreeBlocksInBothDirectionsAndOriginalHeight() {
		var body = new AABB(-0.3, 64, -0.3, 0.3, 65.8, 0.3);
		var box = HordeClient.RegionBox.forEntity(body);
		assertTrue(box.x() <= body.minX - 3 && box.z() <= body.minZ - 3);
		assertTrue(box.x() + box.sizeX() >= body.maxX + 3);
		assertTrue(box.z() + box.sizeZ() >= body.maxZ + 3);
		assertEquals(64, box.y());
		assertEquals(2, box.sizeY());
	}

	@Test
	public void combatEnvelopeIncludesNearbyMembersWithoutChainingAcrossTheWorld() {
		var target = HordeClient.RegionBox.forEntity(new AABB(-1, 0, -1, 1, 2, 1));
		var expanded = HordeClient.RegionBox.includeCombat(target, target, new AABB(9, 0, -1, 11, 2, 1), 8);
		assertEquals(14, expanded.x() + expanded.sizeX());
		assertSame(expanded, HordeClient.RegionBox.includeCombat(target, expanded, new AABB(18, 0, -1, 20, 2, 1), 8));
		assertSame(target, HordeClient.RegionBox.includeCombat(target, target, new AABB(0, 12, 0, 2, 14, 2), 8));
	}

	@Test
	public void eventClockDoesNotJumpWhenHeadersArrive() {
		var clock = new HordeRenderClock();
		clock.synchronize(100, 1000);
		assertEquals(110, clock.time(1010), 0.000001);
		clock.synchronize(108, 1010);
		assertEquals(110, clock.time(1010), 0.000001);
		double previous = clock.time(1010);
		for (int step = 1; step <= 160; step++) {
			double value = clock.time(1010 + step * 0.25);
			assertTrue(value > previous);
			previous = value;
		}
		assertEquals(previous, clock.time(1200), 0.000001);
	}

	@Test
	public void largeNegativeClockCorrectionStillMovesForwardAndResyncRemainsContinuous() {
		var clock = new HordeRenderClock();
		clock.synchronize(1000, 0);
		clock.synchronize(0, 1);
		double before = clock.time(1);
		assertTrue(clock.time(2) > before);
		double continuous = clock.time(20);
		clock.synchronize(500, 20);
		assertEquals(continuous, clock.time(20), 0.000001);
		assertTrue(clock.time(20.5) > continuous);
	}

	@Test
	public void spatialDomainIsAlignedAndFinerLevelsRespectCapacity() {
		var boxes = List.of(new HordeClient.RegionBox(-4, 64, -4, 8, 2, 8));
		var full = HordeSpatialIndex.Grid.plan(boxes, 64, 4L * 1024 * 1024);
		assertEquals(0, Math.floorMod(full.x(), 32));
		assertEquals(0, Math.floorMod(full.y(), 32));
		assertEquals(0, Math.floorMod(full.z(), 32));
		assertEquals(7, full.flags());
		assertEquals(1, HordeSpatialIndex.Grid.plan(boxes, 64, full.words(0)).flags());
		assertEquals(3, HordeSpatialIndex.Grid.plan(boxes, 64, full.words(1)).flags());
		assertEquals(7, HordeSpatialIndex.Grid.plan(boxes, 64, full.words(2)).flags());
		assertEquals(0, HordeSpatialIndex.Grid.plan(boxes, 64, full.words(0) - 1).flags());
	}

	@Test
	public void sparseRegionsStopAt32WithoutChangingActualSmallBoxes() {
		var box = HordeClient.RegionBox.forEntity(new AABB(0, 0, 0, 0.5, 1, 0.5));
		var layout = HordeSpatialIndex.Grid.plan(List.of(box), 8, 4L * 1024 * 1024);
		assertEquals(1, layout.flags());
		assertTrue(box.sizeX() < 8);
		assertTrue(box.sizeZ() < 8);
		assertEquals(2, layout.stride());
	}

	@Test
	public void farCoordinatesAndOversizedDomainsNeverAliasOrAllocateUnboundedIndices() {
		var far = new HordeClient.RegionBox(29999990, 60, -29999999, 8, 2, 8);
		var layout = HordeSpatialIndex.Grid.plan(List.of(far), 64, 4L * 1024 * 1024);
		assertTrue(layout.x() <= far.x() && layout.z() <= far.z());
		assertTrue((long) layout.x() + layout.nx() * 32L >= (long) far.x() + far.sizeX());
		var huge = HordeSpatialIndex.Grid.plan(List.of(far, new HordeClient.RegionBox(-29999999, -64, 29999990, 8, 2, 8)), 64, 4L * 1024 * 1024);
		assertEquals(0, huge.flags());
		assertEquals(0, huge.words(0));
	}

	@Test
	public void irisFrameLayoutPreservesAllFramesAndIncludesPaddedLastRow() {
		var layout = HordeIrisAnimation.FrameLayout.plan(4, 3, 64);
		assertEquals(120, layout.words());
		assertEquals(64, layout.width());
		assertEquals(2, layout.height());
		var model = HordeIrisAnimation.FrameLayout.plan(520, 20, 16384);
		assertEquals(104000, model.words());
		assertTrue((long) model.width() * model.height() >= model.words());
		assertTrue((long) model.width() * (model.height() - 1) < model.words());
	}

	@Test
	public void incompleteFramesFailAndPartialMaskWordsKeepEveryRegion() {
		assertThrows(IllegalArgumentException.class, () -> HordeIrisAnimation.FrameLayout.plan(5, 1, 64));
		assertThrows(IllegalArgumentException.class, () -> HordeIrisAnimation.FrameLayout.plan(4, 2, 8));
		var boxes = List.of(new HordeClient.RegionBox(0, 0, 0, 8, 2, 8));
		assertEquals(3, HordeSpatialIndex.Grid.plan(boxes, 33, 4L * 1024 * 1024).stride());
		assertEquals(3, HordeSpatialIndex.Grid.plan(boxes, 64, 4L * 1024 * 1024).stride());
		assertEquals(4, HordeSpatialIndex.Grid.plan(boxes, 65, 4L * 1024 * 1024).stride());
	}
}
