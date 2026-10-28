package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HordeSplitTransitionTest {
    private static final Vec3 ORIGIN = new Vec3(100, 60, -100);
    private static final HordeClient.RegionBox TARGET = new HordeClient.RegionBox(98, 60, -102, 4, 2, 4);

    @Test
    public void everyEntryStartsAtZeroAndOpensContinuously() {
        try (var split = new HordeSplitState()) {
            var regions = Map.of("local", TARGET, "target:other", TARGET, "member:other", TARGET);
            split.updateCircles(regions, ORIGIN, 0);
            assertEquals(3, split.circles().size());
            for (var circle : split.circles()) {
                assertEquals(0, circle.radius());
                assertEquals(60, circle.bounds(ORIGIN).y());
            }

            split.updateCircles(regions, ORIGIN, 0.001);
            for (var circle : split.circles()) {
                assertTrue(circle.radius() > 0 && circle.radius() < 0.001);
            }

            split.updateCircles(regions, ORIGIN, 10);
            for (var circle : split.circles()) {
                assertTrue(circle.radius() > 1 && circle.radius() < TARGET.radius());
            }
        }
    }

    @Test
    public void retirementReachesZeroWithoutADeadlineJump() {
        try (var split = new HordeSplitState()) {
            split.updateCircles(Map.of("local", TARGET), ORIGIN, 0);
            split.updateCircles(Map.of("local", TARGET), ORIGIN, 20);
            double radius = split.circles().getFirst().radius();
            split.updateCircles(Map.of(), ORIGIN, 20);
            assertEquals(radius, split.circles().getFirst().radius());

            double previous = radius;
            for (int tick = 21; tick < 60; tick++) {
                split.updateCircles(Map.of(), ORIGIN, tick);
                double current = split.circles().getFirst().radius();
                assertTrue(current < previous && current > 0);
                previous = current;
            }

            split.updateCircles(Map.of(), ORIGIN, 59.999);
            assertTrue(split.circles().getFirst().radius() < 1.0e-8);
            split.updateCircles(Map.of(), ORIGIN, 60);
            assertTrue(split.circles().isEmpty());
        }
    }

    @Test
    public void reentryAndReversalKeepTheCurrentRadius() {
        try (var split = new HordeSplitState()) {
            var regions = Map.of("target:other", TARGET);
            split.updateCircles(regions, ORIGIN, 0);
            split.updateCircles(regions, ORIGIN, 20);
            split.updateCircles(Map.of(), ORIGIN, 20);
            split.updateCircles(Map.of(), ORIGIN, 40);
            double retiring = split.circles().getFirst().radius();
            split.updateCircles(regions, ORIGIN, 40);
            assertEquals(retiring, split.circles().getFirst().radius());

            split.updateCircles(regions, ORIGIN, 41);
            double reopening = split.circles().getFirst().radius();
            assertTrue(reopening > retiring && reopening < TARGET.radius());
            split.updateCircles(Map.of(), ORIGIN, 41);
            assertEquals(reopening, split.circles().getFirst().radius());
            split.updateCircles(Map.of(), ORIGIN, 80.999);
            assertTrue(split.circles().getFirst().radius() < 1.0e-8);
            split.updateCircles(Map.of(), ORIGIN, 81);
            assertTrue(split.circles().isEmpty());
        }
    }

    @Test
    public void particleCoreAndGuidanceVanishWithTheSmoothedCircle() {
        var zero = new HordeSplitState.Circle(0, 0, 0, 0, 2).bounds(ORIGIN);
        assertFalse(zero.contains(ORIGIN));
        assertEquals(Double.POSITIVE_INFINITY, zero.contourDistance(ORIGIN, new Vec3(1, 0, 0), 0.75));
        assertEquals(0, HordeSplitState.influence(0));
        assertEquals(1, HordeSplitState.influence(0.5));
        assertTrue(HordeSplitState.influence(0.0001) < 1.0e-6);

        var small = new HordeSplitState.Circle(0, 0, 0.0001, 0, 2).bounds(ORIGIN);
        assertFalse(small.contains(ORIGIN.add(0.1, 0, 0)));
        assertTrue(small.contourDistance(ORIGIN.add(0.1, 0, 0), new Vec3(1, 0, 0), 0.75) > 0);
    }
}
