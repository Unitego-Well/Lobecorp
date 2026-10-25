package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HordeRegionTest {
	@Test
	public void bodyGapKeepsThreeBlockClearanceWithoutExpandingHeight() {
		var bounds = new AABB(-1, 10, -2, 1, 12, 2);
		var box = HordeClient.RegionBox.forEntity(bounds);
		assertEquals(-4, box.x());
		assertEquals(8, box.sizeX());
		assertEquals(-6, box.z());
		assertEquals(12, box.sizeZ());
		assertEquals(10, box.y());
		assertEquals(2, box.sizeY());
	}

    @Test
    public void integerRegionCoversNegativeCoordinatesAtEverySubmissionTier() {
        var bounds = new AABB(-17.2, 63.8, -0.2, -0.1, 66.1, 1.2);
	    for (int grid : new int[]{1, 8, 16, 32}) {
            var box = HordeClient.RegionBox.of(bounds, grid);
            assertTrue(box.x() <= bounds.minX && box.y() <= bounds.minY && box.z() <= bounds.minZ);
            assertTrue(box.x() + box.sizeX() >= bounds.maxX);
            assertTrue(box.y() + box.sizeY() >= bounds.maxY);
            assertTrue(box.z() + box.sizeZ() >= bounds.maxZ);
            assertEquals(0, Math.floorMod(box.x(), grid));
            assertEquals(0, Math.floorMod(box.sizeX(), grid));
        }
    }

    @Test
	public void interruptedEntryAndExitRemainContinuous() {
		var region = new HordeClient.Region();
		region.update(new Vec3(0, 0, 0), 10, 10, 0, 20);
		assertEquals(0, region.fromX);
		assertEquals(10, region.toX);
		region.remove(10, 20);
		assertEquals(5, region.fromX, 0.00001);
		assertEquals(0, region.toX);
		assertFalse(region.expired(29, 20));
		assertTrue(region.expired(30, 20));
		region.update(new Vec3(8, 0, 0), 12, 12, 15, 20);
		assertEquals(4.21875, region.fromX, 0.00001);
		assertEquals(12, region.toX);
	}

	@Test
	public void repeatedTargetsDoNotRestartTransition() {
		var region = new HordeClient.Region();
		region.update(new Vec3(4, 0, 0), 10, 10, 0, 20);
		region.update(new Vec3(4, 0, 0), 10, 10, 10, 20);
		assertEquals(0, region.changed);
		region.remove(10, 20);
		region.remove(15, 20);
		assertEquals(10, region.changed);
		assertTrue(region.expired(30, 20));
	}
}
