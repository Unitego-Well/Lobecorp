package org.unitego.lobecorp.util.horde;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HordePresenceUtilTest {
    @Test
    public void occupiedBandMovesAndClampsToRoute() {
        assertEquals(60, HordePresenceUtil.head(30, 2, 100));
        assertEquals(20, HordePresenceUtil.tail(30, 20, 2, 100));
        assertEquals(40, HordePresenceUtil.center(60, 20));
        assertEquals(100, HordePresenceUtil.tail(100, 20, 2, 100));
        assertFalse(HordePresenceUtil.contains(Vec3.ZERO, 100, Vec3.ZERO, new Vec3(0, 0, 1), 100, 100, 10));
    }

    @Test
    public void routeCorridorDoesNotImplyCurrentWavePresence() {
        var point = new Vec3(0, 0, 80);
        var forward = new Vec3(0, 0, 1);
        assertTrue(HordePresenceUtil.contains(point, 80, point, forward, 0, 100, 10));
        assertFalse(HordePresenceUtil.contains(point, 80, point, forward, 20, 60, 10));
        assertFalse(HordePresenceUtil.contains(new Vec3(0, 0, 61), 60, new Vec3(0, 0, 60), forward, 20, 60, 10));
        assertFalse(HordePresenceUtil.contains(new Vec3(11, 0, 40), 40, new Vec3(0, 0, 40), forward, 20, 60, 10));
    }
}
