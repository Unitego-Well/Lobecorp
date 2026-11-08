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
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/// 粗地形样本的高度及横向绕行持续状态；更新、阻尼与采样全部在GPU。
public class HordeTerrainState implements AutoCloseable {
    private static final int STATE_WORDS = 4;
    private static final int ROW_WIDTH = 1024;
    private static final int PARAMETER_BYTES = 32;
    private static final float RESPONSE_OMEGA = 10;
    private static final RenderPipeline UPDATE = RenderPipeline.builder()
            .withLocation(Lobecorp.id("pipeline/horde_terrain_update"))
            .withVertexShader(Lobecorp.id("core/horde_motion"))
            .withFragmentShader(Lobecorp.id("core/horde_terrain_update"))
            .withSampler("TerrainTargets").withSampler("TerrainPrevious")
            .withUniform("TerrainParameters", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
    private GpuTexture read;
    private GpuTexture write;
    private GpuTextureView readView;
    private GpuTextureView writeView;
    private GpuBuffer parameters;
    private ByteBuffer memory;
    private int width;
    private int height;
    private double lastTime = Double.NaN;

	public static boolean precompile() {
		return RenderSystem.getDevice().precompilePipeline(UPDATE).isValid();
	}

    public void update(HordeClient.ClientEvent event, HordeDataTexture targets, double time) {
        if (read != null && time == lastTime) {
            return;
        }
        ensureState(Math.multiplyExact(event.nodes.length, event.heightColumns));
        memory.clear();
        memory.putInt(event.nodes.length).putInt(event.heightColumns).putInt(width).putInt(Double.isNaN(lastTime) ? 1 : 0);
        memory.putFloat(Double.isNaN(lastTime) ? 0 : (float) Math.max(0, (time - lastTime) / 20))
                .putFloat(RESPONSE_OMEGA).putFloat((float) event.groundWidth)
                .putFloat((float) HordeSamplingUtil.MAX_VISUAL_OFFSET).flip();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(parameters.slice(), memory);
        HordeRenderMetrics.add(HordeRenderMetrics.Counter.UPLOAD_BYTES, PARAMETER_BYTES);
        long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.TERRAIN,
                (long) event.nodes.length * event.heightColumns);
        try (var pass = encoder.createRenderPass(() -> "Horde smooth terrain field", writeView, OptionalInt.empty())) {
            var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            HordeIrisShadows.setNativePipeline(pass, UPDATE);
            pass.setViewport(0, 0, width, height);
            pass.setUniform("TerrainParameters", parameters.slice());
            pass.bindTexture("TerrainTargets", targets.texture().getTextureView(), sampler);
            pass.bindTexture("TerrainPrevious", readView, sampler);
            pass.draw(0, 3);
            HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
            HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
        } finally {
            HordeRenderMetrics.end(HordeRenderMetrics.Stage.TERRAIN, started);
        }
        var previous = read;
        read = write;
        write = previous;
        var previousView = readView;
        readView = writeView;
        writeView = previousView;
        lastTime = time;
    }

    private void ensureState(int count) {
        if (read != null) {
            return;
        }
        var device = RenderSystem.getDevice();
        width = Math.min(ROW_WIDTH, device.getMaxTextureSize());
        height = Math.ceilDiv(Math.multiplyExact(count, STATE_WORDS), width);
        if (height > device.getMaxTextureSize()) {
            throw new IllegalArgumentException("群体地形状态超过设备纹理容量");
        }
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST;
        try {
            read = device.createTexture(() -> "Horde terrain state read", usage, TextureFormat.RGBA8, width, height, 1, 1);
            write = device.createTexture(() -> "Horde terrain state write", usage, TextureFormat.RGBA8, width, height, 1, 1);
            readView = device.createTextureView(read);
            writeView = device.createTextureView(write);
            parameters = device.createBuffer(() -> "Horde terrain parameters", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, PARAMETER_BYTES);
            memory = MemoryUtil.memAlloc(PARAMETER_BYTES);
            var encoder = device.createCommandEncoder();
            encoder.clearColorTexture(read, 0);
            encoder.clearColorTexture(write, 0);
        } catch (RuntimeException exception) {
            close();
            throw exception;
        }
    }

    public GpuTextureView view() {
        return readView;
    }

    @Override
    public void close() {
        if (readView != null) {
            readView.close();
            readView = null;
        }
        if (writeView != null) {
            writeView.close();
            writeView = null;
        }
        if (read != null) {
            read.close();
            read = null;
        }
        if (write != null) {
            write.close();
            write = null;
        }
        if (parameters != null) {
            parameters.close();
            parameters = null;
        }
        if (memory != null) {
            MemoryUtil.memFree(memory);
            memory = null;
        }
        lastTime = Double.NaN;
    }
}
