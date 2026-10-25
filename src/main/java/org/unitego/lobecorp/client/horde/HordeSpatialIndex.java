package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.OptionalInt;

/// CPU只准备整数索引域；候选位图、密度判断及32/16/8细分完全由GPU生成。
public class HordeSpatialIndex implements AutoCloseable {
    private static final int COARSE_SIZE = 32;
    private static final int LEVELS = 3;
    private static final int ROW_WIDTH = 1024;
    private static final int PARAMETER_BYTES = 96;
    // 每级最多16MiB索引，不限制区域数；不足时用上一档或完整查询。
    private static final long MAX_WORDS = 4L * 1024 * 1024;
    private static final int DOMAIN_PADDING = 128;
    private static final int REFINE_THRESHOLD = 8;
    private static final RenderPipeline BUILD = RenderPipeline.builder()
            .withLocation(Lobecorp.id("pipeline/horde_spatial_index"))
            .withVertexShader(Lobecorp.id("core/horde_motion"))
            .withFragmentShader(Lobecorp.id("core/horde_spatial_index"))
            .withSampler("IndexTargets").withSampler("IndexState").withSampler("IndexParent")
            .withUniform("IndexParameters", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
	// 本对象拥有三个级别，close统一释放，不在单次借用结束时关闭。
	@SuppressWarnings("resource")
	private final Level[] levels = {new Level(), new Level(), new Level()};
	private Grid grid = Grid.empty();
    private GpuBuffer parameters;
    private ByteBuffer memory;
    private int parameterStride;
    private double lastTime = Double.NaN;
    private float lastRadius = Float.NaN;
    private long lastVersion = Long.MIN_VALUE;
    private int lastActive = -1;

	public static boolean precompile() {
		return RenderSystem.getDevice().precompilePipeline(BUILD).isValid();
	}

    public void update(HordeClient.ClientEvent event, HordeRegionState state, double time, float radius) {
        if (time == lastTime && event.regionVersion == lastVersion && state.activeCount() == lastActive && radius == lastRadius) {
            return;
        }
        int maximum = RenderSystem.getDevice().getMaxTextureSize();
	    int width = Math.min(ROW_WIDTH, maximum);
	    if (lastVersion != event.regionVersion || lastActive != state.activeCount()) {
		    grid = Grid.plan(event.regions.values(), state.activeCount(), Math.min(MAX_WORDS, (long) width * maximum));
	    }
        if (grid.flags() == 0) {
            lastTime = time;
            lastRadius = radius;
            lastVersion = event.regionVersion;
            lastActive = state.activeCount();
            return;
        }
        ensureParameters();
        for (int level = 0; level < LEVELS && (grid.flags() & 1 << level) != 0; level++) {
            levels[level].ensure(grid.words(level), width, maximum);
            writeParameters(event, state, level, radius);
        }
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.SPATIAL, grid.words(0));
        try {
            for (int level = 0; level < LEVELS && (grid.flags() & 1 << level) != 0; level++) {
                var output = levels[level];
                var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                try (var pass = encoder.createRenderPass(() -> "Horde adaptive spatial index", output.view, OptionalInt.empty())) {
                    pass.setPipeline(BUILD);
                    pass.setViewport(0, 0, output.width, output.height);
                    pass.setUniform("IndexParameters", parameters.slice((long) level * parameterStride, PARAMETER_BYTES));
                    pass.bindTexture("IndexTargets", state.raw().texture().getTextureView(), sampler);
                    pass.bindTexture("IndexState", state.view(), sampler);
                    pass.bindTexture("IndexParent", level == 0 ? state.view() : levels[level - 1].view, sampler);
                    pass.draw(0, 3);
                    HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
                    HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
                    HordeRenderMetrics.add(HordeRenderMetrics.Counter.SPATIAL_WORDS, grid.words(level));
                    HordeRenderMetrics.add(HordeRenderMetrics.Counter.SPATIAL_LEVELS, 1);
                }
            }
        } finally {
            HordeRenderMetrics.end(HordeRenderMetrics.Stage.SPATIAL, started);
        }
        lastTime = time;
	    lastRadius = radius;
        lastVersion = event.regionVersion;
        lastActive = state.activeCount();
    }

    private void ensureParameters() {
        if (parameters != null) {
            return;
        }
        int alignment = RenderSystem.getDevice().getUniformOffsetAlignment();
        parameterStride = Math.ceilDiv(PARAMETER_BYTES, alignment) * alignment;
        parameters = RenderSystem.getDevice().createBuffer(() -> "Horde spatial parameters",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, (long) parameterStride * LEVELS);
        memory = MemoryUtil.memAlloc(PARAMETER_BYTES);
    }

    private void writeParameters(HordeClient.ClientEvent event, HordeRegionState state, int level, float radius) {
        int factor = 1 << level;
        memory.clear();
        memory.putInt(grid.x()).putInt(grid.y()).putInt(grid.z()).putInt(state.activeCount());
        memory.putInt(grid.nx() * factor).putInt(grid.ny() * factor).putInt(grid.nz() * factor).putInt(grid.stride());
        memory.putInt(COARSE_SIZE / factor).putInt(levels[level].width).putInt(state.slotCount()).putInt(level);
        memory.putInt((int) Math.floor(event.origin.x)).putInt((int) Math.floor(event.origin.y))
                .putInt((int) Math.floor(event.origin.z)).putInt(level == 0 ? 0 : levels[level - 1].width);
        memory.putFloat((float) (event.origin.x - Math.floor(event.origin.x)))
                .putFloat((float) (event.origin.y - Math.floor(event.origin.y)))
                .putFloat((float) (event.origin.z - Math.floor(event.origin.z))).putFloat(radius);
        memory.putFloat(event.entityHeight).putFloat(REFINE_THRESHOLD).putFloat(0).putFloat(0).flip();
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(
                parameters.slice((long) level * parameterStride, PARAMETER_BYTES), memory);
        HordeRenderMetrics.add(HordeRenderMetrics.Counter.UPLOAD_BYTES, PARAMETER_BYTES);
    }

    public Grid grid() {
        return grid;
    }

    public int width(int level) {
        return (grid.flags() & 1 << level) == 0 ? 0 : levels[level].width;
    }

    public GpuTextureView view(int level, HordeRegionState fallback) {
        return width(level) == 0 ? fallback.view() : levels[level].view;
    }

    @Override
    public void close() {
        for (var level : levels) {
            level.close();
        }
        if (parameters != null) {
            parameters.close();
            parameters = null;
        }
        if (memory != null) {
            MemoryUtil.memFree(memory);
            memory = null;
        }
    }

    /// 只决定索引纹理的整数包围域与容量，不在CPU构建候选列表。
    public record Grid(int x, int y, int z, int nx, int ny, int nz, int stride, int flags) {
        public static Grid empty() {
            return new Grid(0, 0, 0, 0, 0, 0, 1, 0);
        }

        public static Grid plan(Collection<HordeClient.RegionBox> boxes, int count, long maximumWords) {
            if (boxes.isEmpty() || count <= 0) {
                return empty();
            }
            long minX = Long.MAX_VALUE, minY = Long.MAX_VALUE, minZ = Long.MAX_VALUE;
            long maxX = Long.MIN_VALUE, maxY = Long.MIN_VALUE, maxZ = Long.MIN_VALUE;
            for (var box : boxes) {
                minX = Math.min(minX, box.x());
                minY = Math.min(minY, box.y());
                minZ = Math.min(minZ, box.z());
                maxX = Math.max(maxX, (long) box.x() + box.sizeX());
                maxY = Math.max(maxY, (long) box.y() + box.sizeY());
                maxZ = Math.max(maxZ, (long) box.z() + box.sizeZ());
            }
            long x = Math.floorDiv(minX - DOMAIN_PADDING, COARSE_SIZE) * COARSE_SIZE;
            long y = Math.floorDiv(minY - DOMAIN_PADDING, COARSE_SIZE) * COARSE_SIZE;
            long z = Math.floorDiv(minZ - DOMAIN_PADDING, COARSE_SIZE) * COARSE_SIZE;
            long nx = Math.ceilDiv(maxX + DOMAIN_PADDING - x, COARSE_SIZE);
            long ny = Math.ceilDiv(maxY + DOMAIN_PADDING - y, COARSE_SIZE);
            long nz = Math.ceilDiv(maxZ + DOMAIN_PADDING - z, COARSE_SIZE);
            long stride = 1L + Math.ceilDiv(count, 32);
            long cells = nx * ny;
            if (nx > maximumWords || ny > maximumWords || nz > maximumWords
                    || cells > maximumWords || cells * nz > maximumWords / stride
                    || x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE
                    || y > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
                return empty();
            }
            long words = cells * nz * stride;
            int flags = 1;
            if (count > REFINE_THRESHOLD && words <= maximumWords / 8) {
                flags |= 2;
                if (words <= maximumWords / 64) {
                    flags |= 4;
                }
            }
            return new Grid((int) x, (int) y, (int) z, (int) nx, (int) ny, (int) nz, (int) stride, flags);
        }

        public long words(int level) {
            return (long) nx * ny * nz * stride * (1L << (level * 3));
        }
    }

    private static class Level implements AutoCloseable {
        private GpuTexture texture;
        private GpuTextureView view;
        private int width;
        private int height;

        private void ensure(long words, int targetWidth, int limit) {
            int rows = Math.toIntExact(Math.ceilDiv(words, targetWidth));
            if (texture != null && rows <= height) {
                return;
            }
            int targetHeight = (int) Math.min(limit, (long) Integer.highestOneBit(Math.max(1, rows - 1)) * 2);
            var device = RenderSystem.getDevice();
            var replacement = device.createTexture(() -> "Horde GPU spatial bitset",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
                    TextureFormat.RGBA8, targetWidth, targetHeight, 1, 1);
            GpuTextureView replacementView;
            try {
                replacementView = device.createTextureView(replacement);
            } catch (RuntimeException exception) {
                replacement.close();
                throw exception;
            }
            close();
            texture = replacement;
            view = replacementView;
            width = targetWidth;
            height = targetHeight;
        }

        @Override
        public void close() {
            if (view != null) {
                view.close();
                view = null;
            }
            if (texture != null) {
                texture.close();
                texture = null;
            }
        }
    }
}
