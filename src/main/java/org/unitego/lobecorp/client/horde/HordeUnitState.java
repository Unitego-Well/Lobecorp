package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import java.util.List;

/// CPU只维护按行环页的稳定编号映射；位置、限速和方向历史保留在GPU双缓冲，避免每帧重建查找纹理。
public class HordeUnitState implements AutoCloseable {
    public static final int VALUES = 12;
    private static final int TILE_UNITS = 16;
    private static final int TILE_WIDTH = TILE_UNITS * VALUES;
    private static final int MIN_RING = 2;
    private static final int MAX_RING = 1024;
    private static final long MAX_BYTES = 32L * 1024 * 1024;
    private final GpuTexture[] textures = new GpuTexture[2];
    private final GpuTextureView[] views = new GpuTextureView[2];
    private HordeDataTexture lookup;
    private int mask;
    private int ringMask;
    private int columnTiles;
    private int rowTiles;
    private int slots;
    private int width;
    private int height;
    private int current;
    private long lastDraw = Long.MIN_VALUE;
    private int configuredRing = -1;
    private int configuredColumns = -1;

    public record Range(HordeClient.Batch batch, int offset, int count) {
    }

    /// 行跨度决定环页大小；可见范围内的行不会被同一槽位覆盖，因此查找纹理只在尺寸变化时重建。
    public void prepare(List<Range> ranges, long draw) {
        if (ranges.isEmpty()) {
            return;
        }
        int columns = 0;
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (var range : ranges) {
            var batch = range.batch();
            int rowSize = batch.block().columns();
            columns = Math.max(columns, batch.block().firstColumn() + rowSize);
            long start = batch.offset() + range.offset();
            long end = start + range.count() - 1L;
            int first = Math.toIntExact(batch.firstRow() + start / rowSize);
            int last = Math.toIntExact(batch.firstRow() + end / rowSize);
            minRow = Math.min(minRow, first);
            maxRow = Math.max(maxRow, last);
        }
        int spanTiles = Math.max(MIN_RING, Math.ceilDiv(maxRow - minRow + 1, TILE_UNITS) + 1);
        int target = Integer.highestOneBit(spanTiles - 1) * 2;
        int perColumn = Math.ceilDiv(columns, TILE_UNITS);
        int rowTiles = Math.min(8, RenderSystem.getDevice().getMaxTextureSize() / TILE_WIDTH);
        if (rowTiles <= 0) {
            throw new IllegalArgumentException("GPU不支持单位历史页尺寸");
        }
        long budgetSlots = MAX_BYTES / (2L * TILE_WIDTH * TILE_UNITS * Integer.BYTES);
        int ring = Math.min(MAX_RING, target);
        while (ring > MIN_RING && (long) ring * perColumn > budgetSlots) {
            ring >>= 1;
        }
        if (ring != configuredRing || perColumn != configuredColumns || lookup == null) {
            configure(perColumn, rowTiles, ring);
        }
    }

    private void configure(int perColumn, int atlasRowTiles, int ring) {
        int newSlots = ring * perColumn;
        int targetWidth = atlasRowTiles * TILE_WIDTH;
        int targetHeight = Math.ceilDiv(newSlots, atlasRowTiles) * TILE_UNITS;
        var device = RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        var replacement = new GpuTexture[2];
        var replacementViews = new GpuTextureView[2];
        try {
            for (int index = 0; index < 2; index++) {
                int slot = index;
                replacement[index] = device.createTexture(() -> "Horde stable unit history " + slot,
                        GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT
                                | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
                        TextureFormat.RGBA8, targetWidth, targetHeight, 1, 1);
                replacementViews[index] = device.createTextureView(replacement[index]);
                encoder.clearColorTexture(replacement[index], -1);
            }
        } catch (RuntimeException | OutOfMemoryError failure) {
            release(replacementViews, replacement);
            throw failure;
        }
        release(views, textures);
        System.arraycopy(replacement, 0, textures, 0, 2);
        System.arraycopy(replacementViews, 0, views, 0, 2);
        width = targetWidth;
        height = targetHeight;
        slots = newSlots;
        this.rowTiles = atlasRowTiles;
        columnTiles = perColumn;
        ringMask = ring - 1;
        configuredRing = ring;
        configuredColumns = perColumn;
        current = 0;
        lastDraw = Long.MIN_VALUE;
        rebuildLookup();
    }

    private void rebuildLookup() {
        int count = 2;
        while (count < slots * 2) {
            count <<= 1;
        }
        int[] words = new int[count * 4];
        mask = count - 1;
        for (int row = 0; row <= ringMask; row++) {
            for (int column = 0; column < columnTiles; column++) {
                int at = hash(row, 0, column) & mask;
                while (words[at * 4 + 3] != 0) {
                    at = at + 1 & mask;
                }
                words[at * 4] = row;
                words[at * 4 + 1] = 0;
                words[at * 4 + 2] = column;
                words[at * 4 + 3] = row * columnTiles + column + 1;
            }
        }
        var replacement = new HordeDataTexture("Horde stable unit page index", words);
        if (lookup != null) {
            lookup.close();
        }
        lookup = replacement;
        HordeRenderMetrics.add(HordeRenderMetrics.Counter.UPLOAD_BYTES, (long) words.length * Integer.BYTES);
    }

    private static int hash(int x, int y, int z) {
        return x * 73856093 ^ y * 19349663 ^ z * 83492791;
    }

    public long beginFrame(long draw) {
        if (lastDraw == draw) {
            return 0;
        }
        int target = 1 - current;
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                textures[current], textures[target], 0, 0, 0, 0, 0, width, height);
        current = target;
        lastDraw = draw;
        return (long) width * height * Integer.BYTES;
    }

    public int mask() {
        return mask;
    }

    public int ringMask() {
        return ringMask;
    }

    public int columnTiles() {
        return columnTiles;
    }

    public GpuTextureView current() {
        return views[current];
    }

    public GpuTextureView previous() {
        return views[1 - current];
    }

    public GpuTextureView index() {
        return lookup.texture().getTextureView();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    private static void release(GpuTextureView[] views, GpuTexture[] textures) {
        for (int index = 0; index < textures.length; index++) {
            try {
                if (views[index] != null) {
                    views[index].close();
                }
            } finally {
                if (textures[index] != null) {
                    textures[index].close();
                }
                views[index] = null;
                textures[index] = null;
            }
        }
    }

    @Override
    public void close() {
        release(views, textures);
        if (lookup != null) {
            lookup.close();
        }
        lookup = null;
        width = height = slots = mask = ringMask = columnTiles = rowTiles = current = 0;
        configuredRing = -1;
        configuredColumns = -1;
        lastDraw = Long.MIN_VALUE;
    }
}
