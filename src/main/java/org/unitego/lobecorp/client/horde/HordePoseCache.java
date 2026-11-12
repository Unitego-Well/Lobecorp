package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/// 与既有1024行外观周期一致的共享姿态表，不保存逐单位顶点。
public class HordePoseCache {
    private static final Long2ObjectOpenHashMap<HordeDataTexture> TABLES = new Long2ObjectOpenHashMap<>();

    public static GpuTextureView view(int columns, int frames) {
        long key = ((long) columns << 32) | frames;
        var table = TABLES.get(key);
        if (table == null) {
            table = create(columns, frames);
            TABLES.put(key, table);
        }
        return table.texture().getTextureView();
    }

    private static HordeDataTexture create(int columns, int frames) {
        int[] values = new int[Math.multiplyExact(1024, columns)];
        for (int row = 0; row < 1024; row++) {
            for (int column = 0; column < columns; column++) {
                int hash = row * 17 + column * 31;
                hash = (hash ^ (hash >>> 16)) * 0x7feb352d;
                hash = (hash ^ (hash >>> 15)) * 0x846ca68b;
                hash ^= hash >>> 16;
                values[row * columns + column] = Math.floorMod(hash, frames);
            }
        }
        return new HordeDataTexture("Horde fixed sample indices", values);
    }

    public static void clear() {
        TABLES.values().forEach(HordeDataTexture::close);
        TABLES.clear();
    }
}
