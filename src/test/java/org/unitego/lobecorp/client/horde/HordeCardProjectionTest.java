package org.unitego.lobecorp.client.horde;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// 使用独立数值核对全帧投影、留边与实际片网格，不依赖游戏图像初始化。
public class HordeCardProjectionTest {
    private static final double EPSILON = 0.00001;
    private static final int STRIDE = HordeMeshCache.VERTEX_STRIDE;
    private static final double[][] RIGHT = {{0, 0, 1}, {0, 1, 0}, {1, 0, 0},
            {0, 0, 1}, {0, 1, 0}, {1, 0, 0}};
    private static final double[][] DOWN = {{0, 1, 0}, {0, 0, 1}, {0, 0, 1},
            {1, 0, 0}, {1, 0, 0}, {0, 1, 0}};
    private static final double[][] NORMAL = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0},
            {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

    @Test
    public void animationUnionControlsCenterAndPaddedSideInsteadOfRestPose() {
        var projection = HordeMeshReduction.cardProjection(source());
        assertArrayEquals(new double[]{1, 4, 1}, projection.center(), EPSILON);
        assertEquals(320.0 / 29, projection.side(), EPSILON);
        // 最长边十单位占58像素，三像素留边贴回后仍恢复脚底-1和头顶9。
        assertEquals(3, 32 + (-1 - projection.centerY()) * 64 / projection.side(), EPSILON);
        assertEquals(61, 32 + (9 - projection.centerY()) * 64 / projection.side(), EPSILON);
        var bottomLeft = projection.corner(0, 0);
        var topLeft = projection.corner(0, 3);
        assertEquals(-1, bottomLeft[1] + (topLeft[1] - bottomLeft[1]) * 3 / 64, EPSILON);
        assertEquals(9, bottomLeft[1] + (topLeft[1] - bottomLeft[1]) * 61 / 64, EPSILON);
    }

    @Test
    public void allSixProjectionsRestoreKnownTangentialCoordinatesWithoutMirroring() {
        var projection = HordeMeshReduction.cardProjection(source());
        double[] expectedX = {55.2, 3, 49.4, 55.2, 3, 49.4};
        double[] expectedY = {3, 55.2, 55.2, 49.4, 49.4, 3};
        double[] expectedDepth = {-3, 3, 5, -5, -4, 4};
        double[][] expectedWorld = {{1, -1, 5}, {1, -1, 5}, {4, 4, 5},
                {4, 4, 5}, {4, -1, 1}, {4, -1, 1}};
        double[] offset = {3, -5, 4};
        for (int face = 0; face < 6; face++) {
            var axes = HordeMeshReduction.cardAxes(face);
            assertArrayEquals(RIGHT[face], axes[0], EPSILON);
            assertArrayEquals(DOWN[face], axes[1], EPSILON);
            assertArrayEquals(NORMAL[face], axes[2], EPSILON);
            assertEquals(expectedX[face], 32 + dot(offset, axes[0]) * 64 / projection.side(), EPSILON);
            assertEquals(expectedY[face], 32 + dot(offset, axes[1]) * 64 / projection.side(), EPSILON);
            assertEquals(expectedDepth[face], dot(offset, axes[2]), EPSILON);
            var a = projection.corner(face, 0);
            var b = projection.corner(face, 1);
            var d = projection.corner(face, 3);
            for (int axis = 0; axis < 3; axis++) {
                double restored = a[axis] + (b[axis] - a[axis]) * expectedX[face] / 64
                        + (d[axis] - a[axis]) * expectedY[face] / 64;
                assertEquals(expectedWorld[face][axis], restored, EPSILON);
            }
            // 最大深度应选择朝外方向的表面，反面深度严格更小。
            assertEquals(2, dot(NORMAL[face], axes[2]) * 2, EPSILON);
            assertEquals(-2, dot(NORMAL[face], axes[2]) * -2, EPSILON);
        }
    }

    @Test
    public void flatAnimationKeepsUnitProjectionAxesAndFullHeight() {
        var source = source();
        var frames = source.frameVertices().clone();
        for (int at = 2; at < frames.length; at += 6) {
            frames[at] = 2;
        }
        var flat = new HordeMeshCache.Model(source.vertices(), frames, 48, 2, 1);
        var projection = HordeMeshReduction.cardProjection(flat);
        assertArrayEquals(new double[]{1, 4, 2}, projection.center(), EPSILON);
        assertEquals(320.0 / 29, projection.side(), EPSILON);
        assertArrayEquals(new double[]{0, 1, 0}, HordeMeshReduction.cardAxes(4)[0], EPSILON);
        assertArrayEquals(new double[]{1, 0, 0}, HordeMeshReduction.cardAxes(4)[1], EPSILON);
        var a = projection.corner(4, 0);
        var b = projection.corner(4, 1);
        assertEquals(2, a[2], EPSILON);
        assertEquals(-1, a[1] + (b[1] - a[1]) * 3 / 64, EPSILON);
        assertEquals(9, a[1] + (b[1] - a[1]) * 61 / 64, EPSILON);
    }

    @Test
    public void preparedTierThreeUsesAnimationUnionForRealVerticesFramesAndUv() {
        var source = source();
        var prepared = HordeMeshReduction.prepare(source, List.of(
                        new HordeMeshReduction.Cube(0, 24, 0), new HordeMeshReduction.Cube(24, 24, 0)),
                List.of("body"), new int[]{0}, Identifier.parse("lobecorp:test"), false);
        var cards = prepared[3];
        assertEquals(24, cards.vertexCount());
        assertEquals(2, cards.frames());
        assertArrayEquals(source.frameVertices(), prepared[0].frameVertices());
        double half = 160.0 / 29;
        double[] center = {1, 4, 1};
        for (int face = 0; face < 6; face++) {
            for (int corner = 0; corner < 4; corner++) {
                int vertex = face * 4 + corner;
                double u = corner == 1 || corner == 2 ? half : -half;
                double v = corner >= 2 ? half : -half;
                for (int axis = 0; axis < 3; axis++) {
                    double expected = center[axis] + RIGHT[face][axis] * u + DOWN[face][axis] * v;
                    assertEquals(expected, cards.vertices()[vertex * STRIDE + axis], EPSILON);
                    assertEquals(NORMAL[face][axis], cards.vertices()[vertex * STRIDE + 5 + axis], EPSILON);
                    for (int frame = 0; frame < 2; frame++) {
                        int at = (frame * 24 + vertex) * 6;
                        assertEquals(expected, cards.frameVertices()[at + axis], EPSILON);
                        assertEquals(NORMAL[face][axis], cards.frameVertices()[at + 3 + axis], EPSILON);
                    }
                }
                float encodedU = cards.vertices()[vertex * STRIDE + 3];
                assertEquals((face * 2 + (u > 0 ? 1 : 0)) / 12.0, encodedU, EPSILON);
                assertEquals(v > 0 ? 1 : 0, cards.vertices()[vertex * STRIDE + 4], EPSILON);
            }
        }
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static HordeMeshCache.Model source() {
        var vertices = new float[48 * STRIDE];
        var frames = new float[2 * 48 * 6];
        float[][] boxes = {{-2, 0, -3, 0, 5, 1}, {1, -1, 0, 4, 9, 5}};
        for (int vertex = 0; vertex < 48; vertex++) {
            int corner = vertex % 8;
            for (int axis = 0; axis < 3; axis++) {
                boolean upper = (corner & (1 << axis)) != 0;
                vertices[vertex * STRIDE + axis] = 100 + (upper ? (axis + 1) * 2 : 0);
                for (int frame = 0; frame < 2; frame++) {
                    frames[(frame * 48 + vertex) * 6 + axis] = boxes[frame][axis + (upper ? 3 : 0)];
                }
            }
        }
        return new HordeMeshCache.Model(vertices, frames, 48, 2, 1);
    }
}
