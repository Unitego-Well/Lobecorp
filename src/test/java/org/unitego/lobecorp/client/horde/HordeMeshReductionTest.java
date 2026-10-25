package org.unitego.lobecorp.client.horde;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// 无游戏资源依赖：验证简化网格闭合、薄刀面保留和各动画帧的空间一致性。
public class HordeMeshReductionTest {
    private static final int STRIDE = HordeMeshCache.VERTEX_STRIDE;
    private static final int[][] FACES = {{0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4},
            {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}};

    @Test
    public void rigidChildReductionProducesClosedGeometry() {
        var model = source(false);
        var cubes = List.of(new HordeMeshReduction.Cube(0, 24, 0), new HordeMeshReduction.Cube(24, 24, 1));
        var models = HordeMeshReduction.prepare(model, cubes, List.of("body", "foot"), new int[]{0, 0},
                Identifier.parse("lobecorp:test"), false);
        assertArrayEquals(model.vertices(), models[0].vertices());
        assertArrayEquals(model.frameVertices(), models[0].frameVertices());
        assertTrue(models[2].vertexCount() < models[0].vertexCount());
        assertEquals(model.frames(), models[2].frames());
        assertEquals(model.duration(), models[2].duration());
        for (int frame = 0; frame < model.frames(); frame++) {
            assertClosed(models[2], frame);
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            for (int vertex = 0; vertex < models[2].vertexCount(); vertex++) {
                float x = models[2].frameVertices()[(frame * models[2].vertexCount() + vertex) * 6];
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
            }
            assertEquals(frame * 0.125f, minX);
            assertEquals(1 + frame * 0.125f, maxX);
        }
    }

    @Test
    public void planarDoubleSidedBladeIsPreserved() {
        var model = source(true);
        var models = HordeMeshReduction.prepare(model, List.of(
                        new HordeMeshReduction.Cube(0, 24, 0), new HordeMeshReduction.Cube(24, 24, 0),
                        new HordeMeshReduction.Cube(48, 8, 1)),
                List.of("body", "blade"), new int[]{0, 0}, Identifier.parse("lobecorp:test"), false);
        var blade = new ArrayList<String>();
        for (int vertex = 0; vertex < models[2].vertexCount(); vertex++) {
            if (models[2].vertices()[vertex * STRIDE + 8] == 1) {
                blade.add(Arrays.toString(Arrays.copyOfRange(models[2].vertices(), vertex * STRIDE,
                        (vertex + 1) * STRIDE)));
            }
        }
        assertEquals(8, blade.size());
        for (int vertex = 48; vertex < 56; vertex++) {
            assertTrue(blade.contains(Arrays.toString(Arrays.copyOfRange(model.vertices(),
                    vertex * STRIDE, (vertex + 1) * STRIDE))));
        }
    }

    private static HordeMeshCache.Model source(boolean blade) {
        var values = new ArrayList<Float>();
        cube(values, 0, 0);
        cube(values, 1, blade ? 0 : 1);
        if (blade) {
            for (int side = 0; side < 2; side++) {
                int[] corners = side == 0 ? new int[]{0, 1, 2, 3} : new int[]{3, 2, 1, 0};
                for (int corner : corners) {
                    vertex(values, 3, corner == 1 || corner == 2 ? 1 : 0, corner >= 2 ? 1 : 0,
                            0, 0, side == 0 ? 1 : -1, corner, 1);
                }
            }
        }
        int count = values.size() / STRIDE;
        float[] vertices = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            vertices[index] = values.get(index);
        }
        float[] frames = new float[4 * count * 6];
        for (int frame = 0; frame < 4; frame++) {
            for (int vertex = 0; vertex < count; vertex++) {
                for (int axis = 0; axis < 3; axis++) {
                    frames[(frame * count + vertex) * 6 + axis] = vertices[vertex * STRIDE + axis]
                            + (axis == 0 ? frame * 0.125f : 0);
                    frames[(frame * count + vertex) * 6 + 3 + axis] = vertices[vertex * STRIDE + 5 + axis];
                }
            }
        }
        return new HordeMeshCache.Model(vertices, frames, count, 4, 0.6667f);
    }

    private static void cube(List<Float> values, float yOffset, int bone) {
        for (int[] face : FACES) {
            float[] a = corner(face[0], yOffset);
            float[] b = corner(face[1], yOffset);
            float[] c = corner(face[2], yOffset);
            float x = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
            float y = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
            float z = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
            for (int index = 0; index < 4; index++) {
                float[] point = corner(face[index], yOffset);
                vertex(values, point[0], point[1], point[2], x, y, z, index, bone);
            }
        }
    }

    private static float[] corner(int corner, float yOffset) {
        return new float[]{corner & 1, ((corner & 2) == 0 ? 0 : 1) + yOffset, (corner & 4) == 0 ? 0 : 1};
    }

    private static void vertex(List<Float> values, float x, float y, float z, float nx, float ny, float nz, int uv, int bone) {
        for (float value : new float[]{x, y, z, uv == 1 || uv == 2 ? 1 : 0, uv >= 2 ? 1 : 0, nx, ny, nz, bone}) {
            values.add(value);
        }
    }

    private static void assertClosed(HordeMeshCache.Model model, int frame) {
        var edges = new HashMap<String, Integer>();
        for (int quad = 0; quad < model.vertexCount(); quad += 4) {
            int[][] triangles = {{quad, quad + 1, quad + 2}, {quad, quad + 2, quad + 3}};
            for (int[] triangle : triangles) {
                String a = point(model, frame, triangle[0]);
                String b = point(model, frame, triangle[1]);
                String c = point(model, frame, triangle[2]);
                if (a.equals(b) || b.equals(c) || a.equals(c)) {
                    continue;
                }
                for (String[] edge : new String[][]{{a, b}, {b, c}, {c, a}}) {
                    String key = edge[0].compareTo(edge[1]) <= 0 ? edge[0] + "|" + edge[1] : edge[1] + "|" + edge[0];
                    edges.merge(key, 1, Integer::sum);
                }
            }
        }
        assertFalse(edges.isEmpty());
        assertTrue(edges.values().stream().allMatch(count -> count == 2));
    }

    private static String point(HordeMeshCache.Model model, int frame, int vertex) {
        int at = (frame * model.vertexCount() + vertex) * 6;
        return Arrays.toString(Arrays.copyOfRange(model.frameVertices(), at, at + 3));
    }
}
