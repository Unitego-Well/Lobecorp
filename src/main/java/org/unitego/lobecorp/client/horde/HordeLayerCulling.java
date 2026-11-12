package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Consumer;

/// 实验性逐像素不同单位深度剥层，随后同步读回可见标志。
/// 借用的输入资源须在整个选择过程保持有效；不调用原生图形接口。
public class HordeLayerCulling implements AutoCloseable {
    private static final int PARAMETER_RESOURCE_BYTES = 32 + 112 + 112;
    private static final RenderPipeline MASK = RenderPipeline.builder()
        .withLocation(Lobecorp.id("pipeline/horde_layer_scatter"))
        .withVertexShader(Lobecorp.id("core/horde_layer_scatter"))
        .withFragmentShader(Lobecorp.id("core/horde_layer_scatter"))
        .withUniform("HordeLayerMaskParameters", UniformType.UNIFORM_BUFFER)
        .withSampler("LayerHistory")
        .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.POINTS)
        .withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();

    private GpuTexture temp, depth, history, flags;
    private GpuTextureView tempView, depthView, historyView, flagsView;
    private GpuBuffer params, maskParams, readback;
    private ByteBuffer upload;
    private int width, height, columns, rows, flagWidth, flagHeight;
    private long allocated;


    public static RenderPipeline pipeline(VertexFormat format, boolean reverse, boolean cull) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
            .withLocation(Lobecorp.id("pipeline/horde_layer_" + reverse + "_" + cull))
            .withVertexShader(Lobecorp.id("core/horde_layer"))
            .withFragmentShader(Lobecorp.id("core/horde_layer"))
            .withSampler("Sampler0").withSampler("Sampler2")
            .withSampler("HordeMotion").withSampler("HordeAnimation")
            .withSampler("HordeLodSelected").withSampler("HordeLodCompact")
            .withSampler("HordeCardFacing").withSampler("LayerHistory").withSampler("LayerSceneDepth")
            .withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
            .withUniform("HordeLayerParameters", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(format, VertexFormat.Mode.QUADS).withCull(cull)
            .withDepthStencilState(new DepthStencilState(reverse ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, true))
            .withColorTargetState(ColorTargetState.DEFAULT).build();
    }

    private static int[] identity(int count) {
        int[] result = new int[count];
        Arrays.setAll(result, i -> i);
        return result;
    }

    private Result unsupported(Input in, String reason) {
        return new Result(identity(in.totalUnits()), false, reason, 0, allocated);
    }

    public Result select(Input in) {
        if (in.totalUnits() < 0 || in.layers() < -1) {
            throw new IllegalArgumentException("Invalid layer input");
        }
        if (in.layers() == 0 || in.totalUnits() == 0) {
            return new Result(new int[0], true, "empty", 0, allocated);
        }
        if (in.layers() == -1 || in.layers() >= in.totalUnits()) {
            return new Result(identity(in.totalUnits()), true, "identity", 0, allocated);
        }
        if (in.width() <= 0 || in.height() <= 0 || !Float.isFinite(in.radius()) || in.radius() < 0
                || in.center() == null || !Float.isFinite(in.center().x())
                || !Float.isFinite(in.center().y()) || !Float.isFinite(in.center().z())) {
            return unsupported(in, "invalid viewport or conservative bounds");
        }
        var device = RenderSystem.getDevice();
        int max = device.getMaxTextureSize();
        if (in.width() > max || in.height() > max) {
            return unsupported(in, "viewport exceeds device limit");
        }
        int cols = Math.min(in.layers(), max / in.width());
        int tileRows = Math.ceilDiv(in.layers(), cols);
        if (tileRows > max / in.height()) {
            return unsupported(in, "history atlas exceeds device limit");
        }
        int fw = Math.min(1024, max), fh = Math.ceilDiv(in.totalUnits(), fw);
        if (fh > max) {
            return unsupported(in, "flag texture exceeds device limit");
        }
        boolean sameTargets = temp != null && width == in.width() && height == in.height()
            && columns == cols && rows == tileRows;
        int retainedHeight = sameTargets && flagWidth == fw ? flagHeight : 0;
        int capacityHeight = retainedHeight;
        if (capacityHeight < fh) {
            capacityHeight = (int) Math.min(max, Long.highestOneBit((long) fh - 1) * 2);
            capacityHeight = Math.max(fh, capacityHeight);
        }
        long targetBytes = 8L * in.width() * in.height()
            + 4L * cols * tileRows * in.width() * in.height() + PARAMETER_RESOURCE_BYTES;
        long budget = targetBytes + 8L * fw * capacityHeight;
        // 接近预算时允许精确增长，但不因单位减少而收缩已有容量。
        if (budget > in.maxResourceBytes() && retainedHeight < fh) {
            capacityHeight = fh;
            budget = targetBytes + 8L * fw * capacityHeight;
        }
        if (budget > in.maxResourceBytes() || 4L * fw * capacityHeight > Integer.MAX_VALUE) {
            return unsupported(in, "actual resource capacity exceeds budget or mapping limit: " + budget);
        }
        long sourcePixels = (long) in.width() * in.height() * in.layers();
        if (sourcePixels > Integer.MAX_VALUE) {
            return unsupported(in, "scatter source exceeds draw address range");
        }
        try {
            ensure(in.width(), in.height(), cols, tileRows, fw, capacityHeight, budget);
            var encoder = device.createCommandEncoder();
            var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            encoder.clearColorTexture(history, 0);
            for (int layer = 0; layer < in.layers(); layer++) {
                upload.clear();
                upload.putInt(width).putInt(height).putInt(columns).putInt(0)
                    .putInt(layer).putInt(in.reverse() ? 1 : 0).putInt(0).putInt(0).flip();
                encoder.writeToBuffer(params.slice(), upload);
                try (var pass = encoder.createRenderPass(() -> "Horde layer peel", tempView, OptionalInt.of(0),
                        depthView, OptionalDouble.of(in.reverse() ? 0 : 1))) {
                    HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
                    in.bindDefaults().accept(pass);
                    for (var draw : in.draws()) {
                        HordeIrisShadows.setNativePipeline(pass, draw.pipeline());
                        pass.setUniform("HordeLayerParameters", params);
                        pass.bindTexture("LayerHistory", historyView, nearest);
                        pass.bindTexture("LayerSceneDepth", in.sceneDepth(), nearest);
                        pass.setViewport(0, 0, width, height);
                        draw.submit().accept(pass);
                    }
                }
                encoder.copyTextureToTexture(temp, history, 0,
                    layer % columns * width, layer / columns * height, 0, 0, width, height);
            }
            upload.clear();
            in.modelView().get(upload);
            upload.position(64);
            upload.putInt(width).putInt(height).putInt(columns).putInt(device.isZZeroToOne() ? 1 : 0)
                .putInt(in.layers()).putInt(in.totalUnits()).putInt(flagWidth).putInt(fh)
                .putFloat(in.center().x()).putFloat(in.center().y()).putFloat(in.center().z()).putFloat(in.radius()).flip();
            encoder.writeToBuffer(maskParams.slice(), upload);
            try (var pass = encoder.createRenderPass(() -> "Horde layer visible flags", flagsView, OptionalInt.of(0))) {
                HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
                HordeIrisShadows.setNativePipeline(pass, MASK);
                pass.setUniform("HordeLayerMaskParameters", maskParams);
                pass.bindTexture("LayerHistory", historyView, nearest);
                pass.setViewport(0, 0, flagWidth, fh);
                // 仅遍历有效层，不读取图集填充区；保持完整分辨率。
                int totalPixels = (int) sourcePixels;
                for (int first = 0; first < totalPixels;) {
                    int count = Math.min(1_000_000, totalPixels - first);
                    pass.draw(first, count);
                    HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
                    first += count;
                }
            }
            long bytes = 4L * flagWidth * fh;
            encoder.copyTextureToBuffer(flags, readback, 0, () -> {}, 0, 0, 0, flagWidth, fh);
            // 映射会同步等待复制完成；不要在渲染线程等待完成回调。
            int[] visible = new int[in.totalUnits()];
            int count = 0;
            try (var mapped = encoder.mapBuffer(readback, true, false)) {
                var data = mapped.data();
                for (int id = 0; id < in.totalUnits(); id++) {
                    if (data.get(id * 4) != 0) {
                        visible[count++] = id;
                    }
                }
            }
            return new Result(Arrays.copyOf(visible, count), true, "peeled-sync-readback", bytes, allocated);
        } catch (RuntimeException | OutOfMemoryError failure) {
            close();
            return unsupported(in, "peeling failed: " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private void ensure(int w, int h, int c, int r, int fw, int capacityHeight, long bytes) {
        var device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING
            | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;
        // 单位数量变化不会销毁大目标；只有视口或历史图集物理尺寸变化才整体重建。
        if (temp == null || width != w || height != h || columns != c || rows != r) {
            close();
            width = w;
            height = h;
            columns = c;
            rows = r;
            temp = device.createTexture(() -> "Horde peel temporary IDs", usage, TextureFormat.RGBA8, w, h, 1, 1);
            depth = device.createTexture(() -> "Horde peel own depth", GpuTexture.USAGE_RENDER_ATTACHMENT,
                TextureFormat.DEPTH32, w, h, 1, 1);
            history = device.createTexture(() -> "Horde peel history atlas", usage, TextureFormat.RGBA8, w * c, h * r, 1, 1);
            tempView = device.createTextureView(temp);
            depthView = device.createTextureView(depth);
            historyView = device.createTextureView(history);
            params = device.createBuffer(() -> "Horde peel parameters", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 32);
            maskParams = device.createBuffer(() -> "Horde peel mask parameters", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 112);
            upload = MemoryUtil.memAlloc(112);
        }
        if (flags == null || flagWidth != fw || flagHeight < capacityHeight) {
            // 先释放旧小目标，再分配新容量，避免增长瞬间叠加两份读回资源。
            closeFlags();
            flags = device.createTexture(() -> "Horde visible flags", usage, TextureFormat.RGBA8, fw, capacityHeight, 1, 1);
            flagsView = device.createTextureView(flags);
            readback = device.createBuffer(() -> "Horde visible flags readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, 4L * fw * capacityHeight);
            flagWidth = fw;
            flagHeight = capacityHeight;
        }
        allocated = bytes;
    }

    private void closeFlags() {
        if (flagsView != null) {
            flagsView.close();
            flagsView = null;
        }
        if (flags != null) {
            flags.close();
            flags = null;
        }
        if (readback != null) {
            readback.close();
            readback = null;
        }
        flagWidth = 0;
        flagHeight = 0;
    }

    /// 返回借用的可见标志视图，仅在最近一次选择真实剥层成功后使用。
    /// 身份直通、空结果或不支持路径不得消费此视图；后续资源重建或关闭会使借用失效。
    public GpuTextureView flagsView() {
        return flagsView;
    }

    @Override
    public void close() {
        closeFlags();
        if (tempView != null) {
            tempView.close();
            tempView = null;
        }
        if (depthView != null) {
            depthView.close();
            depthView = null;
        }
        if (historyView != null) {
            historyView.close();
            historyView = null;
        }
        if (temp != null) {
            temp.close();
            temp = null;
        }
        if (depth != null) {
            depth.close();
            depth = null;
        }
        if (history != null) {
            history.close();
            history = null;
        }
        if (params != null) {
            params.close();
            params = null;
        }
        if (maskParams != null) {
            maskParams.close();
            maskParams = null;
        }
        if (upload != null) {
            MemoryUtil.memFree(upload);
            upload = null;
        }
        allocated = 0;
        width = 0;
        height = 0;
        columns = 0;
        rows = 0;
    }

    public record Draw(RenderPipeline pipeline, Consumer<RenderPass> submit) {}

    /// 默认绑定回调须绑定投影及几何管线使用的动态和默认统一参数。
    /// 中心与未缩放半径须保守覆盖所有模型的完整动画和方向片；着色器应用单位朝向与缩放。
    public record Input(int layers, int totalUnits, int width, int height, boolean reverse,
                        GpuTextureView sceneDepth, GpuTextureView motion, Matrix4fc modelView,
                        Vector3fc center, float radius, long maxResourceBytes, List<Draw> draws,
                        Consumer<RenderPass> bindDefaults) {}

    public record Result(int[] visibleIDs, boolean supported, String status,
                         long readbackBytes, long resourceBytes) {}
}
