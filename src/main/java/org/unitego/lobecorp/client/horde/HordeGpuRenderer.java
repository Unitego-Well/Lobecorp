package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/// 两帧三维网格常驻 GPU；每帧只写实例参数，由顶点着色器插值和变换。
public class HordeGpuRenderer {
	private static final int INSTANCES_PER_DRAW = 256, INSTANCE_BYTES = 32;
	private static final int BLOCK_BYTES = INSTANCES_PER_DRAW * INSTANCE_BYTES, VERTEX_BYTES = 40;
	private static final VertexFormatElement NEXT_POSITION = VertexFormatElement.register(
			VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.FLOAT, false, 3);
	private static final VertexFormat FORMAT = VertexFormat.builder()
			.add("Position", VertexFormatElement.POSITION).add("UV0", VertexFormatElement.UV0)
			.add("Color", VertexFormatElement.COLOR).add("NextPosition", NEXT_POSITION)
			.add("Normal", VertexFormatElement.NORMAL).padding(1).build();
	private static final RenderPipeline NORMAL = pipeline(false, false);
	private static final RenderPipeline FADE = pipeline(true, false);
	private static final RenderPipeline REVERSE = pipeline(false, true);
	private static final RenderPipeline REVERSE_FADE = pipeline(true, true);
	private static final LinkedHashMap<MeshKey, Geometry> GEOMETRY = new LinkedHashMap<>(16, 0.75f, true);
	private static final Map<Identifier, Boolean> GLOW = new HashMap<>();
	private static final Matrix4f IDENTITY = new Matrix4f();
	private static final Vector4f WHITE = new Vector4f(1);
	private static final Vector3f ZERO = new Vector3f();
	private static GpuBuffer instances;
	private static ByteBuffer upload;
	private static long residentBytes;
	private static long uploadedBytes;
	private static long frame;
	private static int batches;
	private static int drawn;

	private static RenderPipeline pipeline(boolean fade, boolean reverse) {
		return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
				.withLocation(Lobecorp.id("pipeline/horde_" + (fade ? "fade" : "solid") + (reverse ? "_reverse" : "")))
				.withVertexShader(Lobecorp.id("core/horde_instanced"))
				.withFragmentShader(Lobecorp.id("core/horde_instanced"))
				.withSampler("Sampler0").withSampler("Sampler2").withSampler("Glowmask")
				.withUniform("HordeInstances", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(FORMAT, VertexFormat.Mode.QUADS)
				.withCull(false)
				.withDepthStencilState(new DepthStencilState(reverse ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, !fade))
				.withColorTargetState(fade ? new ColorTargetState(BlendFunction.TRANSLUCENT) : ColorTargetState.DEFAULT)
				.build();
	}

	/// TextureManager 持有纹理；本方法只借用，不关闭它们。
	@SuppressWarnings("resource")
	public static void draw(List<HordeRendering.Draw> draws, Matrix4fc modelView, boolean reverse) {
		frame++;
		batches = 0;
		drawn = 0;
		uploadedBytes = 0;
		if (draws.isEmpty()) {
			trim();
			return;
		}
		var minecraft = Minecraft.getInstance();
		var target = minecraft.getMainRenderTarget();
		if (target.getDepthTextureView() == null || target.getColorTextureView() == null) {
			return;
		}
		var groups = new LinkedHashMap<GroupKey, List<HordeRendering.Draw>>();
		for (var draw : draws) {
			var key = new GroupKey(new MeshKey(draw.meshes().first(), draw.meshes().second()),
					draw.instance().visual().texture(), draw.instance().alpha() < 0.999f);
			groups.computeIfAbsent(key, _ -> new ArrayList<>()).add(draw);
		}
		var prepared = new ArrayList<Batch>();
		int blocks = 0;
		for (var group : groups.entrySet()) {
			var geometry = GEOMETRY.computeIfAbsent(group.getKey().mesh(), HordeGpuRenderer::uploadGeometry);
			geometry.lastFrame = frame;
			for (int first = 0; first < group.getValue().size(); first += INSTANCES_PER_DRAW) {
				int count = Math.min(INSTANCES_PER_DRAW, group.getValue().size() - first);
				prepared.add(new Batch(group.getKey(), geometry, group.getValue(), first, count, blocks++));
			}
		}
		int stride = Math.ceilDiv(BLOCK_BYTES, RenderSystem.getDevice().getUniformOffsetAlignment())
				* RenderSystem.getDevice().getUniformOffsetAlignment();
		int required = Math.multiplyExact(blocks, stride);
		ensureInstances(required);
		upload.clear().limit(required);
		var camera = minecraft.gameRenderer.getMainCamera().position();
		for (var batch : prepared) {
			upload.position(batch.block() * stride);
			Identifier glowId = batch.key().texture().withPath(path -> path.replace(".png", "_glowmask.png"));
			boolean hasGlow = GLOW.computeIfAbsent(glowId, id -> minecraft.getResourceManager().getResource(id).isPresent());
			for (int i = 0; i < batch.count(); i++) {
				var draw = batch.draws().get(batch.first() + i);
				var instance = draw.instance();
				double yaw = Math.PI - Math.atan2(-instance.direction().x, instance.direction().z);
				upload.putFloat((float) (instance.position().x - camera.x))
						.putFloat((float) (instance.position().y - camera.y + 0.01))
						.putFloat((float) (instance.position().z - camera.z)).putFloat(instance.alpha());
				upload.putFloat((float) Math.cos(yaw)).putFloat((float) Math.sin(yaw))
						.putFloat(draw.meshes().weight()).putFloat(hasGlow ? instance.light() : -instance.light() - 1);
			}
		}
		upload.position(0).limit(required);
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(instances.slice(0, required), upload);
		uploadedBytes = required;
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, WHITE, ZERO, IDENTITY);
		var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		int maximumIndices = prepared.stream().mapToInt(batch -> batch.geometry().indices).max().orElse(0);
		var indexBuffer = indices.getBuffer(maximumIndices);
		var lightmap = minecraft.gameRenderer.lightmap();
		var lightSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		var textures = new HashMap<Identifier, TexturePair>();
		for (var key : groups.keySet()) {
			textures.computeIfAbsent(key.texture(), id -> {
				var base = minecraft.getTextureManager().getTexture(id);
				Identifier glowId = id.withPath(path -> path.replace(".png", "_glowmask.png"));
				var glow = GLOW.getOrDefault(glowId, false) ? minecraft.getTextureManager().getTexture(glowId) : base;
				return new TexturePair(base, glow);
			});
		}
		// 所有缓冲上传必须在 RenderPass 开始前完成。
		try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Lobecorp horde instances", target.getColorTextureView(), OptionalInt.empty(),
				target.getDepthTextureView(), OptionalDouble.empty())) {
			// 实体淡出按由远到近的实例顺序提交，不再逐四边形排序整个浪潮。
			for (int phase = 0; phase < 2; phase++) {
				for (var batch : prepared) {
					if (batch.key().fade() != (phase == 1)) {
						continue;
					}
					pass.setPipeline(reverse ? (batch.key().fade() ? REVERSE_FADE : REVERSE)
							: (batch.key().fade() ? FADE : NORMAL));
					RenderSystem.bindDefaultUniforms(pass);
					pass.setUniform("DynamicTransforms", transforms);
					pass.setUniform("HordeInstances", instances.slice((long) batch.block() * stride, BLOCK_BYTES));
					var texture = textures.get(batch.key().texture()).base();
					var glow = textures.get(batch.key().texture()).glow();
					pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
					pass.bindTexture("Glowmask", glow.getTextureView(), glow.getSampler());
					pass.bindTexture("Sampler2", lightmap, lightSampler);
					pass.setVertexBuffer(0, batch.geometry().vertices);
					pass.setIndexBuffer(indexBuffer, indices.type());
					pass.drawIndexed(0, 0, batch.geometry().indices, batch.count());
					batches++;
					drawn += batch.count();
				}
			}
		}
		trim();
	}

	private static Geometry uploadGeometry(MeshKey key) {
		float[] a = key.first().vertices(), b = key.second().vertices();
		int count = a.length / 8;
		var data = MemoryUtil.memAlloc(Math.multiplyExact(count, VERTEX_BYTES));
		try {
			for (int i = 0; i < a.length; i += 8) {
				data.putFloat(a[i]).putFloat(a[i + 1]).putFloat(a[i + 2]);
				data.putFloat(a[i + 3]).putFloat(a[i + 4]);
				for (int n = 5; n < 8; n++) {
					data.put((byte) Math.round((Math.clamp(a[i + n], -1, 1) * 0.5f + 0.5f) * 255));
				}
				data.put((byte) 255);
				data.putFloat(b[i]).putFloat(b[i + 1]).putFloat(b[i + 2]);
				for (int n = 5; n < 8; n++) {
					data.put((byte) Math.round(Math.clamp(b[i + n], -1, 1) * 127));
				}
				data.put((byte) 0);
			}
			data.flip();
			GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Horde shared pose", GpuBuffer.USAGE_VERTEX, data);
			residentBytes += data.remaining();
			return new Geometry(buffer, count / 4 * 6);
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	private static void ensureInstances(int size) {
		if (instances != null && instances.size() >= size) {
			return;
		}
		if (instances != null) {
			instances.close();
			MemoryUtil.memFree(upload);
		}
		int capacity = Math.max(BLOCK_BYTES, Integer.highestOneBit(Math.max(1, size - 1)) << 1);
		instances = RenderSystem.getDevice().createBuffer(() -> "Horde instance parameters",
				GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, capacity);
		upload = MemoryUtil.memAlloc(capacity);
	}

	private static void trim() {
		long budget = HordeVisualConfig.CACHE_MIB.get() * 1024L * 1024;
		var iterator = GEOMETRY.entrySet().iterator();
		while (iterator.hasNext()) {
			var geometry = iterator.next().getValue();
			if (geometry.lastFrame != frame && (residentBytes > budget || frame - geometry.lastFrame > 1200)) {
				residentBytes -= geometry.vertices.size();
				geometry.vertices.close();
				iterator.remove();
			}
		}
	}

	public static String statistics() {
		return "GPU instances=" + drawn + ", batches=" + batches + ", upload=" + uploadedBytes
				+ "B, resident=" + residentBytes / 1024 + "KiB, shadows=0";
	}

	public static void clear() {
		GEOMETRY.values().forEach(geometry -> geometry.vertices.close());
		GEOMETRY.clear();
		GLOW.clear();
		if (instances != null) {
			instances.close();
			instances = null;
			MemoryUtil.memFree(upload);
			upload = null;
		}
		residentBytes = 0;
		drawn = 0;
		batches = 0;
	}

	private record TexturePair(AbstractTexture base, AbstractTexture glow) {
	}

	private record MeshKey(HordeMeshCache.Mesh first, HordeMeshCache.Mesh second) {
	}

	private record GroupKey(MeshKey mesh, Identifier texture, boolean fade) {
	}

	private record Batch(GroupKey key, Geometry geometry, List<HordeRendering.Draw> draws,
	                     int first, int count, int block) {
	}

	private static class Geometry {
		private final GpuBuffer vertices;
		private final int indices;
		private long lastFrame;

		private Geometry(GpuBuffer vertices, int indices) {
			this.vertices = vertices;
			this.indices = indices;
		}
	}
}
