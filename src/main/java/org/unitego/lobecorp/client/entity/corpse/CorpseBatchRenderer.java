package org.unitego.lobecorp.client.entity.corpse;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.entity.ordeal.indigo.model.SweeperModel;
import org.unitego.lobecorp.client.entity.renderer.EntityCorpseRenderer;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.config.client.ClientConfig;
import org.unitego.lobecorp.client.horde.HordeDataTexture;
import org.unitego.lobecorp.client.horde.HordeIrisAnimation;
import org.unitego.lobecorp.client.horde.HordeIrisShadows;
import org.unitego.lobecorp.client.horde.HordeMeshCache;
import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;
import org.unitego.lobecorp.registry.entity.LcEntityTypes;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/// 已冻结网格按材质归组；CPU仅提交真实尸体的实例数据，逐顶点变换和Iris展开由GPU执行。
public class CorpseBatchRenderer {
	private static final int INSTANCE_WORDS = 8;
	private static final int PARAMETER_BYTES = 32;
	private static final int VERTEX_BYTES = 28;
	private static final int WORKSET_WORDS = 4 * 1024 * 1024;
	private static final int MAX_INSTANCES = 65536;
	private static final long RETRY_NANOS = 5_000_000_000L;
	private static final VertexFormat FORMAT = VertexFormat.builder().add("Position", VertexFormatElement.POSITION)
			.add("UV0", VertexFormatElement.UV0).add("Color", VertexFormatElement.COLOR)
			.add("Normal", VertexFormatElement.NORMAL).padding(1).build();
	private static final RenderPipeline NORMAL = pipeline(false);
	private static final RenderPipeline REVERSE = pipeline(true);
	private static final RenderPipeline EXPAND = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/corpse_iris_vertices"))
			.withVertexShader(Lobecorp.id("core/horde_motion"))
			.withFragmentShader(Lobecorp.id("core/corpse_iris_vertices"))
			.withSampler("CorpseInstances").withSampler("CorpseFrames").withSampler("CorpseUvs")
			.withUniform("CorpseParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false)
			.withColorTargetState(ColorTargetState.DEFAULT).build();
	private static final List<Instance> PENDING = new ArrayList<>();
	private static final Map<HordeMeshCache.Model, Mesh> MESHES = new HashMap<>();
	private static Object generation;
	private static HordeDataTexture instances;
	private static int[] words = new int[0];
	private static GpuBuffer parameters;
	private static ByteBuffer upload;
	private static int stride;
	private static GpuTexture expanded;
	private static GpuTextureView expandedView;
	private static GpuBuffer expandedVertices;
	private static int expandedWidth;
	private static int expandedHeight;
	private static long frame = Long.MIN_VALUE;
	private static long retryAt;
	private static long irisRetryAt;
	private static long failures;
	private static long cacheBuilds;
	private static long cacheHits;
	private static int staticSubmitted;
	private static int staticDrawn;
	private static int fallback;
	private static int shadowFallback;
	private static int draws;
	private static String path = "idle";

	public record Instance(HordeMeshCache.Model model, float x, float y, float z, float yawRadians,
	                       int light, int color, float range, float scale) {
		private double distanceSquared() {
			return x * x + y * y + z * z;
		}
	}

	private record Batch(Mesh mesh, int first, int count) {
	}

	private static RenderPipeline pipeline(boolean reverse) {
		return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
				.withLocation(Lobecorp.id(reverse ? "pipeline/corpse_instanced_reverse" : "pipeline/corpse_instanced"))
				.withVertexShader(Lobecorp.id("core/corpse_instanced"))
				.withFragmentShader(Lobecorp.id("core/corpse_instanced"))
				.withSampler("Sampler0").withSampler("Sampler2").withSampler("Glowmask").withSampler("CorpseInstances")
				.withUniform("CorpseParameters", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(FORMAT, VertexFormat.Mode.QUADS).withCull(false)
				.withDepthStencilState(new DepthStencilState(
						reverse ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, true))
				.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
	}

	private static void beginFrame() {
		long current = PhotonFrameCapture.renderFrameId();
		if (frame != current) {
			frame = current;
			PENDING.clear();
			staticSubmitted = staticDrawn = fallback = shadowFallback = draws = 0;
			path = "idle";
		}
	}

	public static boolean available() {
		return ClientConfig.CORPSE_BATCH.get() && PhotonFrameCapture.renderFrameOpen() && !HordeIrisShadows.shadowPass()
				&& (retryAt == 0 || System.nanoTime() - retryAt >= 0);
	}

	public static boolean submit(Instance instance) {
		beginFrame();
		if (!available()) {
			return false;
		}
		PENDING.add(instance);
		staticSubmitted++;
		return true;
	}

	public static void fallback() {
		beginFrame();
		if (HordeIrisShadows.shadowPass()) {
			shadowFallback++;
		} else {
			fallback++;
		}
	}

	public static void cacheBuilt() {
		cacheBuilds++;
	}

	public static void cacheHit() {
		cacheHits++;
	}

	public static long failures() {
		return failures;
	}

	public static JsonObject snapshot() {
		var result = new JsonObject();
		result.addProperty("render_frame_id", frame);
		result.addProperty("static_submitted", staticSubmitted);
		result.addProperty("static_drawn", staticDrawn);
		result.addProperty("fallback", fallback);
		result.addProperty("shadow_fallback", shadowFallback);
		result.addProperty("draws", draws);
		result.addProperty("path", path);
		result.addProperty("cache_builds", cacheBuilds);
		result.addProperty("cache_hits", cacheHits);
		result.addProperty("gpu_meshes", MESHES.size());
		result.addProperty("gpu_failures", failures);
		return result;
	}

	public static void draw(Matrix4fc modelView, boolean reverse) {
		beginFrame();
		try {
			Object current = FrozenCorpseMeshProvider.generation();
			EntityCorpseRenderer.invalidateResourceEpoch(current);
			if (generation != current) {
				clearMeshes();
				generation = current;
			}
			if (PENDING.isEmpty()) {
				return;
			}
			renderBatches(prepareBatches(), modelView, reverse);
		} catch (RuntimeException | OutOfMemoryError failure) {
			failures++;
			retryAt = System.nanoTime() + RETRY_NANOS;
			path = "legacy_retry";
			Lobecorp.LOGGER.error("尸体冻结批渲染失败，下一帧保留原渲染路径", failure);
		} finally {
			PENDING.clear();
		}
	}

	private record PreparedFrame(List<Batch> batches, boolean iris, int format) {
	}

	private static PreparedFrame prepareBatches() {
		var grouped = new LinkedHashMap<HordeMeshCache.Model, List<Instance>>();
		for (var instance : PENDING) {
			grouped.computeIfAbsent(instance.model(), _ -> new ArrayList<>()).add(instance);
		}
		boolean iris = HordeIrisShadows.active() && HordeIrisShadows.entityVertexWords() > 0
				&& RenderPipelines.ENTITY_TRANSLUCENT.getVertexFormat() == RenderPipelines.ENTITY_CUTOUT.getVertexFormat()
				&& (irisRetryAt == 0 || System.nanoTime() - irisRetryAt >= 0);
		int format = iris ? HordeIrisShadows.entityVertexWords() : 9;
		int rowWidth = Integer.highestOneBit(Math.min(4096, RenderSystem.getDevice().getMaxTextureSize()));
		long capacity = Math.min(WORKSET_WORDS, (long) rowWidth * RenderSystem.getDevice().getMaxTextureSize());
		for (var entry : grouped.entrySet()) {
			var mesh = MESHES.computeIfAbsent(entry.getKey(), Mesh::new);
			if (iris) {
				try {
					if ((long) mesh.model.vertexCount() * format > capacity) {
						throw new IllegalArgumentException("尸体网格超过Iris工作集");
					}
					mesh.prepareIris();
				} catch (RuntimeException | OutOfMemoryError failure) {
					iris = false;
					irisRetryAt = System.nanoTime() + RETRY_NANOS;
					Lobecorp.LOGGER.warn("尸体Iris冻结网格暂不可用，保留实例化路径", failure);
				}
			}
		}
		var batches = new ArrayList<Batch>();
		int total = PENDING.size();
		ensureInstances(Math.multiplyExact(total, INSTANCE_WORDS));
		int index = 0;
		for (var entry : grouped.entrySet()) {
			var mesh = MESHES.get(entry.getKey());
			var list = entry.getValue();
			list.sort(Comparator.comparingDouble(Instance::distanceSquared).reversed());
			int first = index;
			for (var instance : list) {
				int at = index++ * INSTANCE_WORDS;
				words[at] = Float.floatToRawIntBits(instance.x());
				words[at + 1] = Float.floatToRawIntBits(instance.y());
				words[at + 2] = Float.floatToRawIntBits(instance.z());
				words[at + 3] = Float.floatToRawIntBits(instance.yawRadians());
				words[at + 4] = instance.light();
				words[at + 5] = instance.color();
				words[at + 6] = Float.floatToRawIntBits(instance.range());
				words[at + 7] = Float.floatToRawIntBits(instance.scale());
			}
			int limit = iris ? (int) Math.min(MAX_INSTANCES, capacity / ((long) mesh.model.vertexCount() * format)) : MAX_INSTANCES;
			for (int offset = 0; offset < list.size(); offset += limit) {
				batches.add(new Batch(mesh, first + offset, Math.min(limit, list.size() - offset)));
			}
		}
		instances.update(0, words, 0, total * INSTANCE_WORDS);
		ensureParameters(batches.size());
		int maximumVertices = batches.stream().mapToInt(batch -> Math.multiplyExact(batch.mesh().model.vertexCount(), batch.count())).max().orElse(0);
		if (iris) {
			try {
				prepareExpanded(maximumVertices, format, rowWidth);
			} catch (RuntimeException | OutOfMemoryError failure) {
				iris = false;
				irisRetryAt = System.nanoTime() + RETRY_NANOS;
				Lobecorp.LOGGER.warn("尸体Iris输出缓冲暂不可用，回退实例化", failure);
			}
		}
		upload.clear();
		for (int i = 0; i < batches.size(); i++) {
			var batch = batches.get(i);
			upload.position(i * stride);
			upload.putInt(batch.first()).putInt(batch.mesh().hasGlow ? 1 : 0).putInt(batch.count()).putInt(expandedWidth);
			upload.putInt(batch.mesh().model.vertexCount()).putInt(format)
					.putInt(iris ? HordeIrisShadows.entityId(LcEntityTypes.ENTITY_CORPSE.get()) : 0).putInt(0);
		}
		int parameterBytes = batches.size() * stride;
		upload.position(0).limit(parameterBytes);
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(0, parameterBytes), upload);
		return new PreparedFrame(batches, iris, format);
	}

	private static void renderBatches(PreparedFrame frame, Matrix4fc modelView, boolean reverse) {
		var batches = frame.batches();
		boolean iris = frame.iris();
		int format = frame.format();
		var minecraft = Minecraft.getInstance();
		var target = minecraft.getMainRenderTarget();
		var color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
		var depth = RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
		if (color == null || depth == null) {
			throw new IllegalStateException("尸体渲染目标不可用");
		}
		var transform = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1), new Vector3f(), new Matrix4f());
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		path = iris ? "iris_frozen_batch" : HordeIrisShadows.active() ? "native_frozen_fallback" : "native_frozen_batch";
		for (int i = 0; i < batches.size(); i++) {
			var batch = batches.get(i);
			var mesh = batch.mesh();
			int vertices = Math.multiplyExact(mesh.model.vertexCount(), batch.count());
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			if (iris) {
				try (var pass = encoder.createRenderPass(() -> "Corpse frozen Iris expansion", expandedView, OptionalInt.empty())) {
					pass.setPipeline(EXPAND);
					pass.setViewport(0, 0, expandedWidth, expandedHeight);
					pass.setUniform("CorpseParameters", parameters.slice((long) i * stride, PARAMETER_BYTES));
					pass.bindTexture("CorpseInstances", instances.texture().getTextureView(), nearest);
					pass.bindTexture("CorpseFrames", mesh.frames.view(), nearest);
					pass.bindTexture("CorpseUvs", mesh.uvs.texture().getTextureView(), nearest);
					pass.draw(0, 3);
				}
				int rows = Math.ceilDiv(Math.multiplyExact(vertices, format), expandedWidth);
				encoder.copyTextureToBuffer(expanded, expandedVertices, 0, () -> {
				}, 0, 0, 0, expandedWidth, rows);
			}
			int indexCount = iris ? vertices / 4 * 6 : mesh.model.vertexCount() / 4 * 6;
			var indexBuffer = indices.getBuffer(indexCount);
			for (int layer = 0; layer < (iris && mesh.hasGlow ? 2 : 1); layer++) {
				try (var pass = encoder.createRenderPass(() -> "Corpse frozen material batch", color,
						OptionalInt.empty(), depth, OptionalDouble.empty())) {
					pass.setPipeline(iris ? layer == 0 ? RenderPipelines.ENTITY_TRANSLUCENT : RenderPipelines.EYES
							: reverse ? REVERSE : NORMAL);
					RenderSystem.bindDefaultUniforms(pass);
					pass.setUniform("DynamicTransforms", transform);
					pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), linear);
					pass.bindTexture("Sampler0", (layer == 0 ? mesh.base : mesh.glow).getTextureView(), nearest);
					if (iris) {
						if (layer == 0) {
							pass.bindTexture("Sampler1", minecraft.gameRenderer.overlayTexture().getTextureView(), nearest);
						}
						pass.setVertexBuffer(0, expandedVertices);
					} else {
						pass.setUniform("CorpseParameters", parameters.slice((long) i * stride, PARAMETER_BYTES));
						pass.bindTexture("CorpseInstances", instances.texture().getTextureView(), nearest);
						pass.bindTexture("Glowmask", mesh.glow.getTextureView(), nearest);
						pass.setVertexBuffer(0, mesh.vertices);
					}
					pass.setIndexBuffer(indexBuffer, indices.type());
					pass.drawIndexed(0, 0, indexCount, iris ? 1 : batch.count());
					draws++;
				}
			}
			staticDrawn += batch.count();
		}
	}

	private static void ensureInstances(int required) {
		if (words.length >= required && instances != null) {
			return;
		}
		int size = Integer.highestOneBit(Math.max(1, required - 1)) * 2;
		var replacement = new HordeDataTexture("Corpse instance metadata", new int[size]);
		if (instances != null) {
			instances.close();
		}
		instances = replacement;
		words = new int[size];
	}

	private static void ensureParameters(int count) {
		int alignment = RenderSystem.getDevice().getUniformOffsetAlignment();
		stride = Math.ceilDiv(PARAMETER_BYTES, alignment) * alignment;
		int bytes = Math.multiplyExact(count, stride);
		if (parameters != null && parameters.size() >= bytes) {
			return;
		}
		var memory = MemoryUtil.memAlloc(bytes);
		GpuBuffer replacement;
		try {
			replacement = RenderSystem.getDevice().createBuffer(() -> "Corpse batch parameters",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, bytes);
		} catch (RuntimeException | OutOfMemoryError failure) {
			MemoryUtil.memFree(memory);
			throw failure;
		}
		if (parameters != null) {
			parameters.close();
		}
		if (upload != null) {
			MemoryUtil.memFree(upload);
		}
		parameters = replacement;
		upload = memory;
	}

	private static void prepareExpanded(int vertices, int format, int rowWidth) {
		int rows = Math.ceilDiv(Math.multiplyExact(vertices, format), rowWidth);
		if (expanded != null && expandedWidth == rowWidth && expandedHeight >= rows) {
			return;
		}
		releaseExpanded();
		int maxRows = Math.min(RenderSystem.getDevice().getMaxTextureSize(), WORKSET_WORDS / rowWidth);
		int targetHeight = Math.min(maxRows, Integer.highestOneBit(Math.max(1, rows - 1)) * 2);
		if (rows > targetHeight) {
			throw new IllegalArgumentException("尸体Iris工作集不足");
		}
		try {
			expanded = RenderSystem.getDevice().createTexture(() -> "Corpse frozen expanded words",
					GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
					TextureFormat.RGBA8, rowWidth, targetHeight, 1, 1);
			expandedView = RenderSystem.getDevice().createTextureView(expanded);
			expandedVertices = RenderSystem.getDevice().createBuffer(() -> "Corpse frozen expanded vertices",
					GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, (long) rowWidth * targetHeight * 4);
			expandedWidth = rowWidth;
			expandedHeight = targetHeight;
		} catch (RuntimeException | OutOfMemoryError failure) {
			releaseExpanded();
			throw failure;
		}
	}

	private static void releaseExpanded() {
		if (expandedView != null) {
			expandedView.close();
		}
		if (expanded != null) {
			expanded.close();
		}
		if (expandedVertices != null) {
			expandedVertices.close();
		}
		expandedView = null;
		expanded = null;
		expandedVertices = null;
		expandedWidth = expandedHeight = 0;
	}

	private static void clearMeshes() {
		MESHES.values().forEach(Mesh::close);
		MESHES.clear();
	}

	public static void clear() {
		PENDING.clear();
		clearMeshes();
		releaseExpanded();
		if (instances != null) {
			instances.close();
		}
		if (parameters != null) {
			parameters.close();
		}
		if (upload != null) {
			MemoryUtil.memFree(upload);
		}
		instances = null;
		parameters = null;
		upload = null;
		words = new int[0];
		frame = Long.MIN_VALUE;
		generation = null;
		retryAt = irisRetryAt = 0;
		FrozenCorpseMeshProvider.clear();
		EntityCorpseRenderer.clearFrozen();
	}

	private static class Mesh implements AutoCloseable {
		private final HordeMeshCache.Model model;
		private final GpuBuffer vertices;
		private final AbstractTexture base;
		private final AbstractTexture glow;
		private final boolean hasGlow;
		private HordeDataTexture animation;
		private HordeDataTexture uvs;
		private HordeIrisAnimation frames;

		private Mesh(HordeMeshCache.Model model) {
			this.model = model;
			var minecraft = Minecraft.getInstance();
			base = minecraft.getTextureManager().getTexture(SweeperModel.TEXTURES_CORPSE);
			var glowId = SweeperModel.TEXTURES_CORPSE.withPath(path -> path.replace(".png", "_glowmask.png"));
			hasGlow = minecraft.getResourceManager().getResource(glowId).isPresent();
			glow = hasGlow ? minecraft.getTextureManager().getTexture(glowId) : base;
			var memory = MemoryUtil.memAlloc(Math.multiplyExact(model.vertexCount(), VERTEX_BYTES));
			try {
				for (int vertex = 0; vertex < model.vertexCount(); vertex++) {
					int at = vertex * 6, uv = vertex * HordeMeshCache.VERTEX_STRIDE;
					memory.putFloat(model.frameVertices()[at]).putFloat(model.frameVertices()[at + 1]).putFloat(model.frameVertices()[at + 2]);
					memory.putFloat(model.vertices()[uv + 3]).putFloat(model.vertices()[uv + 4]);
					memory.putInt(-1);
					for (int axis = 3; axis < 6; axis++) {
						memory.put((byte) Math.round(Math.clamp(model.frameVertices()[at + axis], -1, 1) * 127));
					}
					memory.put((byte) 0);
				}
				memory.flip();
				vertices = RenderSystem.getDevice().createBuffer(() -> "Corpse shared frozen mesh", GpuBuffer.USAGE_VERTEX, memory);
			} finally {
				MemoryUtil.memFree(memory);
			}
		}

		private void prepareIris() {
			if (frames != null) {
				return;
			}
			try {
				animation = new HordeDataTexture("Corpse frozen frame positions", model.frameVertices());
				float[] data = new float[model.vertexCount() * 4];
				for (int vertex = 0; vertex < model.vertexCount(); vertex++) {
					int uv = vertex * HordeMeshCache.VERTEX_STRIDE;
					data[vertex * 4] = model.vertices()[uv + 3];
					data[vertex * 4 + 1] = model.vertices()[uv + 4];
					int quad = vertex / 4 * 4 * HordeMeshCache.VERTEX_STRIDE;
					for (int corner = 0; corner < 4; corner++) {
						data[vertex * 4 + 2] += model.vertices()[quad + corner * HordeMeshCache.VERTEX_STRIDE + 3] * 0.25f;
						data[vertex * 4 + 3] += model.vertices()[quad + corner * HordeMeshCache.VERTEX_STRIDE + 4] * 0.25f;
					}
				}
				uvs = new HordeDataTexture("Corpse frozen frame UVs", data);
				frames = new HordeIrisAnimation(model, animation, uvs);
			} catch (RuntimeException | OutOfMemoryError failure) {
				if (frames != null) {
					frames.close();
				}
				if (animation != null) {
					animation.close();
				}
				if (uvs != null) {
					uvs.close();
				}
				frames = null;
				animation = null;
				uvs = null;
				throw failure;
			}
		}

		@Override
		public void close() {
			vertices.close();
			if (frames != null) {
				frames.close();
			}
			if (animation != null) {
				animation.close();
			}
			if (uvs != null) {
				uvs.close();
			}
		}
	}
}
