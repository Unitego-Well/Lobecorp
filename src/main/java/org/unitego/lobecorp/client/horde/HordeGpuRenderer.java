package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.HashSet;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.util.Comparator;

import org.unitego.lobecorp.client.photon.performance.PhotonFrameCapture;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

/// 常驻模型、线路和骨骼动画；每批只上传时间与编号区间，不上传逐单位变换。
public class HordeGpuRenderer {
	// std140共16个vec4，与horde_motion.glsl的HordeParameters一致。
	private static final int PARAMETER_BYTES = 256;
    private static final int VERTEX_BYTES = 28;
    private static final int INSTANCES_PER_DRAW = 65536;
	// 位置、朝向角、光照、sin/cos和帧顶点基址，与GLSL布局一致。
	private static final int MOTION_VALUES = 8;
	// 分割让步的几何系数：基础带宽、推出强度、提前带宽度与单侧最大偏移（格）。
	private static final double SPLIT_BASE_BAND = 3.2;
	private static final float SPLIT_STRENGTH = 1.0f;
	private static final float SPLIT_LEAD = 2.5f;
	private static final float SPLIT_MAXIMUM_OFFSET = 16.0f;
	private static final int MAX_RETRY_SECONDS = 5;
	private static int resourceFailures;
	private static long retryAtNanos;
    private static final int MOTION_ROW_WIDTH = 1024;
    private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION).add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR).add("Normal", VertexFormatElement.NORMAL).padding(1).build();
    private static final RenderPipeline NORMAL = pipeline(false, false);
    private static final RenderPipeline REVERSE = pipeline(true, false);
    // 片档是共中心的六向投影片：只提交朝观察者的方向片，透明裁剪并保持背面剔除。
    private static final RenderPipeline CARD_NORMAL = cardPipeline(false);
    private static final RenderPipeline CARD_REVERSE = cardPipeline(true);
	private static final RenderPipeline MOTION = RenderPipeline.builder()
            .withLocation(Lobecorp.id("pipeline/horde_motion"))
            .withVertexShader(Lobecorp.id("core/horde_motion")).withFragmentShader(Lobecorp.id("core/horde_motion"))
		    .withSampler("HordeRoute").withSampler("HordeHeights").withSampler("HordeLight")
		    .withSampler("HordeLightIndex").withSampler("HordeTerrain").withSampler("HordeSplit")
			.withSampler("HordeAvoidance")
            .withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false)
            .withColorTargetState(ColorTargetState.DEFAULT).build();
	private static final Map<GeometryKey, Geometry> GEOMETRY = new HashMap<>();
    private static final Map<UUID, EventData> EVENTS = new HashMap<>();
	private static final Map<GeometryKey, HordeIrisAnimation> IRIS_ANIMATIONS = new HashMap<>();
    private static final HordeLightField LIGHT = new HordeLightField();
	private static final IrisVertices IRIS = new IrisVertices();
    private static GpuTexture motion;
    private static GpuTextureView motionView;
    private static int motionWidth;
    private static int motionHeight;
    private static GpuBuffer parameters;
	private static ByteBuffer upload;
	private static long drawn;
	private static int batches;
	private static long uploadedBytes;
	private static long gpuCopiedBytes;
	private static long preparations;
	private static final long PREPARATION_BUDGET_NS = 3_000_000L;
	private static final Set<GeometryKey> PENDING_VISUALS = new LinkedHashSet<>();
	// 0=MOTION，1=Iris顶点烘焙，2=地形，3=Iris动画，4/5=原生模型，6/7=片，8=避让状态。
	private static final int PIPELINE_COUNT = 9;
	private static final int[] NATIVE_PIPELINE_ORDER = {2, 3, 1, 8, 0, 4, 5, 6, 7};
	private static final int[] IRIS_PIPELINE_ORDER = {1, 3, 2, 8, 0, 4, 5, 6, 7};
	private static final boolean[] COMPILED_PIPELINES = new boolean[PIPELINE_COUNT];
	private static final int[] PIPELINE_FAILURES = new int[PIPELINE_COUNT];
	private static final long[] PIPELINE_RETRY_AT = new long[PIPELINE_COUNT];
	private static final Map<GeometryKey, PreparationRetry> VISUAL_RETRIES = new HashMap<>();
	private static int nextPipeline;
	private static boolean pipelineIrisMode;
	private static int pipelineGeneration;
	private static long drawSerial;
	private static final Map<HordeVisualDefinition, GeometryKey[]> MODEL_KEYS = new HashMap<>();
	private static final Map<UUID, Map<Integer, Integer>> LOD_STATES = new HashMap<>();

	private static RenderPipeline pipeline(boolean reverse, boolean cull) {
		String location = reverse ? "pipeline/horde_gpu_reverse" : "pipeline/horde_gpu";
		return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
				.withLocation(Lobecorp.id(cull ? location + "_cull" : location))
				.withVertexShader(Lobecorp.id("core/horde_instanced")).withFragmentShader(Lobecorp.id("core/horde_instanced"))
				.withSampler("Sampler0").withSampler("Sampler2").withSampler("Glowmask")
				.withSampler("HordeMotion").withSampler("HordeAnimation")
				.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(FORMAT, VertexFormat.Mode.QUADS).withCull(cull)
				.withDepthStencilState(new DepthStencilState(reverse ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, true))
				.withColorTargetState(ColorTargetState.DEFAULT).build();
	}

	/// 片档专用：透明裁剪（cutout）+ 背面剔除；按不透明队列绘制，不做半透明混合。
	private static RenderPipeline cardPipeline(boolean reverse) {
		return pipeline(reverse, true);
	}

	private static boolean irisPath() {
		return HordeIrisShadows.active() && HordeIrisShadows.entityVertexWords() > 0 && IRIS.available();
	}

	private static GeometryKey meshKey(HordeVisualDefinition visual, int tier) {
		int settings = HordeMeshCache.settingsKey();
		var keys = MODEL_KEYS.get(visual);
		if (keys == null || keys[0].settings() != settings) {
			keys = new GeometryKey[]{new GeometryKey(visual, 0, settings),
					new GeometryKey(visual, 1, settings), new GeometryKey(visual, 2, settings),
					new GeometryKey(visual, 3, settings)};
			MODEL_KEYS.put(visual, keys);
		}
		return keys[tier];
	}

	private static float visualRadius(HordeVisualDefinition visual) {
		float radius = 0;
		for (int tier = 0; tier < 4; tier++) {
			var model = GEOMETRY.get(meshKey(visual, tier));
			if (model != null) {
				radius = Math.max(radius, model.radius());
			}
		}
		return radius;
	}

	public static void requestResources(HordeVisualDefinition visual) {
		HordeMeshCache.request(visual);
		int[] tiers = HordeVisualConfig.LOD_ENABLED.get() && HordeVisualConfig.CARD_DISTANCE.get() >= 0
				? new int[]{0, 3} : new int[]{0};
		for (int tier : tiers) {
			var key = meshKey(visual, tier);
			if (!GEOMETRY.containsKey(key) || irisPath() && !IRIS_ANIMATIONS.containsKey(key)) {
				PENDING_VISUALS.add(key);
			}
		}
	}

	private static int selectLod(HordeClient.Batch batch) {
		if (!HordeVisualConfig.LOD_ENABLED.get()) {
			return 0;
		}
		int cardDistance = HordeVisualConfig.CARD_DISTANCE.get();
		if (cardDistance < 0) {
			// 不使用片：只保留完整模型。
			return 0;
		}
		double band = Math.max(1, cardDistance * 0.1);
		double distance = batch.block().center().distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().position());
		var states = LOD_STATES.computeIfAbsent(batch.event().id, _ -> new HashMap<>());
		int previous = states.getOrDefault(batch.block().index(), distance >= cardDistance ? 3 : 0);
		int tier = previous == 3
				? (distance < cardDistance - band ? 0 : 3)
				: (distance >= cardDistance ? 3 : 0);
		states.put(batch.block().index(), tier);
		return tier;
	}

	/// 每帧在绘制前推进；驱动编译和单次上传不可抢占，预算仅约束步骤之间的调度。
	public static void prepareResources() {
		long started = System.nanoTime();
		preparePipeline(started);
		prepareVisuals(started);
		HordeMeshCache.advance(PREPARATION_BUDGET_NS - (System.nanoTime() - started));
	}

	private static void preparePipeline(long started) {
		boolean iris = HordeIrisShadows.active();
		if (pipelineIrisMode != iris || pipelineGeneration != HordeIrisShadows.shaderGeneration()) {
			resetPipelineWarmup(iris);
		}

		var order = iris ? IRIS_PIPELINE_ORDER : NATIVE_PIPELINE_ORDER;
		for (int checked = 0; checked < PIPELINE_COUNT; checked++) {
			int index = order[nextPipeline];
			nextPipeline = (nextPipeline + 1) % PIPELINE_COUNT;
			if (COMPILED_PIPELINES[index] || System.nanoTime() < PIPELINE_RETRY_AT[index]) {
				continue;
			}

			if (System.nanoTime() - started >= PREPARATION_BUDGET_NS) {
				return;
			}

			try {
				var device = RenderSystem.getDevice();
				boolean valid = switch (index) {
					case 0 -> device.precompilePipeline(MOTION).isValid();
					case 1 -> device.precompilePipeline(IrisVertices.BAKE).isValid();
					case 2 -> HordeTerrainState.precompile();
					case 3 -> HordeIrisAnimation.precompile();
					case 4 -> device.precompilePipeline(NORMAL).isValid();
					case 5 -> device.precompilePipeline(REVERSE).isValid();
					case 6 -> device.precompilePipeline(CARD_NORMAL).isValid();
					case 7 -> device.precompilePipeline(CARD_REVERSE).isValid();
					case 8 -> HordeAvoidanceState.precompile();
					default -> throw new IllegalStateException("群体管线预热步骤无效");
				};
				if (!valid) {
					throw new IllegalStateException("群体管线预编译结果无效");
				}

				COMPILED_PIPELINES[index] = true;
				PIPELINE_FAILURES[index] = 0;
				PIPELINE_RETRY_AT[index] = 0;
			} catch (RuntimeException failure) {
				int attempts = Math.min(PIPELINE_FAILURES[index] + 1, MAX_RETRY_SECONDS);
				PIPELINE_FAILURES[index] = attempts;
				PIPELINE_RETRY_AT[index] = System.nanoTime() + attempts * 1_000_000_000L;
				Lobecorp.LOGGER.error("群体管线预热步骤 {} 失败，保留其他资源并延后重试", index, failure);
			}

			return;
		}
	}

	private static void resetPipelineWarmup(boolean iris) {
		Arrays.fill(COMPILED_PIPELINES, false);
		Arrays.fill(PIPELINE_FAILURES, 0);
		Arrays.fill(PIPELINE_RETRY_AT, 0);
		nextPipeline = 0;
		pipelineIrisMode = iris;
		pipelineGeneration = HordeIrisShadows.shaderGeneration();
	}

	private static void prepareVisuals(long started) {
		var iterator = PENDING_VISUALS.iterator();
		while (iterator.hasNext() && System.nanoTime() - started < PREPARATION_BUDGET_NS) {
			var key = iterator.next();
			var visual = key.visual();
			var retry = VISUAL_RETRIES.get(key);
			if (retry != null && System.nanoTime() < retry.atNanos()) {
				continue;
			}

			HordeMeshCache.Model model;
			try {
				model = HordeMeshCache.ready(visual, key.tier());
			} catch (RuntimeException _) {
				iterator.remove();
				VISUAL_RETRIES.remove(key);
				continue;
			}

			if (model == null) {
				continue;
			}

			try {
				var geometry = GEOMETRY.get(key);
				if (geometry == null) {
					geometry = geometry(visual, model);
					GEOMETRY.put(key, geometry);
					if (System.nanoTime() - started >= PREPARATION_BUDGET_NS) {
						continue;
					}
				}

				if (irisPath() && !IRIS_ANIMATIONS.containsKey(key)) {
					try {
						IRIS_ANIMATIONS.put(key, new HordeIrisAnimation(geometry.model(), geometry.animation(), geometry.uvs()));
					} catch (RuntimeException | OutOfMemoryError failure) {
						IRIS.disable(failure);
						throw failure;
					}
				}

				iterator.remove();
				VISUAL_RETRIES.remove(key);
			} catch (RuntimeException | OutOfMemoryError failure) {
				int attempts = retry == null ? 1 : Math.min(retry.failures() + 1, MAX_RETRY_SECONDS);
				VISUAL_RETRIES.put(key, new PreparationRetry(attempts, System.nanoTime() + attempts * 1_000_000_000L));
				Lobecorp.LOGGER.error("群体资源预热失败 {}，保留其他资源并延后重试", visual.model(), failure);
			}
		}
	}

	private record PreparationRetry(int failures, long atNanos) {
	}

	public static void draw(List<HordeClient.Batch> source, Matrix4fc modelView, boolean reverse) {
		drawSerial++;
		HordeRenderMetrics.beginFrame(HordeIrisShadows.active());
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.PARTICLE_AREAS, HordeAmbient.selectedAreas());
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.PARTICLES, HordeAmbient.emittedParticles());
		drawn = 0;
		uploadedBytes = 0;
		try {

			drawPrepared(source, modelView, reverse);
			resourceFailures = 0;
		} catch (RuntimeException exception) {
			int attempts = resourceFailures + 1;
			resourceFailures = attempts;
			// 只限制报错频率；保留有效资源和GPU历史，下一帧继续尝试，不整潮清空。
			long now = System.nanoTime();
			if (attempts == 1 || now - retryAtNanos >= 0) {
				retryAtNanos = now + Math.min(attempts, MAX_RETRY_SECONDS) * 1_000_000_000L;
				Lobecorp.LOGGER.error("群体GPU绘制失败，保留有效资源与平滑历史并在下一帧重试", exception);
			}
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.FAILURES, 1);
		} finally {
			HordeRenderMetrics.set(HordeRenderMetrics.Counter.INSTANCES, drawn);
			HordeRenderMetrics.set(HordeRenderMetrics.Counter.UPLOAD_BYTES, uploadedBytes + HordeRenderMetrics.value(HordeRenderMetrics.Counter.UPLOAD_BYTES));
			HordeRenderMetrics.endFrame();
		}
	}

	private static void drawPrepared(List<HordeClient.Batch> source, Matrix4fc modelView, boolean reverse) {
		drawn = 0;
		batches = 0;
		HordeDebugState.setGroups(0);
		uploadedBytes = 0;
		gpuCopiedBytes = 0;
		if (source.isEmpty()) {
			return;
		}
		var minecraft = Minecraft.getInstance();
		var target = minecraft.getMainRenderTarget();
		var colorTarget = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
		var depthTarget = RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
		if (colorTarget == null || depthTarget == null) {
			return;
		}
		List<Prepared> prepared;
		long preparationStarted = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.PREPARE);
		try {
			prepared = prepare(source);
		} finally {
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.PREPARE, preparationStarted);
		}
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.LOGICAL_BATCHES, prepared.size());
		if (prepared.isEmpty()) {
			return;
		}
		updateAvoidance(prepared);
		int groupCount = 0;
		for (int first = 0; first < prepared.size(); ) {
			int last = first;
			int instances = 0;
			while (last < prepared.size() && prepared.get(last).count() <= INSTANCES_PER_DRAW - instances) {
				instances += prepared.get(last++).count();
			}
			drawGroup(prepared.subList(first, last), modelView, reverse, colorTarget, depthTarget);
			groupCount++;
			first = last;
		}
		HordeDebugState.setGroups(groupCount);
		HordeDebugState.drawn(drawn);
		var sampled = new HashSet<EventData>();
		for (var item : prepared) {
			if (sampled.add(item.event())) {
				// 借用事件缓存；由remove/clear释放，采样不取得其资源所有权。
				@SuppressWarnings("resource") var event = item.event();
				event.particleSamples.capture(item.batch().event(), item.batch().time(), drawSerial,
						item.batch().firstRow(), item.batch().block().firstColumn(), item.batch().block().columns());
			}
		}
	}

	// EventData 由 EVENTS 持有；此处借用其 GPU 状态与采样资源。
	@SuppressWarnings("resource")
	private static void updateAvoidance(List<Prepared> prepared) {
		var updated = new HashSet<EventData>();
		ensureParameters(PARAMETER_BYTES);
		for (var item : prepared) {
			if (!updated.add(item.event())) {
				continue;
			}
			upload.clear().limit(PARAMETER_BYTES);
			writeParameters(item, 0, 0);
			upload.flip();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(0, PARAMETER_BYTES), upload);
			long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.MOTION);
			try {
				item.event().avoidance.update(item.batch().time(), parameters.slice(0, PARAMETER_BYTES),
						item.event().route.texture().getTextureView(), item.event().terrain.view(),
						item.batch().event().split.view());
			} finally {
				HordeRenderMetrics.end(HordeRenderMetrics.Stage.MOTION, started);
			}
			uploadedBytes += PARAMETER_BYTES;
		}
	}

	private static void prepareLight(List<Prepared> prepared) {
		long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.LIGHT);
		try {
			LIGHT.prepare(prepared, item -> item.batch().block());
		} finally {
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.LIGHT, started);
		}
	}

	private static void drawGroup(List<Prepared> prepared, Matrix4fc modelView, boolean reverse,
	                              GpuTextureView colorTarget, GpuTextureView depthTarget) {
		var minecraft = Minecraft.getInstance();
		try {
			prepareLight(prepared);
		} catch (IllegalArgumentException exception) {
			if (prepared.size() <= 1) {
				throw exception;
			}
			int middle = prepared.size() / 2;
			drawGroup(prepared.subList(0, middle), modelView, reverse, colorTarget, depthTarget);
			drawGroup(prepared.subList(middle, prepared.size()), modelView, reverse, colorTarget, depthTarget);
			return;
		}
		HordeRenderMetrics.add(HordeRenderMetrics.Counter.GROUPS, 1);
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.LIGHT_SECTIONS, LIGHT.activeSections());
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.LIGHT_SLOTS, LIGHT.sections());
		int total = 0;
		for (var item : prepared) {
			total = Math.addExact(total, item.count());
		}
		ensureMotion(total);
		boolean iris = irisPath();
		HordeVertexLayout layout = null;
		if (iris) {
			int[] vertices = prepared.stream().mapToInt(item -> Math.multiplyExact(
					item.count(), item.verticesPerInstance())).toArray();
			while (iris) {
				try {
					int words = HordeIrisShadows.entityVertexWords();
					layout = new HordeVertexLayout(vertices, words, IRIS.capacityWords());
					IRIS.prepare(layout.maximumVertices(), words);
					break;
				} catch (RuntimeException | OutOfMemoryError failure) {
					iris = IRIS.reduceBudget(failure);
				}
			}
		}
		if (HordeIrisShadows.active() && (!iris || !IRIS.degradation.isEmpty())) {
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.DEGRADED, 1);
		}
		var offsets = new int[prepared.size()];
		int offset = 0;
		int alignment = RenderSystem.getDevice().getUniformOffsetAlignment();
		int stride = Math.ceilDiv(PARAMETER_BYTES, alignment) * alignment;
		int required = Math.multiplyExact(prepared.size(), stride);
		ensureParameters(required);
		upload.clear().limit(required);
		for (int i = 0; i < prepared.size(); i++) {
			upload.position(i * stride);
			offsets[i] = offset;
			writeParameters(prepared.get(i), offset, iris ? layout.offsets()[i] : 0);
			offset += prepared.get(i).count();
		}
		upload.position(0).limit(required);
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(0, required), upload);
		uploadedBytes += required;
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1), new Vector3f(), new Matrix4f());
		var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		int maximum = prepared.stream().mapToInt(item -> item.verticesPerInstance() / 4 * 6).max().orElse(0);
		var indexBuffer = indices.getBuffer(maximum);
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		long motionStarted = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.MOTION, total);
		try {
			drawMotion(prepared, offsets, stride);
		} finally {
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.MOTION, motionStarted);
		}
		if (iris) {
			IRIS.draw(prepared, layout, stride, transforms, colorTarget, depthTarget);
			return;
		}

		long baseStarted = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.BASE, total);
		try {
			// 方向片单独使用透明裁剪与背面剔除管线。
			drawNativePass(prepared, reverse ? CARD_REVERSE : CARD_NORMAL, true,
					transforms, indexBuffer, indices.type(), colorTarget, depthTarget, linear, nearest, stride);
			drawNativePass(prepared, reverse ? REVERSE : NORMAL, false,
					transforms, indexBuffer, indices.type(), colorTarget, depthTarget, linear, nearest, stride);
		} finally {
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.BASE, baseStarted);
		}
	}

	/// 按是否片档拆成两趟绘制：片档开启背面剔除，完整模型保持双面。
	private static void drawNativePass(List<Prepared> prepared, RenderPipeline pipeline, boolean card,
	                                   GpuBufferSlice transforms, GpuBuffer indexBuffer, VertexFormat.IndexType indexType,
	                                   GpuTextureView colorTarget, GpuTextureView depthTarget, GpuSampler linear,
	                                   GpuSampler nearest, int stride) {
		boolean any = false;
		for (var item : prepared) {
			if ((item.geometry().model.tier() == 3) == card) {
				any = true;
				break;
			}
		}
		if (!any) {
			return;
		}
		try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> card ? "Lobecorp GPU horde cards" : "Lobecorp GPU horde", colorTarget, OptionalInt.empty(),
				depthTarget, OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(), linear);
			pass.bindTexture("HordeMotion", motionView, nearest);
			pass.setIndexBuffer(indexBuffer, indexType);
			for (int i = 0; i < prepared.size(); i++) {
				var item = prepared.get(i);
				if ((item.geometry().model.tier() == 3) != card) {
					continue;
				}
				pass.setUniform("HordeParameters", parameters.slice((long) i * stride, PARAMETER_BYTES));
				pass.bindTexture("Sampler0", item.geometry().base.getTextureView(), item.geometry().base.getSampler());
				pass.bindTexture("Glowmask", item.geometry().glow.getTextureView(), item.geometry().glow.getSampler());
				pass.bindTexture("HordeAnimation", item.geometry().animation.texture().getTextureView(), nearest);
				pass.setVertexBuffer(0, item.geometry().vertices);
				pass.drawIndexed(0, 0, item.verticesPerInstance() / 4 * 6, item.count());
				drawn += item.count();
				batches++;
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
			}
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
		}
	}

	// 动画资源归IRIS_ANIMATIONS缓存所有，统一在clear释放。
	@SuppressWarnings("resource")
	private static List<Prepared> prepare(List<HordeClient.Batch> source) {
		var prepared = new ArrayList<Prepared>();
		for (var batch : source) {
			var event = batch.event();
			if (event.failed) {
				continue;
			}
			int tier = selectLod(batch);
			var key = meshKey(event.visual, tier);
			try {
				if (HordeMeshCache.ready(event.visual, tier) == null) {
					requestResources(event.visual);
					continue;
				}
			} catch (RuntimeException exception) {
				event.failed = true;
				remove(event.id);
				prepared.removeIf(item -> item.batch().event() == event);
				Lobecorp.LOGGER.error("停止群体事件虚拟显示 {}：模型数据准备失败", event.id, exception);
				continue;
			}
			var geometry = GEOMETRY.get(key);
			if (geometry == null || irisPath() && !IRIS_ANIMATIONS.containsKey(key)) {
				requestResources(event.visual);
				continue;
			}
			HordeRenderMetrics.add(switch (tier) {
				case 1 -> HordeRenderMetrics.Counter.LOD_MID_BLOCKS;
				case 2 -> HordeRenderMetrics.Counter.LOD_FAR_BLOCKS;
				case 3 -> HordeRenderMetrics.Counter.LOD_CARD_BLOCKS;
				default -> HordeRenderMetrics.Counter.LOD_NEAR_BLOCKS;
			}, 1);
			HordeRenderMetrics.add(switch (tier) {
				case 1 -> HordeRenderMetrics.Counter.LOD_MID_INSTANCES;
				case 2 -> HordeRenderMetrics.Counter.LOD_FAR_INSTANCES;
				case 3 -> HordeRenderMetrics.Counter.LOD_CARD_INSTANCES;
				default -> HordeRenderMetrics.Counter.LOD_NEAR_INSTANCES;
			}, batch.count());
			var data = EVENTS.computeIfAbsent(event.id, _ -> new EventData(event));
			data.updateHeights(event);
			event.split.update(event, batch.time());
			data.terrain.update(event, data.heights, batch.time());
			data.avoidance.prepare(event, batch.time());
			int verticesPerInstance = verticesPerInstance(geometry);
			for (int offset = 0; offset < batch.count(); ) {
				int limit = irisPath() ? IRIS.instanceLimit(verticesPerInstance) : INSTANCES_PER_DRAW;
				int count = Math.min(limit, batch.count() - offset);
				prepared.add(new Prepared(batch, geometry, data, offset, count, verticesPerInstance));
				offset += count;
			}
		}
		return prepared;
	}

	/// 方向片固定十二个输出槽，方向与单片阈值完全由 GPU 判定；源帧仍缓存全部六向顶点。
	private static int verticesPerInstance(Geometry geometry) {
		return geometry.model.tier() == 3 ? 12 : geometry.model.vertexCount();
	}

	@SuppressWarnings("resource")
	private static void writeParameters(Prepared item, int motionOffset, int vertexOffset) {
		var batch = item.batch();
		var event = batch.event();
		var camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		var origin = event.origin.subtract(camera);
		long offset = batch.offset() + item.offset();
		long row = batch.firstRow() + offset / batch.block().columns();
		int columnOffset = (int) (offset % batch.block().columns());
		var split = event.split;
		HordeDebugState.addSplit(split);
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.REGIONS, split.count());
		put((float) origin.x, (float) origin.y, (float) origin.z, (float) batch.time());
		put((float) camera.x, (float) camera.y, (float) camera.z, (float) event.speed);
		put((float) event.rules.rowSpacing(), (float) event.rules.columnSpacing(), event.columns, (float) event.length);
		put((float) (batch.time() * event.speed - row * event.rules.rowSpacing()), batch.block().firstColumn(), batch.block().columns(), columnOffset);
		put((float) batch.block().first(), (float) batch.block().last(), vertexOffset, split.count());
		put(item.geometry().model.vertexCount(), item.geometry().model.frames(), item.geometry().model.duration(), item.geometry().hasGlow ? 1 : 0);
		put(LIGHT.mask(), event.nodes.length, 0, event.entityHeight);
		int sx = (int) Math.floor(event.origin.x / 16);
		int sy = (int) Math.floor(event.origin.y / 16);
		int sz = (int) Math.floor(event.origin.z / 16);
		put(sx, sy, sz, (float) (row % 1024));
		put((float) event.groundWidth,
				(float) (event.origin.x - sx * 16d), (float) (event.origin.y - sy * 16d), (float) (event.origin.z - sz * 16d));
		put(event.lightProbeHeight, event.heightColumns, IrisVertices.rowWidth(), (float) HordeSamplingUtil.MAX_VISUAL_OFFSET);
		upload.putInt(motionOffset).putInt(item.count()).putInt(motionWidth).putInt(IRIS.wordsPerVertex);
		// 分割让步：数量、纹理宽度（0 表示按实际纹理自适应）、提前带宽、调试标志。
		put(split.count(), 0, splitBand(event.speed), HordeVisualConfig.STATISTICS.get() ? 1 : 0);
		put(SPLIT_STRENGTH, SPLIT_LEAD, SPLIT_MAXIMUM_OFFSET, visualRadius(event.visual));
		// 光照区段缺失时的回退亮度：露天（方块光0、天空光15），不伪造洞穴亮度也不隐藏单位。
		put(0, 15, 0, 0);
		put((float) HordeVisualConfig.FRAME_DISTANCE.get(), (float) HordeVisualConfig.SINGLE_CARD_DISTANCE.get(),
				item.geometry().model.tier() == 3 ? 1f : 0f, item.verticesPerInstance());
		long firstStateRow = item.event().avoidance.firstRow();
		upload.putInt(Math.toIntExact(row - firstStateRow)).putInt((int) firstStateRow)
				.putInt((int) (firstStateRow >>> 32)).putInt(item.event().avoidance.rows());
	}

	/// 迎潮侧提前分流的带宽（格）：随浪潮速度增大，低速时仍保留基础宽度。
	private static float splitBand(double speed) {
		return (float) (SPLIT_BASE_BAND + speed * 8.0);
	}

	private static void put(float a, float b, float c, float d) {
		upload.putFloat(a).putFloat(b).putFloat(c).putFloat(d);
	}

	private static Geometry geometry(HordeVisualDefinition visual, HordeMeshCache.Model model) {
		float[] vertices = model.vertices();
		var data = MemoryUtil.memAlloc(Math.multiplyExact(vertices.length / HordeMeshCache.VERTEX_STRIDE, VERTEX_BYTES));
		GpuBuffer buffer = null;
		HordeDataTexture animation = null;
		HordeDataTexture uvs = null;
		var uvData = new float[model.vertexCount() * 4];
		float radius = 0;
		for (int at = 0; at < model.frameVertices().length; at += 6) {
			radius = Math.max(radius, (float) Math.hypot(model.frameVertices()[at], model.frameVertices()[at + 2]));
		}
		try {
			for (int i = 0; i < vertices.length; i += HordeMeshCache.VERTEX_STRIDE) {
				data.putFloat(vertices[i]).putFloat(vertices[i + 1]).putFloat(vertices[i + 2]);
				data.putFloat(vertices[i + 3]).putFloat(vertices[i + 4]);
				int vertex = i / HordeMeshCache.VERTEX_STRIDE;
				uvData[vertex * 4] = vertices[i + 3];
				uvData[vertex * 4 + 1] = vertices[i + 4];
				int quad = vertex / 4 * 4 * HordeMeshCache.VERTEX_STRIDE;
				for (int corner = 0; corner < 4; corner++) {
					uvData[vertex * 4 + 2] += vertices[quad + corner * HordeMeshCache.VERTEX_STRIDE + 3] * 0.25f;
					uvData[vertex * 4 + 3] += vertices[quad + corner * HordeMeshCache.VERTEX_STRIDE + 4] * 0.25f;
				}
				int bone = (int) vertices[i + 8];
				data.put((byte) bone).put((byte) (bone >>> 8)).put((byte) (bone >>> 16)).put((byte) 255);
				for (int normal = 5; normal < 8; normal++) {
					data.put((byte) Math.round(Math.clamp(vertices[i + normal], -1, 1) * 127));
				}
				data.put((byte) 0);
			}
			data.flip();
			buffer = RenderSystem.getDevice().createBuffer(() -> "Horde static model", GpuBuffer.USAGE_VERTEX, data);
			animation = new HordeDataTexture("Horde baked animation frames", model.frameVertices());
			uvs = new HordeDataTexture("Horde static UVs", uvData);
			var minecraft = Minecraft.getInstance();
			AbstractTexture base = minecraft.getTextureManager().getTexture(visual.texture());
			Identifier glowId = visual.texture().withPath(path -> path.replace(".png", "_glowmask.png"));
			boolean hasGlow = minecraft.getResourceManager().getResource(glowId).isPresent();
			AbstractTexture glow = hasGlow ? minecraft.getTextureManager().getTexture(glowId) : base;
			DynamicTexture cardAtlas = null;
			DynamicTexture cardGlowAtlas = null;
			if (model.tier() == 3) {
				// 片档用6方向×20帧的烘焙图集取代原模型贴图；失败则回退普通贴图+UV方向面。
				var source = HordeMeshCache.ready(visual, 0);
				try (var input = minecraft.getResourceManager().getResourceOrThrow(visual.texture()).open();
				     var image = NativeImage.read(input)) {
					var atlas = HordeCardBake.bake(source, image);
					if (atlas != null) {
						cardAtlas = new CardAtlasTexture(atlas);
						base = cardAtlas;
						glow = cardAtlas;
						hasGlow = false;
					}
				} catch (IOException | RuntimeException failure) {
					Lobecorp.LOGGER.warn("群体片图集未生成，回退6面盒贴图：{}", visual.texture(), failure);
				}
				// 发光图集：glowmask用同一方向/帧布局再烘焙一份，片档也能在夜间发光。
				if (cardAtlas != null && source != null) {
					var glowResource = minecraft.getResourceManager().getResource(glowId);
					if (glowResource.isPresent()) {
						try (var input = glowResource.get().open();
						     var image = NativeImage.read(input)) {
							var atlas = HordeCardBake.bakeGlow(source, image);
							if (atlas != null) {
								cardGlowAtlas = new CardAtlasTexture(atlas);
								glow = cardGlowAtlas;
								hasGlow = true;
							}
						} catch (IOException | RuntimeException failure) {
							Lobecorp.LOGGER.warn("群体片发光图集未生成，片保持无发光：{}", visual.texture(), failure);
						}
					}
				}
			}
			preparations++;
			return new Geometry(buffer, vertices.length / HordeMeshCache.VERTEX_STRIDE / 4 * 6, animation, uvs, model, base, glow, hasGlow, radius, cardAtlas, cardGlowAtlas);
		} catch (RuntimeException exception) {
			if (buffer != null) {
				buffer.close();
			}
			if (animation != null) {
				animation.close();
			}
			if (uvs != null) {
				uvs.close();
			}
			throw exception;
		} finally {
			MemoryUtil.memFree(data);
		}
	}

	private static void ensureMotion(int instances) {
		long pixels = Math.multiplyExact((long) instances, MOTION_VALUES);
		if (motion != null && (long) motionWidth * motionHeight >= pixels) {
			return;
		}
		var device = RenderSystem.getDevice();
		int limit = device.getMaxTextureSize();
		int width = Math.min(MOTION_ROW_WIDTH, limit);
		while (Math.ceilDiv(pixels, width) > limit && width < limit) {
			width = (int) Math.min((long) width * 2, limit);
		}
		long rows = Math.ceilDiv(pixels, width);
		if (rows > limit) {
			throw new IllegalArgumentException("群体运动缓存超过设备纹理尺寸");
		}
		int height = (int) Math.min(limit, (long) Integer.highestOneBit(Math.max(1, (int) rows - 1)) * 2);
		var replacement = device.createTexture(() -> "Horde per-instance motion",
				GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
				TextureFormat.RGBA8, width, height, 1, 1);
		GpuTextureView view;
		try {
			view = device.createTextureView(replacement);
		} catch (RuntimeException exception) {
			replacement.close();
			throw exception;
		}
		if (motionView != null) {
			motionView.close();
			motion.close();
		}
		motion = replacement;
		motionView = view;
		motionWidth = width;
		motionHeight = height;
	}

	// EventData由EVENTS缓存拥有，remove/clear统一关闭，此处只借用。
	@SuppressWarnings("resource")
	private static void drawMotion(List<Prepared> prepared, int[] offsets, int stride) {
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Lobecorp horde motion cache", motionView, OptionalInt.empty())) {
			pass.setPipeline(MOTION);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
			pass.setViewport(0, 0, motionWidth, motionHeight);
			pass.bindTexture("HordeLight", LIGHT.atlas().getTextureView(), nearest);
			pass.bindTexture("HordeLightIndex", LIGHT.lookup().getTextureView(), nearest);
			for (int i = 0; i < prepared.size(); i++) {
				var item = prepared.get(i);
				pass.setUniform("HordeParameters", parameters.slice((long) i * stride, PARAMETER_BYTES));
				pass.bindTexture("HordeRoute", item.event().route.texture().getTextureView(), nearest);
				pass.bindTexture("HordeHeights", item.event().heights.texture().getTextureView(), nearest);
				pass.bindTexture("HordeTerrain", item.event().terrain.view(), nearest);
				pass.bindTexture("HordeSplit", item.batch().event().split.view(), nearest);
				pass.bindTexture("HordeAvoidance", item.event().avoidance.view(), nearest);
				int first = Math.multiplyExact(offsets[i], MOTION_VALUES) / motionWidth;
				int last = Math.toIntExact(Math.ceilDiv((long) (offsets[i] + item.count()) * MOTION_VALUES, motionWidth));
				pass.enableScissor(0, first, motionWidth, last - first);
				pass.draw(0, 3);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
			}
			pass.disableScissor();
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
		LOD_STATES.remove(id);
		var data = EVENTS.remove(id);
		if (data != null) {
			data.close();
		}
	}

	public static void clearModels() {
		HordeParticleSamples.invalidate();
		for (var event : EVENTS.values()) {
			event.particleSamples.close();
			event.particleSamples = new HordeParticleSamples();
		}
		GEOMETRY.values().forEach(geometry -> {
			geometry.vertices.close();
			geometry.animation.close();
			geometry.uvs.close();
			if (geometry.cardAtlas != null) {
				geometry.cardAtlas.close();
			}
			if (geometry.cardGlowAtlas != null) {
				geometry.cardGlowAtlas.close();
			}
		});
		GEOMETRY.clear();
		MODEL_KEYS.clear();
		PENDING_VISUALS.clear();
		resetPipelineWarmup(false);
		VISUAL_RETRIES.clear();
		IRIS_ANIMATIONS.values().forEach(HordeIrisAnimation::close);
		IRIS_ANIMATIONS.clear();
	}

	public static void clear() {
		EVENTS.values().forEach(EventData::close);
		EVENTS.clear();
		clearModels();
		LOD_STATES.clear();
		drawSerial = 0;
		LIGHT.close();
		IRIS.close();
		if (motionView != null) {
			motionView.close();
			motion.close();
			motionView = null;
			motion = null;
		}
		if (parameters != null) {
			parameters.close();
			parameters = null;
			MemoryUtil.memFree(upload);
			upload = null;
		}
		drawn = 0;
		batches = 0;
		retryAtNanos = 0;
		resourceFailures = 0;
	}

	public static float modelRadius(HordeVisualDefinition visual) {
		var geometry = GEOMETRY.get(meshKey(visual, 0));
		return geometry == null ? 0 : geometry.radius();
	}

	public static JsonObject resourceStatistics() {
		var result = new JsonObject();
		long bytes = 0;
		for (var slot : IRIS.slots) {
			if (slot.texture != null) {
				bytes += (long) slot.width * slot.height * 8;
			}
		}
		result.addProperty("iris_workset_owned_bytes", bytes);
		result.addProperty("avoidance_state_texture_bytes", EVENTS.values().stream()
				.mapToLong(event -> event.avoidance.textureBytes()).sum());
		result.addProperty("avoidance_state_words", HordeAvoidanceState.WORDS);
		result.addProperty("iris_active_miwords", IRIS.budgetWords / (1024 * 1024));
		result.addProperty("iris_requested_miwords", HordeVisualConfig.IRIS_WORKSET_WORDS.get());
		result.addProperty("iris_double_buffer", HordeVisualConfig.IRIS_DOUBLE_BUFFER.get());
		result.addProperty("iris_material_merge", HordeVisualConfig.IRIS_MERGE_MATERIALS.get());
		result.addProperty("lod_enabled", HordeVisualConfig.LOD_ENABLED.get());
		result.addProperty("animation_frames", HordeVisualConfig.ANIMATION_FRAMES.get());
		result.addProperty("degradation", IRIS.degradation);
		var models = new JsonArray();
		GEOMETRY.entrySet().stream().sorted(Comparator.comparing(
				entry -> entry.getKey().visual().model().toString() + ":" + entry.getKey().tier())).forEach(entry -> {
			var value = new JsonObject();
			var model = entry.getValue().model();
			value.addProperty("model", entry.getKey().visual().model().toString());
			value.addProperty("tier", model.tier());
			value.addProperty("vertices_before", model.sourceVertexCount());
			value.addProperty("vertices_after", model.vertexCount());
			value.addProperty("frames", model.frames());
			value.addProperty("duration_seconds", model.duration());
			value.addProperty("baked_cpu_bytes", (long) model.frameVertices().length * Float.BYTES);
			value.addProperty("static_gpu_vertex_bytes", entry.getValue().vertices().size());
			models.add(value);
		});
		result.add("models", models);
		return result;
	}

	public static String statistics() {
		return "GPU instances=" + drawn + ", batches=" + batches + ", upload=" + uploadedBytes
				+ "B, gpuCopy=" + gpuCopiedBytes + "B, lightSections=" + LIGHT.sections() + ", modelBuilds=" + preparations
				+ ", irisMiWords=" + IRIS.budgetWords / (1024 * 1024) + ", irisSlots=" + IRIS.slotCount()
				+ ", degraded=" + (HordeIrisShadows.active() && HordeIrisShadows.entityVertexWords() == 0 ? "unsupported_layout" : IRIS.degradation) + ", shadows=0";
	}

	private record GeometryKey(HordeVisualDefinition visual, int tier, int settings) {
	}

	/// 片图集用夹边最近邻采样，避免远距离mipmap把相邻方向/帧tile混成噪点。
	private static final class CardAtlasTexture extends DynamicTexture {
		private CardAtlasTexture(NativeImage image) {
			super(() -> "Horde card atlas", image);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		}
	}

	private record Geometry(GpuBuffer vertices, int indices, HordeDataTexture animation, HordeDataTexture uvs,
	                        HordeMeshCache.Model model,
	                        AbstractTexture base, AbstractTexture glow, boolean hasGlow, float radius,
	                        @Nullable DynamicTexture cardAtlas, @Nullable DynamicTexture cardGlowAtlas) {
	}

	private record Prepared(HordeClient.Batch batch, Geometry geometry, EventData event, int offset, int count,
	                        int verticesPerInstance) {
	}

	/// GPU展开原版实体顶点，再交给Iris已有材质管线；不映射或读取回CPU。
	private static class IrisVertices implements AutoCloseable {
		// 单槽纹理与VBO共128MiB，双槽上限256MiB；仅限制工作集，不减少单位。
		private static final int MAX_WORDS = 16 * 1024 * 1024;
		private static final int MIN_WORDS = 4 * 1024 * 1024;
		private static final long RETRY_NANOS = 5_000_000_000L;
		private static final RenderPipeline BAKE = RenderPipeline.builder()
				.withLocation(Lobecorp.id("pipeline/horde_iris_vertices"))
				.withVertexShader(Lobecorp.id("core/horde_motion"))
				.withFragmentShader(Lobecorp.id("core/horde_iris_vertices"))
				.withSampler("HordeMotion").withSampler("HordeIrisAnimation").withSampler("HordeUVs")
				.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false)
				.withColorTargetState(ColorTargetState.DEFAULT).build();
		private final Slot[] slots = {new Slot(), new Slot()};
		private int wordsPerVertex = 9;
		private int configuredWords;
		private int budgetWords = MAX_WORDS;
		private long recoveryAt;
		private long disabledUntil;
		private String degradation = "";

		private static int rowWidth() {
			return Integer.highestOneBit(Math.min(4096, RenderSystem.getDevice().getMaxTextureSize()));
		}

		private void synchronizeBudget() {
			int requested = Math.min(MAX_WORDS,
					Integer.highestOneBit(HordeVisualConfig.IRIS_WORKSET_WORDS.get()) * 1024 * 1024);
			long now = System.nanoTime();
			if (configuredWords != requested || recoveryAt != 0 && now - recoveryAt >= 0) {
				configuredWords = requested;
				budgetWords = requested;
				disabledUntil = 0;
				recoveryAt = 0;
			}
		}

		private boolean available() {
			synchronizeBudget();
			return disabledUntil == 0 || System.nanoTime() - disabledUntil >= 0;
		}

		private long capacityWords() {
			synchronizeBudget();
			return Math.min(budgetWords, (long) rowWidth() * RenderSystem.getDevice().getMaxTextureSize());
		}

		private int instanceLimit(int vertexCount) {
			long words = Math.multiplyExact((long) vertexCount, HordeIrisShadows.entityVertexWords());
			long capacity = capacityWords();
			if (words <= 0 || words > capacity) {
				return INSTANCES_PER_DRAW;
			}
			return (int) Math.min(INSTANCES_PER_DRAW, capacity / words);
		}

		private boolean reduceBudget(Throwable failure) {
			dropSlots();
			recoveryAt = System.nanoTime() + RETRY_NANOS;
			if (budgetWords > MIN_WORDS) {
				budgetWords = Math.max(MIN_WORDS, budgetWords / 2);
				degradation = "workset=" + budgetWords / (1024 * 1024) + "MiWords";
				Lobecorp.LOGGER.warn("Iris群体工作集缩小为{}，实例数量不变", degradation, failure);
				return true;
			}
			disabledUntil = recoveryAt;
			degradation = "native_fallback:" + failure.getClass().getSimpleName();
			Lobecorp.LOGGER.warn("Iris群体工作集暂不可用，回退实例化并延后恢复", failure);
			return false;
		}

		private void disable(Throwable failure) {
			dropSlots();
			recoveryAt = System.nanoTime() + RETRY_NANOS;
			disabledUntil = recoveryAt;
			degradation = "native_fallback:" + failure.getClass().getSimpleName();
			Lobecorp.LOGGER.warn("Iris群体帧资源暂不可用，保留模型并回退实例化", failure);
		}

		private int slotCount() {
			return HordeVisualConfig.IRIS_DOUBLE_BUFFER.get() ? 2 : 1;
		}

		private void prepare(int vertexCount, int layoutWords) {
			wordsPerVertex = layoutWords;
			int width = rowWidth();
			int rows = Math.ceilDiv(Math.multiplyExact(vertexCount, layoutWords), width);
			int maximumRows = (int) Math.min(RenderSystem.getDevice().getMaxTextureSize(), capacityWords() / width);
			if (rows > maximumRows) {
				throw new IllegalArgumentException("Iris群体顶点超过当前工作集容量");
			}
			int height = Math.min(maximumRows, Integer.highestOneBit(Math.max(1, rows - 1)) * 2);
			try {
				for (int slot = 0; slot < slotCount(); slot++) {
					slots[slot].prepare(width, height, maximumRows, slot);
				}
				if (slotCount() == 1) {
					slots[1].close();
				}
				degradation = budgetWords < configuredWords ? "workset=" + budgetWords / (1024 * 1024) + "MiWords" : "";
			} catch (RuntimeException | OutOfMemoryError failure) {
				dropSlots();
				throw failure;
			}
		}

		private void draw(List<Prepared> items, HordeVertexLayout layout, int stride,
		                  GpuBufferSlice transforms, GpuTextureView colorTarget, GpuTextureView depthTarget) {
			var window = new ArrayList<Chunk>(slotCount());
			int first = 0;
			int block = 0;
			int width = HordeVisualConfig.IRIS_MERGE_MATERIALS.get() ? slotCount() : 1;
			for (int last : layout.ends()) {
				var slot = slots[block++ % slotCount()];
				window.add(expand(items, first, last, layout.offsets(), stride, slot));
				first = last;
				if (window.size() == width) {
					drawMaterials(items, window, layout.offsets(), transforms, colorTarget, depthTarget);
					window.clear();
				}
			}
			if (!window.isEmpty()) {
				drawMaterials(items, window, layout.offsets(), transforms, colorTarget, depthTarget);
			}
		}

		private Chunk expand(List<Prepared> items, int first, int last, int[] offsets, int stride, Slot slot) {
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
			var tail = items.get(last - 1);
			int vertices = offsets[last - 1] + Math.multiplyExact(tail.count(), tail.verticesPerInstance());
			int rows = Math.ceilDiv(Math.multiplyExact(vertices, wordsPerVertex), slot.width);
			long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.EXPAND, vertices);
			try (var pass = encoder.createRenderPass(() -> "Horde Iris GPU vertex expansion", slot.view, OptionalInt.empty())) {
				pass.setPipeline(BAKE);
				pass.setViewport(0, 0, slot.width, slot.height);
				pass.bindTexture("HordeMotion", motionView, nearest);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
				for (int i = first; i < last; i++) {
					var item = items.get(i);
					pass.setUniform("HordeParameters", parameters.slice((long) i * stride, PARAMETER_BYTES));
					pass.bindTexture("HordeIrisAnimation", IRIS_ANIMATIONS.get(meshKey(item.batch().event().visual, item.geometry().model.tier())).view(), nearest);
					pass.bindTexture("HordeUVs", item.geometry().uvs.texture().getTextureView(), nearest);
					int low = Math.multiplyExact(offsets[i], wordsPerVertex) / slot.width;
					int high = Math.ceilDiv(Math.multiplyExact(offsets[i]
							+ Math.multiplyExact(item.count(), item.verticesPerInstance()), wordsPerVertex), slot.width);
					pass.enableScissor(0, low, slot.width, high - low);
					pass.draw(0, 3);
					HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
					drawn += item.count();
				}
				pass.disableScissor();
			} finally {
				HordeRenderMetrics.end(HordeRenderMetrics.Stage.EXPAND, started);
			}
			long bytes = (long) slot.width * rows * Integer.BYTES;
			started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.COPY, bytes);
			try {
				// 两槽各自拥有纹理和VBO；有序GPU提交保护复用，不读取或映射回CPU。
				encoder.copyTextureToBuffer(slot.texture, slot.vertices, 0, () -> {
				}, 0, 0, 0, slot.width, rows);
				gpuCopiedBytes += bytes;
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.COPIES, 1);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.COPY_BYTES, bytes);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.EXPANDED_VERTICES, vertices);
			} finally {
				HordeRenderMetrics.end(HordeRenderMetrics.Stage.COPY, started);
			}
			return new Chunk(slot, first, last, vertices);
		}

		private void drawMaterials(List<Prepared> items, List<Chunk> chunks, int[] offsets,
		                           GpuBufferSlice transforms, GpuTextureView colorTarget, GpuTextureView depthTarget) {
			var minecraft = Minecraft.getInstance();
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
			var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
			int maximum = chunks.stream().mapToInt(chunk -> chunk.vertices() / 4 * 6).max().orElse(0);
			var indexBuffer = indices.getBuffer(maximum);
			int vertices = chunks.stream().mapToInt(Chunk::vertices).sum();
			boolean glow = false;
			for (var chunk : chunks) {
				for (int i = chunk.first(); i < chunk.last(); i++) {
					glow |= items.get(i).geometry().hasGlow;
				}
			}
			// 片档是封闭盒：基础层单独用开启背面剔除的原版管线，完整模型保持双面。
			drawMaterialPass(items, chunks, offsets, transforms, colorTarget, depthTarget, minecraft,
					encoder, nearest, linear, indexBuffer, indices.type(), vertices, false, false);
			drawMaterialPass(items, chunks, offsets, transforms, colorTarget, depthTarget, minecraft,
					encoder, nearest, linear, indexBuffer, indices.type(), vertices, false, true);
			if (glow) {
				drawMaterialPass(items, chunks, offsets, transforms, colorTarget, depthTarget, minecraft,
						encoder, nearest, linear, indexBuffer, indices.type(), vertices, true, false);
			}
		}

		private void drawMaterialPass(List<Prepared> items, List<Chunk> chunks, int[] offsets,
		                              GpuBufferSlice transforms, GpuTextureView colorTarget, GpuTextureView depthTarget,
		                              Minecraft minecraft, CommandEncoder encoder, GpuSampler nearest, GpuSampler linear,
		                              GpuBuffer indexBuffer, VertexFormat.IndexType indexType, int vertices,
		                              boolean glowLayer, boolean cardOnly) {
			var stage = glowLayer ? HordeRenderMetrics.Stage.GLOW : HordeRenderMetrics.Stage.BASE;
			long started = HordeRenderMetrics.begin(stage, vertices);
			try (var pass = encoder.createRenderPass(
					() -> glowLayer ? "Horde Iris glow material" : "Horde Iris merged entity material", colorTarget,
					OptionalInt.empty(), depthTarget, OptionalDouble.empty())) {
				pass.setPipeline(glowLayer ? RenderPipelines.EYES
						: cardOnly ? RenderPipelines.ENTITY_CUTOUT_CULL : RenderPipelines.ENTITY_CUTOUT);
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("DynamicTransforms", transforms);
				if (!glowLayer) {
					pass.bindTexture("Sampler1", minecraft.gameRenderer.overlayTexture().getTextureView(), nearest);
				}
				pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), linear);
				pass.setIndexBuffer(indexBuffer, indexType);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
				for (var chunk : chunks) {
					pass.setVertexBuffer(0, chunk.slot().vertices);
					for (int i = chunk.first(); i < chunk.last(); ) {
						var item = items.get(i);
						if (glowLayer ? !item.geometry().hasGlow : (item.geometry().model.tier() == 3) != cardOnly) {
							i++;
							continue;
						}
						var material = glowLayer ? item.geometry().glow : item.geometry().base;
						int begin = offsets[i];
						int count = item.count() * item.verticesPerInstance();
						i++;
						while (i < chunk.last()) {
							var next = items.get(i);
							if (glowLayer && !next.geometry().hasGlow) {
								break;
							}
							if (!glowLayer && (next.geometry().model.tier() == 3) != cardOnly) {
								break;
							}
							if ((glowLayer ? next.geometry().glow : next.geometry().base) != material) {
								break;
							}
							count += next.count() * next.verticesPerInstance();
							i++;
						}
						pass.bindTexture("Sampler0", material.getTextureView(), material.getSampler());
						pass.drawIndexed(begin, 0, count / 4 * 6, 1);
						batches++;
						HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
					}
				}
			} finally {
				HordeRenderMetrics.end(stage, started);
			}
		}

		private void dropSlots() {
			for (var slot : slots) {
				slot.close();
			}
		}

		@Override
		public void close() {
			dropSlots();
			wordsPerVertex = 9;
			configuredWords = 0;
			budgetWords = MAX_WORDS;
			recoveryAt = 0;
			disabledUntil = 0;
			degradation = "";
		}

		private record Chunk(Slot slot, int first, int last, int vertices) {
		}

		private static class Slot implements AutoCloseable {
			private GpuTexture texture;
			private GpuTextureView view;
			private GpuBuffer vertices;
			private int width;
			private int height;

			private void prepare(int targetWidth, int targetHeight, int maximumRows, int slot) {
				if (texture != null && width == targetWidth && height >= targetHeight && height <= maximumRows) {
					return;
				}
				close();
				var device = RenderSystem.getDevice();
				try {
					texture = device.createTexture(() -> "Horde Iris vertex words slot " + slot,
							GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
							TextureFormat.RGBA8, targetWidth, targetHeight, 1, 1);
					view = device.createTextureView(texture);
					vertices = device.createBuffer(() -> "Horde Iris vertices slot " + slot,
							GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, (long) targetWidth * targetHeight * 4);
					width = targetWidth;
					height = targetHeight;
				} catch (RuntimeException | OutOfMemoryError failure) {
					close();
					throw failure;
				}
			}

			@Override
			public void close() {
				try {
					if (view != null) {
						view.close();
					}
				} finally {
					try {
						if (texture != null) {
							texture.close();
						}
					} finally {
						if (vertices != null) {
							vertices.close();
						}
						texture = null;
						view = null;
						vertices = null;
						width = 0;
						height = 0;
					}
				}
			}
		}
	}

	private static class EventData implements AutoCloseable {
		private final HordeDataTexture route;
		private final HordeDataTexture heights;
		private final HordeTerrainState terrain = new HordeTerrainState();
		private final HordeAvoidanceState avoidance = new HordeAvoidanceState();
		private HordeParticleSamples particleSamples = new HordeParticleSamples();

		private EventData(HordeClient.ClientEvent event) {
			route = new HordeDataTexture("Horde fixed route " + event.id, event.routeData);
			try {
				heights = new HordeDataTexture("Horde ground heights " + event.id, event.heightData);
			} catch (RuntimeException exception) {
				route.close();
				throw exception;
			}
		}

		private void updateHeights(HordeClient.ClientEvent event) {
			for (int node = event.changedHeights.nextSetBit(0); node >= 0; node = event.changedHeights.nextSetBit(node + 1)) {
				heights.update(node * event.heightColumns, event.heightData, node * event.heightColumns, event.heightColumns);
			}
			event.changedHeights.clear();
		}


		@Override
		public void close() {
			route.close();
			heights.close();
			terrain.close();
			avoidance.close();
			particleSamples.close();
		}
	}
}
