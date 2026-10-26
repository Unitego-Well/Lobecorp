package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HordeCircularMotionContractTest {
	@Test
	public void circleKeepsTrueHeightAndDoesNotExcludeItsBoundingSquareCorners() {
		var body = new AABB(-1.3, 63.125, -2.7, -0.7, 64.925, -2.1);
		var core = HordeClient.RegionBox.forEntity(body);
		assertEquals(core.sizeX(), core.sizeZ());
		assertEquals(body.minY, core.y());
		assertEquals(body.getYsize(), core.sizeY());
		assertTrue(core.contains(new Vec3(-1, 63.125, -2.4)));
		assertFalse(core.contains(new Vec3(-1, 64.925, -2.4)));
		assertFalse(core.contains(new Vec3(core.x() + 0.01, 64, core.z() + 0.01)));
		assertFalse(core.contains(new Vec3(core.x() + core.sizeX() + 0.01, 64, -2.4)));
		var exact = new HordeClient.RegionBox(-4, 10, -4, 8, 2, 8);
		assertFalse(exact.contains(new Vec3(4, 11, 0)));
	}

	@Test
	public void capsuleExtendsTheGuidanceButNotTheForbiddenCore() {
		var core = HordeClient.RegionBox.forEntity(new AABB(-0.3, 10.25, -0.3, 0.3, 12.05, 0.3));
		var forward = new Vec3(1, 0, 0);
		var ahead = new Vec3(-8, 11, 0);
		var behind = new Vec3(7, 11, 0);
		assertFalse(core.contains(ahead));
		assertFalse(core.contains(behind));
		assertTrue(core.contourDistance(ahead, forward, 0.75) < 0);
		assertTrue(core.contourDistance(behind, forward, 0.75) < 0);
		assertTrue(core.contourDistance(new Vec3(-30, 11, 0), forward, 0.75) > 0);
		assertEquals(Double.POSITIVE_INFINITY, core.contourDistance(new Vec3(0, 20, 0), forward, 0.75));
	}

	@Test
	public void localFloatWordsPreserveFractionsNearTheWorldBoundary() {
		var targets = new HordeRegionState.Targets();
		var core = new HordeClient.RegionBox(29_999_990.125, 63.375, -29_999_999.875, 7.25, 1.8, 7.25);
		targets.update(Map.of("local", core), 0);
		var words = targets.words(29_999_990, 63, -30_000_000);
		assertEquals(0.125f, Float.intBitsToFloat(words[0]));
		assertEquals(0.375f, Float.intBitsToFloat(words[1]));
		assertEquals(0.125f, Float.intBitsToFloat(words[2]));
		assertEquals(7.25f, Float.intBitsToFloat(words[3]));
		assertEquals(1.8f, Float.intBitsToFloat(words[4]));
		assertEquals(words[3], words[5]);
		assertEquals(0, words[7]);
		int generation = words[6];
		targets.update(Map.of(), 1);
		assertEquals(-generation, targets.words()[6]);
		targets.update(Map.of("local", core), 40);
		assertEquals(generation, targets.words()[6]);
	}

	@Test
	public void fractionalNegativeBoundsAreConservativelyCoveredByTheIndex() {
		var core = new HordeClient.RegionBox(-32.125, -0.125, -64.125, 0.25, 0.25, 0.25);
		var grid = HordeSpatialIndex.Grid.plan(List.of(core), 1, 4L * 1024 * 1024);
		assertTrue(grid.x() <= Math.floor(core.x()) - 128);
		assertTrue(grid.y() <= Math.floor(core.y()) - 128);
		assertTrue(grid.z() <= Math.floor(core.z()) - 128);
		assertTrue((long) grid.x() + grid.nx() * 32L >= Math.ceil(core.x() + core.sizeX()) + 128);
		assertTrue((long) grid.y() + grid.ny() * 32L >= Math.ceil(core.y() + core.sizeY()) + 128);
		assertTrue((long) grid.z() + grid.nz() * 32L >= Math.ceil(core.z() + core.sizeZ()) + 128);
	}
}
