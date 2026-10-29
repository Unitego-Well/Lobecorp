package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

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

}
