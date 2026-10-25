package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/// CPU仅维护16×16稳定编号页；位置、速度限制和方向历史保留于GPU双缓冲。
public class HordeUnitState implements AutoCloseable {
    public static final int VALUES = 12;
    private static final int TILE_UNITS = 16;
    private static final int TILE_WIDTH = TILE_UNITS * VALUES;
    private static final int RETAIN_DRAWS = 120;
    private static final long MAX_BYTES = 32L * 1024 * 1024;
    private final Map<Tile, Slot> slots = new HashMap<>();
    private final ArrayDeque<Integer> free = new ArrayDeque<>();
    private final GpuTexture[] textures = new GpuTexture[2];
    private final GpuTextureView[] views = new GpuTextureView[2];
    private HordeDataTexture lookup;
    private int mask;
    private int nextSlot;
    private int capacity;
    private int width;
    private int height;
    private int current;
    private long lastDraw = Long.MIN_VALUE;

    public record Range(HordeClient.Batch batch, int offset, int count) {
    }

    private record Tile(long row, int column) {
    }

    private static class Slot {
        private final int index;
        private long seen;

        private Slot(int index, long seen) {
            this.index = index;
            this.seen = seen;
        }
    }

    public void prepare(List<Range> ranges, long draw) {
        var required = new HashSet<Tile>();
        for (var range : ranges) {
            var batch = range.batch();
            int columns = batch.block().columns();
            long first = batch.offset() + range.offset();
            long last = first + range.count() - 1;
            long row0 = (batch.firstRow() + first / columns) >> 4;
            long row1 = (batch.firstRow() + last / columns) >> 4;
            int col0 = batch.block().firstColumn() >> 4;
            int col1 = (batch.block().firstColumn() + columns - 1) >> 4;
            for (long row = row0; row <= row1; row++) {
                for (int col = col0; col <= col1; col++) required.add(new Tile(row, col));
            }
        }
        int maximumTexture = RenderSystem.getDevice().getMaxTextureSize();
        int tilesPerRow = Math.min(8, maximumTexture / TILE_WIDTH);
        if (tilesPerRow == 0) throw new IllegalArgumentException("GPU不支持单位历史页尺寸");
        int targetWidth = tilesPerRow * TILE_WIDTH;
        int maximumRows = (int) Math.min(maximumTexture, MAX_BYTES / (targetWidth * 8L));
        int maximumSlots = maximumRows / TILE_UNITS * tilesPerRow;
        if (required.size() > maximumSlots) {
            throw new IllegalArgumentException("当前可见编号页超过GPU历史预算，不截断单位");
        }
        int additions = 0;
        for (var tile : required) if (!slots.containsKey(tile)) additions++;
        boolean pressure = slots.size() + additions > maximumSlots;
        boolean changed = false;
        var iterator = slots.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!required.contains(entry.getKey()) && (pressure || draw - entry.getValue().seen > RETAIN_DRAWS)) {
                free.addLast(entry.getValue().index);
                iterator.remove();
                changed = true;
            }
        }
        for (var tile : required) {
            var slot = slots.get(tile);
            if (slot == null) {
                slot = new Slot(free.isEmpty() ? nextSlot++ : free.removeFirst(), draw);
                slots.put(tile, slot);
                changed = true;
            }
            slot.seen = draw;
        }
        ensureAtlas(Math.max(1, nextSlot), targetWidth, tilesPerRow, maximumSlots);
        if (changed || lookup == null) rebuildLookup();
    }

    private void ensureAtlas(int count, int targetWidth, int tilesPerRow, int maximumSlots) {
        if (textures[current] != null && capacity >= count && width == targetWidth) return;
        int targetCapacity = Math.min(maximumSlots, Integer.highestOneBit(Math.max(1, count - 1)) * 2);
        int targetHeight = Math.ceilDiv(targetCapacity, tilesPerRow) * TILE_UNITS;
        var device = RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        var replacement = new GpuTexture[2];
        var replacementViews = new GpuTextureView[2];
        try {
            for (int i = 0; i < 2; i++) {
                int slot = i;
                replacement[i] = device.createTexture(() -> "Horde stable unit history " + slot,
                        GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT
                                | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST,
                        TextureFormat.RGBA8, targetWidth, targetHeight, 1, 1);
                replacementViews[i] = device.createTextureView(replacement[i]);
                encoder.clearColorTexture(replacement[i], -1);
            }
            if (textures[current] != null && width == targetWidth) {
                encoder.copyTextureToTexture(textures[current], replacement[0], 0, 0, 0, 0, 0, width, height);
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
        capacity = targetCapacity;
        current = 0;
        lastDraw = Long.MIN_VALUE;
    }

    private void rebuildLookup() {
        int count = 2;
        while (count < slots.size() * 2) count <<= 1;
        int[] words = new int[count * 4];
        mask = count - 1;
        for (var entry : slots.entrySet()) {
            var tile = entry.getKey();
            int low = (int) tile.row(), high = (int) (tile.row() >>> 32);
            int at = (low * 73856093 ^ high * 19349663 ^ tile.column() * 83492791) & mask;
            while (words[at * 4 + 3] != 0) at = at + 1 & mask;
            words[at * 4] = low;
            words[at * 4 + 1] = high;
            words[at * 4 + 2] = tile.column();
            words[at * 4 + 3] = entry.getValue().index + 1;
        }
        var replacement = new HordeDataTexture("Horde stable unit page index", words);
        if (lookup != null) lookup.close();
        lookup = replacement;
        HordeRenderMetrics.add(HordeRenderMetrics.Counter.UPLOAD_BYTES, (long) words.length * Integer.BYTES);
    }

    public long beginFrame(long draw) {
        if (lastDraw == draw) return 0;
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
        for (int i = 0; i < textures.length; i++) {
            try {
                if (views[i] != null) views[i].close();
            } finally {
                if (textures[i] != null) textures[i].close();
                views[i] = null;
                textures[i] = null;
            }
        }
    }

    @Override
    public void close() {
        release(views, textures);
        if (lookup != null) lookup.close();
        lookup = null;
        slots.clear();
        free.clear();
        width = height = capacity = nextSlot = mask = current = 0;
        lastDraw = Long.MIN_VALUE;
    }
}
