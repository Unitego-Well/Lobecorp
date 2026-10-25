package org.unitego.lobecorp.client.horde;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/// 只打包批次元数据；工作集上限不会裁掉实例或改变模型。
public class HordeVertexLayout {
    private final int[] offsets;
    private final int[] ends;
    private final int maximumVertices;

    public HordeVertexLayout(int[] vertices, int wordsPerVertex, long capacityWords) {
        if (wordsPerVertex <= 0 || capacityWords <= 0) {
            throw new IllegalArgumentException("顶点工作集必须为正");
        }
        offsets = new int[vertices.length];
        var boundaries = new IntArrayList();
        int used = 0;
        int maximum = 0;
        for (int i = 0; i < vertices.length; i++) {
            int count = vertices[i];
            if (count <= 0 || count % 4 != 0 || (long) count * wordsPerVertex > capacityWords) {
                throw new IllegalArgumentException("批次顶点必须是完整四边形且不超过工作集容量");
            }
            if (((long) used + count) * wordsPerVertex > capacityWords) {
                boundaries.add(i);
                maximum = Math.max(maximum, used);
                used = 0;
            }
            offsets[i] = used;
            used = Math.addExact(used, count);
        }
        if (vertices.length != 0) boundaries.add(vertices.length);
        ends = boundaries.toIntArray();
        maximumVertices = Math.max(maximum, used);
    }

    public int[] offsets() {
        return offsets;
    }

    public int[] ends() {
        return ends;
    }

    public int maximumVertices() {
        return maximumVertices;
    }
}
