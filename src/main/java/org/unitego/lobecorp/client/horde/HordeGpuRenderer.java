package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
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
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

/// 常驻模型、线路和骨骼动画；每批只上传时间与编号区间，不上传逐单位变换。
public class HordeGpuRenderer {
	private static final int PARAMETER_BYTES = 160;
	private static final int VERTEX_BYTES = 28;
	private static final int INSTANCES_PER_DRAW = 65536;
	private static final VertexFormat FORMAT = VertexFormat.builder()
			.add("Position", VertexFormatElement.POSITION).add("UV0", VertexFormatElement.UV0)
			.add("Color", VertexFormatElement.COLOR).add("Normal", VertexFormatElement.NORMAL).padding(1).build();
	private static final RenderPipeline NORMAL = pipeline(false);
	private static final RenderPipeline REVERSE = pipeline(true);
	private static final Map<HordeVisualDefinition, Geometry> GEOMETRY = new HashMap<>();
	private static final Map<UUID, EventData> EVENTS = new HashMap<>();
	private static final HordeLightField LIGHT = new HordeLightField();
	private static GpuBuffer parameters;
	private static ByteBuffer upload;
	private static long drawn;
	private static int batches;
	private static long uploadedBytes;
	private static long preparations;

	private static RenderPipeline pipeline(boolean reverse) {
		return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
				.withLocation(Lobecorp.id(reverse ? "pipeline/horde_gpu_reverse" : "pipeline/horde_gpu"))
				.withVertexShader(Lobecorp.id("core/horde_instanced")).withFragmentShader(Lobecorp.id("core/horde_instanced"))
				.withSampler("Sampler0").withSampler("Sampler2").withSampler("Glowmask")
				.withSampler("HordeRoute").withSampler("HordeAnimation").withSampler("HordeRegions")
				.withSampler("HordeLight").withSampler("HordeLightIndex")
				.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(FORMAT, VertexFormat.Mode.QUADS).withCull(false)
				.withDepthStencilState(new DepthStencilState(reverse ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, true))
				.withColorTargetState(ColorTargetState.DEFAULT).build();
	}

	public static void draw(List<HordeClient.Batch> source, Matrix4fc modelView, boolean reverse) {
		try {
			drawPrepared(source, modelView, reverse);
		} catch (RuntimeException exception) {
			source.forEach(batch -> batch.event().failed = true);
			clear();
			Lobecorp.LOGGER.error("停止当前群体虚拟显示：GPU 资源或光场准备失败", exception);
		}
	}

	@SuppressWarnings("resource")
	private static void drawPrepared(List<HordeClient.Batch> source, Matrix4fc modelView, boolean reverse) {
		drawn = 0;
		batches = 0;
		uploadedBytes = 0;
		if (source.isEmpty()) {
			return;
		}
		var minecraft = Minecraft.getInstance();
		var target = minecraft.getMainRenderTarget();
		if (target.getColorTextureView() == null || target.getDepthTextureView() == null) {
			return;
		}
		var prepared = prepare(source);
		if (prepared.isEmpty()) {
			return;
		}
		LIGHT.prepare(prepared.stream().map(Prepared::batch).toList());
		int alignment = RenderSystem.getDevice().getUniformOffsetAlignment();
		int stride = Math.ceilDiv(PARAMETER_BYTES, alignment) * alignment;
		int required = Math.multiplyExact(prepared.size(), stride);
		ensureParameters(required);
		upload.clear().limit(required);
		for (int i = 0; i < prepared.size(); i++) {
			upload.position(i * stride);
			writeParameters(prepared.get(i));
		}
		upload.position(0).limit(required);
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(0, required), upload);
		uploadedBytes = required;
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1), new Vector3f(), new Matrix4f());
		var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		int maximum = prepared.stream().mapToInt(item -> item.geometry().indices).max().orElse(0);
		var indexBuffer = indices.getBuffer(maximum);
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Lobecorp GPU horde", target.getColorTextureView(), OptionalInt.empty(),
				target.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(reverse ? REVERSE : NORMAL);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), linear);
			pass.bindTexture("HordeLight", LIGHT.atlas().getTextureView(), nearest);
			pass.bindTexture("HordeLightIndex", LIGHT.lookup().getTextureView(), nearest);
			pass.setIndexBuffer(indexBuffer, indices.type());
			for (int i = 0; i < prepared.size(); i++) {
				var item = prepared.get(i);
				pass.setUniform("HordeParameters", parameters.slice((long) i * stride, PARAMETER_BYTES));
				pass.bindTexture("Sampler0", item.geometry().base.getTextureView(), item.geometry().base.getSampler());
				pass.bindTexture("Glowmask", item.geometry().glow.getTextureView(), item.geometry().glow.getSampler());
				pass.bindTexture("HordeRoute", item.event().route.texture().getTextureView(), nearest);
				pass.bindTexture("HordeRegions", item.event().regions.texture().getTextureView(), nearest);
				pass.bindTexture("HordeAnimation", item.geometry().animation.texture().getTextureView(), nearest);
				pass.setVertexBuffer(0, item.geometry().vertices);
				pass.drawIndexed(0, 0, item.geometry().indices, item.count());
				drawn += item.count();
				batches++;
			}
		}
	}

	private static List<Prepared> prepare(List<HordeClient.Batch> source) {
		var prepared = new ArrayList<Prepared>();
		for (var batch : source) {
			var event = batch.event();
			if (event.failed) {
				continue;
			}
			try {
				var geometry = GEOMETRY.computeIfAbsent(event.visual, HordeGpuRenderer::geometry);
				var data = EVENTS.computeIfAbsent(event.id, _ -> new EventData(event));
				data.updateRegions(event);
				for (int offset = 0; offset < batch.count(); ) {
					int count = Math.min(INSTANCES_PER_DRAW, batch.count() - offset);
					prepared.add(new Prepared(batch, geometry, data, offset, count));
					offset += count;
				}
			} catch (RuntimeException exception) {
				event.failed = true;
				remove(event.id);
				prepared.removeIf(item -> item.batch().event() == event);
				Lobecorp.LOGGER.error("停止群体事件虚拟显示 {}：资源准备失败", event.id, exception);
			}
		}
		return prepared;
	}

	@SuppressWarnings("resource")
	private static void writeParameters(Prepared item) {
		var batch = item.batch();
		var event = batch.event();
		var camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		var origin = event.origin.subtract(camera);
		long offset = batch.offset() + item.offset();
		long row = batch.firstRow() + offset / batch.block().columns();
		int columnOffset = (int) (offset % batch.block().columns());
		var span = item.event().spans.get(batch.block().index());
		put((float) origin.x, (float) origin.y, (float) origin.z, (float) batch.time());
		put((float) camera.x, (float) camera.y, (float) camera.z, (float) event.speed);
		put((float) event.rules.rowSpacing(), (float) event.rules.columnSpacing(), event.columns, (float) event.length);
		put((float) (batch.time() * event.speed - row * event.rules.rowSpacing()), batch.block().firstColumn(), batch.block().columns(), columnOffset);
		put((float) batch.block().first(), (float) batch.block().last(), span.offset(), span.count());
		put(item.geometry().model.bones(), item.geometry().model.frames(), item.geometry().model.duration(), item.geometry().hasGlow ? 1 : 0);
		put(LIGHT.mask(), event.nodes.length, event.rules.transitionTicks(), (float) event.rules.regionMargin());
		int sx = (int) Math.floor(event.origin.x / 16);
		int sy = (int) Math.floor(event.origin.y / 16);
		int sz = (int) Math.floor(event.origin.z / 16);
		put(sx, sy, sz, (float) (row % 1024));
		put((float) (event.rules.width() + event.rules.regionMargin() * 2),
				(float) (event.origin.x - sx * 16d), (float) (event.origin.y - sy * 16d), (float) (event.origin.z - sz * 16d));
		put(event.lightProbeHeight, 0, 0, 0);
	}

	private static void put(float a, float b, float c, float d) {
		upload.putFloat(a).putFloat(b).putFloat(c).putFloat(d);
	}

	private static Geometry geometry(HordeVisualDefinition visual) {
		var model = HordeMeshCache.get(visual);
		float[] vertices = model.vertices();
		var data = MemoryUtil.memAlloc(Math.multiplyExact(vertices.length / HordeMeshCache.VERTEX_STRIDE, VERTEX_BYTES));
		GpuBuffer buffer = null;
		HordeDataTexture animation = null;
		try {
			for (int i = 0; i < vertices.length; i += HordeMeshCache.VERTEX_STRIDE) {
				data.putFloat(vertices[i]).putFloat(vertices[i + 1]).putFloat(vertices[i + 2]);
				data.putFloat(vertices[i + 3]).putFloat(vertices[i + 4]);
				int bone = (int) vertices[i + 8];
				data.put((byte) bone).put((byte) (bone >>> 8)).put((byte) (bone >>> 16)).put((byte) 255);
				for (int normal = 5; normal < 8; normal++) {
					data.put((byte) Math.round(Math.clamp(vertices[i + normal], -1, 1) * 127));
				}
				data.put((byte) 0);
			}
			data.flip();
			buffer = RenderSystem.getDevice().createBuffer(() -> "Horde static model", GpuBuffer.USAGE_VERTEX, data);
			animation = new HordeDataTexture("Horde shared skeletal cycle", model.animation());
			var minecraft = Minecraft.getInstance();
			var base = minecraft.getTextureManager().getTexture(visual.texture());
			Identifier glowId = visual.texture().withPath(path -> path.replace(".png", "_glowmask.png"));
			boolean hasGlow = minecraft.getResourceManager().getResource(glowId).isPresent();
			var glow = hasGlow ? minecraft.getTextureManager().getTexture(glowId) : base;
			preparations++;
			return new Geometry(buffer, vertices.length / HordeMeshCache.VERTEX_STRIDE / 4 * 6, animation, model, base, glow, hasGlow);
		} catch (RuntimeException exception) {
			if (buffer != null) {
				buffer.close();
			}
			if (animation != null) {
				animation.close();
			}
			throw exception;
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	private static void ensureParameters(int required) {
		if (parameters != null && parameters.size() >= required) {
			return;
		}
		int capacity = Math.multiplyExact(Integer.highestOneBit(Math.max(1, required - 1)), 2);
		var replacement = RenderSystem.getDevice().createBuffer(() -> "Horde batch parameters",
				GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, capacity);
		var memory = MemoryUtil.memAlloc(capacity);
		if (parameters != null) {
			parameters.close();
			MemoryUtil.memFree(upload);
		}
		parameters = replacement;
		upload = memory;
	}

	public static void remove(UUID id) {
		var data = EVENTS.remove(id);
		if (data != null) {
			data.close();
		}
	}

	public static void clear() {
		EVENTS.values().forEach(EventData::close);
		EVENTS.clear();
		GEOMETRY.values().forEach(geometry -> {
			geometry.vertices.close();
			geometry.animation.close();
		});
		GEOMETRY.clear();
		LIGHT.close();
		if (parameters != null) {
			parameters.close();
			parameters = null;
			MemoryUtil.memFree(upload);
			upload = null;
		}
		drawn = 0;
		batches = 0;
	}

	public static String statistics() {
		return "GPU instances=" + drawn + ", batches=" + batches + ", upload=" + uploadedBytes
				+ "B, lightSections=" + LIGHT.sections() + ", modelBuilds=" + preparations + ", shadows=0";
	}

	private record Geometry(GpuBuffer vertices, int indices, HordeDataTexture animation, HordeMeshCache.Model model,
	                        AbstractTexture base, AbstractTexture glow, boolean hasGlow) {
	}

	private record Prepared(HordeClient.Batch batch, Geometry geometry, EventData event, int offset, int count) {
	}

	private record Span(int offset, int count) {
	}

	private static class EventData implements AutoCloseable {
		private final HordeDataTexture route;
		private HordeDataTexture regions;
		private long version = Long.MIN_VALUE;
		private final Map<Integer, Span> spans = new HashMap<>();

		private EventData(HordeClient.ClientEvent event) {
			route = new HordeDataTexture("Horde fixed route " + event.id, event.routeData);
		}

		private void updateRegions(HordeClient.ClientEvent event) {
			if (version == event.regionVersion) {
				return;
			}
			var entries = new ArrayList<>(event.regions.values());
			var values = new ArrayList<Float>();
			for (var region : entries) {
				var from = region.from.subtract(event.origin);
				var to = region.to.subtract(event.origin);
				for (float value : new float[]{(float) from.x, (float) from.y, (float) from.z, (float) region.changed,
						(float) to.x, (float) to.y, (float) to.z, 0,
						(float) region.fromX, (float) region.fromZ, (float) region.toX, (float) region.toZ}) {
					values.add(value);
				}
			}
			for (var block : event.blocks) {
				int offset = values.size();
				int count = 0;
				for (int i = 0; i < entries.size(); i++) {
					var region = entries.get(i);
					double radiusX = Math.max(region.fromX, region.toX) * 2;
					double radiusZ = Math.max(region.fromZ, region.toZ) * 2;
					if (Math.min(region.from.x, region.to.x) - radiusX <= block.bounds().maxX
							&& Math.max(region.from.x, region.to.x) + radiusX >= block.bounds().minX
							&& Math.min(region.from.z, region.to.z) - radiusZ <= block.bounds().maxZ
							&& Math.max(region.from.z, region.to.z) + radiusZ >= block.bounds().minZ) {
						values.add((float) i);
						count++;
					}
				}
				spans.put(block.index(), new Span(offset, count));
			}
			var data = new float[Math.max(1, values.size())];
			for (int i = 0; i < values.size(); i++) {
				data[i] = values.get(i);
			}
			var replacement = new HordeDataTexture("Horde region targets", data);
			if (regions != null) {
				regions.close();
			}
			regions = replacement;
			version = event.regionVersion;
		}

		@Override
		public void close() {
			route.close();
			if (regions != null) {
				regions.close();
			}
		}
	}
}
