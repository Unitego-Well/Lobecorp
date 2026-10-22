package org.unitego.lobecorp.client.horde;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HordeRegionTest {
	private static CompoundTag zone(double x, double radius) {
		var tag = new CompoundTag();
		tag.putDouble("x", x);
		tag.putDouble("rx", radius);
		tag.putDouble("rz", radius);
		return tag;
	}

	@Test
	public void interruptedEntryAndExitRemainContinuous() {
		var region = new HordeClient.Region();
		region.update(zone(0, 10), 0, 20);
		assertEquals(0, region.fromX);
		assertEquals(10, region.toX);
		region.remove(10, 20);
		assertEquals(5, region.fromX, 0.00001);
		assertEquals(0, region.toX);
		assertFalse(region.expired(29, 20));
		assertTrue(region.expired(30, 20));
		region.update(zone(8, 12), 15, 20);
		assertEquals(4.21875, region.fromX, 0.00001);
		assertEquals(12, region.toX);
	}

	@Test
	public void repeatedTargetsDoNotRestartTransition() {
		var region = new HordeClient.Region();
		region.update(zone(4, 10), 0, 20);
		region.update(zone(4, 10), 10, 20);
		assertEquals(0, region.changed);
		region.remove(10, 20);
		region.remove(15, 20);
		assertEquals(10, region.changed);
		assertTrue(region.expired(30, 20));
	}
}
