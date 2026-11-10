package org.unitego.lobecorp.client.horde;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/// 检查准备期快照的失效条件，以及删除同时间重复几何更新的等价前提；不初始化渲染器。
public class HordeSplitPreparationTest {
    @Test
    public void snapshotDetectsDirectRegionChangesWithoutVersionChanges() throws ReflectiveOperationException {
        var event = event();
        event.regions.put("local", box(0));
        var snapshot = snapshot(event, 20);
        assertTrue(matches(snapshot, event, 20));

        event.regions.put("local", box(4));
        assertFalse(matches(snapshot, event, 20));
        event.regions.put("local", box(0));
        assertTrue(matches(snapshot, event, 20));
        event.regions.put("target:other", box(8));
        assertFalse(matches(snapshot, event, 20));
        event.regions.clear();
        assertFalse(matches(snapshot, event, 20));
    }

    @Test
    public void timeAndOriginChangesInvalidateTheSnapshot() throws ReflectiveOperationException {
        var event = event();
        var snapshot = snapshot(event, 20);
        assertFalse(matches(snapshot, event, 21));
        assertFalse(matches(snapshot, event, 19));
        assertFalse(matches(snapshot, event, Double.NaN));
        assertFalse(matches(snapshot(event, Double.NaN), event, Double.NaN));
        event.origin = new Vec3(1, 0, 0);
        assertFalse(matches(snapshot, event, 20));
        event.origin = Vec3.ZERO;
        assertTrue(matches(snapshot, event, 20));
    }

    @Test
    public void separatePreparationsObserveSameTimeRegionChangesAndRebuild() throws ReflectiveOperationException {
        var event = event();
        var first = snapshot(event, 20);
        event.regions.put("local", box(0));
        var second = snapshot(event, 20);
        assertFalse(matches(first, event, 20));
        assertTrue(matches(second, event, 20));

        event.split.updateCircles(event.regions, event.origin, 20);
        assertEquals(0, event.split.circles().getFirst().radius());
        event.split.close();
        event.split.updateCircles(event.regions, event.origin, 20);
        assertEquals(0, event.split.circles().getFirst().radius());
        event.split.close();
    }

    @Test
    public void duplicateSameTimeGeometryKeepsCircleAndBridgeOrder() {
        var regions = new TreeMap<String, HordeClient.RegionBox>();
        for (int index = 0; index < 12; index++) {
            regions.put("target:" + index, box(index * 2));
        }

        try (var once = new HordeSplitState(); var repeated = new HordeSplitState()) {
            for (double time : new double[]{0, 10, 20, 21, 40, 60, 61}) {
                if (time == 21) {
                    regions.remove("target:3");
                    regions.put("member:new", box(4));
                }
                if (time == 40) {
                    regions.clear();
                }

                once.updateCircles(regions, Vec3.ZERO, time);
                repeated.updateCircles(regions, Vec3.ZERO, time);
                repeated.updateCircles(regions, Vec3.ZERO, time);
                repeated.updateCircles(regions, Vec3.ZERO, time);
                assertEquals(once.circles(), repeated.circles());
                assertTrue(once.circles().size() <= HordeSplitState.MAXIMUM_CIRCLES);
                if (time == 0) {
                    assertTrue(once.circles().stream().allMatch(circle -> circle.radius() == 0));
                }
            }
        }
    }

    @Test
    public void duplicateSameTimeGeometryKeepsCompetingBridgeSlots() {
        // 正方形四顶点的六条边/对角线都可桥接；每顶点三圆产生54个候选，只有52个桥接槽。
        try (var square = new HordeSplitState()) {
            square.updateCircles(squareRegions(1), Vec3.ZERO, 0);
            square.updateCircles(squareRegions(1), Vec3.ZERO, 20);
            assertEquals(10, square.circles().size());
            assertTrue(square.circles().subList(4, 10).stream().allMatch(circle -> circle.radius() > 0));
        }

        var regions = squareRegions(3);
        try (var once = new HordeSplitState(); var repeated = new HordeSplitState()) {
            var initial = updateAndCompare(once, repeated, regions, 0);
            assertEquals(12, initial.size());
            assertTrue(initial.stream().allMatch(circle -> circle.radius() == 0));

            assertFullBridgeBudget(updateAndCompare(once, repeated, regions, 20));
            assertFullBridgeBudget(updateAndCompare(once, repeated, regions, 21));
        }
    }

    @Test
    public void duplicateSameTimeGeometryKeepsLargestSourcesWhenOverCapacity() {
        var regions = new TreeMap<String, HordeClient.RegionBox>();
        for (int index = 0; index < 66; index++) {
            // 距离足够大以排除桥接；后插入的源半径更大，不能靠截断插入顺序通过。
            regions.put("source:%02d".formatted(index), centeredBox(index * 1000, 0, index + 1));
        }

        assertTrue(regions.size() > HordeSplitState.MAXIMUM_CIRCLES);
        try (var once = new HordeSplitState(); var repeated = new HordeSplitState()) {
            updateAndCompare(once, repeated, regions, 0);
            var selected = updateAndCompare(once, repeated, regions, 20);
            assertEquals(HordeSplitState.MAXIMUM_CIRCLES, selected.size());
            for (int index = 0; index < selected.size(); index++) {
                assertEquals((65 - index) * 1000.0, selected.get(index).centerX());
                assertEquals(0.0, selected.get(index).centerZ());
                assertTrue(selected.get(index).radius() > 0);
                if (index > 0) {
                    assertTrue(selected.get(index - 1).radius() > selected.get(index).radius());
                }
            }

            assertEquals(65000.0, selected.getFirst().centerX());
            assertEquals(2000.0, selected.getLast().centerX());
            assertTrue(selected.stream().noneMatch(circle -> circle.centerX() == 0 || circle.centerX() == 1000));
            assertEquals(selected, updateAndCompare(once, repeated, regions, 20));
        }
    }

    @Test
    public void duplicateSameTimeGeometryKeepsRetirementReleaseAndReentryOrder() {
        var regions = squareRegions(3);
        try (var once = new HordeSplitState(); var repeated = new HordeSplitState()) {
            updateAndCompare(once, repeated, regions, 0);
            assertFullBridgeBudget(updateAndCompare(once, repeated, regions, 20));
            for (int copy = 0; copy < 3; copy++) {
                assertNotNull(regions.remove("source:0:" + copy));
            }

            var retiring = updateAndCompare(once, repeated, regions, 21);
            assertEquals(12, retiring.stream().filter(circle -> circle.radius() > 1.5).count());
            var halfway = updateAndCompare(once, repeated, regions, 41);
            assertTrue(halfway.subList(0, 3).stream()
                    .allMatch(circle -> circle.radius() > 0 && circle.radius() < 1.1));

            // 40 tick退场结束，三顶点九源只留下27桥；被移除顶点及其连接已释放槽位。
            var released = updateAndCompare(once, repeated, regions, 61);
            assertEquals(36, released.size());
            assertEquals(9, released.stream().filter(circle -> circle.radius() > 1.5).count());
            assertTrue(released.stream().noneMatch(circle -> circle.centerX() == 0 && circle.centerZ() == 0));
            assertEquals(27, released.subList(9, released.size()).size());
            assertTrue(released.subList(9, released.size()).stream().allMatch(circle -> circle.radius() > 0));

            for (int copy = 0; copy < 3; copy++) {
                regions.put("source:0:" + copy, centeredBox(0, 0, 2));
            }

            // 同时间重入先追加三个零半径源，保留现有27桥的内容和顺序，不补长新源。
            var reentered = updateAndCompare(once, repeated, regions, 61);
            assertEquals(39, reentered.size());
            assertTrue(reentered.subList(9, 12).stream()
                    .allMatch(circle -> circle.centerX() == 0 && circle.centerZ() == 0 && circle.radius() == 0));
            assertEquals(released.subList(9, released.size()), reentered.subList(12, reentered.size()));
            assertFullBridgeBudget(updateAndCompare(once, repeated, regions, 81));
        }
    }

    private static List<HordeSplitState.Circle> updateAndCompare(HordeSplitState once, HordeSplitState repeated,
                                                                 Map<String, HordeClient.RegionBox> regions, double time) {
        once.updateCircles(regions, Vec3.ZERO, time);
        repeated.updateCircles(regions, Vec3.ZERO, time);
        assertEquals(once.circles(), repeated.circles());
        repeated.updateCircles(regions, Vec3.ZERO, time);
        assertEquals(once.circles(), repeated.circles());
        repeated.updateCircles(regions, Vec3.ZERO, time);
        assertEquals(once.circles(), repeated.circles());
        return once.circles();
    }

    private static void assertFullBridgeBudget(List<HordeSplitState.Circle> circles) {
        assertEquals(HordeSplitState.MAXIMUM_CIRCLES, circles.size());
        assertTrue(circles.subList(0, 12).stream().allMatch(circle -> circle.radius() > 1.5));
        assertEquals(52, circles.subList(12, circles.size()).size());
        assertTrue(circles.subList(12, circles.size()).stream()
                .allMatch(circle -> circle.radius() > 0 && circle.radius() <= 1.5));
    }

    private static TreeMap<String, HordeClient.RegionBox> squareRegions(int copies) {
        var regions = new TreeMap<String, HordeClient.RegionBox>();
        for (int corner = 0; corner < 4; corner++) {
            for (int copy = 0; copy < copies; copy++) {
                regions.put("source:" + corner + ":" + copy, centeredBox(corner % 2 * 3, corner < 2 ? 0 : 3, 2));
            }
        }

        return regions;
    }

    private static HordeClient.RegionBox centeredBox(double x, double z, double radius) {
        return new HordeClient.RegionBox(x - radius, 0, z - radius, radius * 2, 2, radius * 2);
    }

    private static HordeClient.ClientHorde event() throws ReflectiveOperationException {
        var constructor = HordeClient.ClientHorde.class.getDeclaredConstructor(UUID.class);
        constructor.setAccessible(true);
        var event = constructor.newInstance(UUID.randomUUID());
        event.origin = Vec3.ZERO;
        return event;
    }

    private static Object snapshot(HordeClient.ClientHorde event, double time) throws ReflectiveOperationException {
        var type = Class.forName("org.unitego.lobecorp.client.horde.HordeGpuRenderer$SplitPreparation");
        var constructor = type.getDeclaredConstructor(HordeClient.ClientHorde.class, double.class);
        constructor.setAccessible(true);
        return constructor.newInstance(event, time);
    }

    private static boolean matches(Object snapshot, HordeClient.ClientHorde event, double time)
            throws ReflectiveOperationException {
        var method = snapshot.getClass().getDeclaredMethod("matches", HordeClient.ClientHorde.class, double.class);
        method.setAccessible(true);
        return (boolean) method.invoke(snapshot, event, time);
    }

    private static HordeClient.RegionBox box(double x) {
        return new HordeClient.RegionBox(x, 0, 0, 4, 2, 4);
    }
}
