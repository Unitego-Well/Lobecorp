package org.unitego.lobecorp.client.horde;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.*;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryStack;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.config.HordePathVariant;
import org.unitego.lobecorp.registry.client.horde.HordeClientHooks;
import org.unitego.lobecorp.util.horde.HordeGpuPacking;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

/// 常驻模型、线路和骨骼动画；每批只上传时间与编号区间，不上传逐单位变换。
public class HordeGpuRenderer {
	// std140共16个vec4，与horde_motion.glsl的HordeParameters一致。
	private static final int PARAMETER_BYTES = 256;
	private static final int VERTEX_BYTES = 28;
	private static final int INSTANCES_PER_DRAW = 65536;
	// 位置、缩放、光照、方向、帧编号及近处分割边缘标记，与GLSL布局一致。
	private static final int MOTION_VALUES = 9;
	// 分割让步的推出强度、提前参数与单侧最大偏移（格）。
	private static final float SPLIT_STRENGTH = 1.0f;
	private static final float SPLIT_LEAD = 2.5f;
	private static final float SPLIT_MAXIMUM_OFFSET = 16.0f;
	private static final int MAX_RETRY_SECONDS = 5;
	// 开发测量开关：默认复用同一次准备内的分割更新，仅显式设为 false 时跳过去重。
	private static final String SPLIT_DEDUPLICATION_PROPERTY = "lobecorp.horde.splitDeduplication";
	private static final boolean SPLIT_DEDUPLICATION = !"false".equalsIgnoreCase(
			System.getProperty(SPLIT_DEDUPLICATION_PROPERTY));
	private static final int MOTION_ROW_WIDTH = 1024;
	private static final VertexFormat FORMAT = VertexFormat.builder()
			.add("Position", VertexFormatElement.POSITION).add("UV0", VertexFormatElement.UV0)
			.add("Color", VertexFormatElement.COLOR).add("Normal", VertexFormatElement.NORMAL).padding(1).build();
	private static final RenderPipeline NORMAL = pipeline(false, false);
	private static final RenderPipeline REVERSE = pipeline(true, false);
	// 片档是共中心的六向投影片：只提交朝观察者的方向片，透明裁剪并保持背面剔除。
	private static final RenderPipeline CARD_NORMAL = cardPipeline(false);
	private static final RenderPipeline CARD_REVERSE = cardPipeline(true);
	private static final RenderPipeline NORMAL_LAYER = pipeline(false, false, true);
	private static final RenderPipeline REVERSE_LAYER = pipeline(true, false, true);
	private static final RenderPipeline CARD_NORMAL_LAYER = pipeline(false, true, true);
	private static final RenderPipeline CARD_REVERSE_LAYER = pipeline(true, true, true);
	private static final RenderPipeline PEEL_CARD = HordeLayerCulling.pipeline(FORMAT, false, true);
	private static final RenderPipeline PEEL_CARD_REVERSE = HordeLayerCulling.pipeline(FORMAT, true, true);
	private static final RenderPipeline PEEL_MODEL = HordeLayerCulling.pipeline(FORMAT, false, false);
	private static final RenderPipeline PEEL_MODEL_REVERSE = HordeLayerCulling.pipeline(FORMAT, true, false);
	private static final RenderPipeline MOTION = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_motion"))
			.withVertexShader(Lobecorp.id("core/horde_motion")).withFragmentShader(Lobecorp.id("core/horde_motion"))
			.withSampler("HordeRoute").withSampler("HordeHeights").withSampler("HordeLight")
			.withSampler("HordeLightIndex").withSampler("HordeTerrain").withSampler("HordeSplit")
			.withSampler("HordeAvoidance").withSampler("HordePose")
			.withSampler("HordeNearTerrain")
			.withUniform("HordeNearTerrainParameters", UniformType.UNIFORM_BUFFER)
			.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false)
			.withColorTargetState(ColorTargetState.DEFAULT).build();
	private static final Map<GeometryKey, Geometry> GEOMETRY = new HashMap<>();
	private static final Map<UUID, EventData> EVENTS = new HashMap<>();
	private static final Map<GeometryKey, HordeIrisAnimation> IRIS_ANIMATIONS = new HashMap<>();
	private static final HordeLightField LIGHT = new HordeLightField();
	private static final IrisVertices IRIS = new IrisVertices();
	private static final HordeLodSelection LOD = new HordeLodSelection();
	private static final long PREPARATION_BUDGET_NS = 3_000_000L;
	private static final Set<GeometryKey> PENDING_VISUALS = new LinkedHashSet<>();
	// 0=MOTION，1=Iris顶点烘焙，2=地形，3=Iris动画，4/5=原生模型，6/7=片，8=避让，9～14=LOD与深度筛选。
	private static final int PIPELINE_COUNT = 16;
	private static final int[] NATIVE_PIPELINE_ORDER = {2, 3, 1, 8, 0, 15, 9, 10, 11, 12, 13, 14, 4, 5, 6, 7};
	private static final int[] IRIS_PIPELINE_ORDER = {1, 3, 2, 8, 0, 15, 9, 10, 11, 12, 13, 14, 4, 5, 6, 7};
	private static final boolean[] COMPILED_PIPELINES = new boolean[PIPELINE_COUNT];
	private static final int[] PIPELINE_FAILURES = new int[PIPELINE_COUNT];
	private static final long[] PIPELINE_RETRY_AT = new long[PIPELINE_COUNT];
	private static final Map<GeometryKey, PreparationRetry> VISUAL_RETRIES = new HashMap<>();
	private static final Map<HordeVisualDefinition, GeometryKey[]> MODEL_KEYS = new HashMap<>();
	private static int resourceFailures;
	private static long retryAtNanos;
	private static final RenderPipeline FACING = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_card_facing"))
			.withVertexShader(Lobecorp.id("core/horde_motion"))
			.withFragmentShader(Lobecorp.id("core/horde_card_facing"))
			.withSampler("HordeMotion").withSampler("HordeAnimation")
			.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false)
			.withColorTargetState(ColorTargetState.DEFAULT).build();

	private static final RenderPipeline MOTION_LINES = shaderFlags(RenderPipeline.builder(), false)
			.withLocation(Lobecorp.id("pipeline/horde_motion_lines"))
			.withVertexShader(Lobecorp.id("core/horde_motion_lines")).withFragmentShader(Lobecorp.id("core/horde_motion_lines"))
			.withSampler("HordeRoute").withSampler("HordeHeights").withSampler("HordeLight")
			.withSampler("HordeLightIndex").withSampler("HordeTerrain").withSampler("HordeSplit")
			.withSampler("HordeAvoidance").withSampler("HordePose").withSampler("HordeNearTerrain")
			.withUniform("HordeNearTerrainParameters", UniformType.UNIFORM_BUFFER)
			.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
			.withUniform("HordeLineOutput", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.DEBUG_LINES).withCull(false)
			.withColorTargetState(ColorTargetState.DEFAULT).build();
	private static final RenderPipeline FACING_LINES = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_facing_lines"))
			.withVertexShader(Lobecorp.id("core/horde_card_facing_lines")).withFragmentShader(Lobecorp.id("core/horde_card_facing_lines"))
			.withSampler("HordeMotion").withSampler("HordeAnimation")
			.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.DEBUG_LINES).withCull(false)
			.withColorTargetState(ColorTargetState.DEFAULT).build();
	private static GpuBuffer motionOutput;
	private static final HordeLayerCulling LAYERS = new HordeLayerCulling();
	private static HordeDataTexture visibleIDs;
	private static GpuTextureView visibleFlags;
	private static GpuBuffer layerDrawParameters;
	private static ByteBuffer layerDrawUpload;
	private static boolean layerMapActive;
	private static String layerStatus = "disabled";
	private static int layerInputCount, layerVisibleCount;
	private static boolean layerSupported = true;
	private static long layerReadbackBytes;
	private static HordeDataTexture layerCompact;
	private static GpuBuffer compactReadback;
	private static GpuTexture facing;
	private static GpuTextureView facingView;
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
	private static int nextPipeline;
	private static int compiledPipelineCount;
	private static boolean pipelineIrisMode;
	private static int pipelineGeneration;
	private static boolean renderPathDirty;
	private static HordeVisualDefinition parameterVisual;
	private static float parameterRadius;

	private static RenderPipeline pipeline(boolean reverse, boolean cull) {
		return pipeline(reverse, cull, false);
	}

	private static RenderPipeline pipeline(boolean reverse, boolean cull, boolean layered) {
		String location = reverse ? "pipeline/horde_gpu_reverse" : "pipeline/horde_gpu";
		return shaderFlags(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET), layered)
				.withLocation(Lobecorp.id((cull ? location + "_cull" : location) + (layered ? "_layers" : "")))
				.withVertexShader(Lobecorp.id("core/horde_instanced")).withFragmentShader(Lobecorp.id("core/horde_instanced"))
				.withSampler("Sampler0").withSampler("Sampler2").withSampler("Glowmask")
				.withSampler("HordeMotion").withSampler("HordeAnimation")
				.withSampler("HordeLodSelected").withSampler("HordeLodCompact").withSampler("HordeCardFacing")
				.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(FORMAT, VertexFormat.Mode.QUADS).withCull(cull)
				.withDepthStencilState(new DepthStencilState(reverse ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, true))
				.withColorTargetState(ColorTargetState.DEFAULT).build();
	}

	/// 片档专用：透明裁剪（cutout）+ 背面剔除；按不透明队列绘制，不做半透明混合。
	private static RenderPipeline.Builder shaderFlags(RenderPipeline.Builder builder, boolean layered) {
		if (!HordePathVariant.detailLod()) {
			builder.withShaderDefine("HORDE_NO_DETAIL_LOD");
		}
		if (!HordePathVariant.facingCache()) {
			builder.withShaderDefine("HORDE_NO_FACING_CACHE");
		}
		if (layered) {
			builder.withShaderDefine("HORDE_LAYER_MAP");
			builder.withSampler("HordeVisibleIDs").withSampler("HordeVisibleFlags")
					.withUniform("HordeLayerDraw", UniformType.UNIFORM_BUFFER);
		}
		return builder;
	}

	private static RenderPipeline cardPipeline(boolean reverse) {
		return pipeline(reverse, true);
	}

	private static boolean irisPath() {
		return HordeRenderPolicy.settings().shaderPackActive() && HordeIrisShadows.entityVertexWords() > 0 && IRIS.available();
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
		boolean iris = irisPath();
		for (int tier = 0; tier <= 3; tier += 3) {
			var key = meshKey(visual, tier);
			if (!GEOMETRY.containsKey(key) || iris && !IRIS_ANIMATIONS.containsKey(key)) {
				PENDING_VISUALS.add(key);
			}
		}
	}

	/// 菜单前只检查公共几何，不依赖进入世界后才确定的Iris实体格式。
	public static boolean resourcesReady(HordeVisualDefinition visual) {
		return GEOMETRY.containsKey(meshKey(visual, 0)) && GEOMETRY.containsKey(meshKey(visual, 3));
	}

	/// 每帧在绘制前推进；驱动编译和单次上传不可抢占，预算仅约束步骤之间的调度。
	public static void prepareResources() {
		long started = System.nanoTime();
		preparePipeline(started);
		prepareVisuals(started);
		HordeMeshCache.advance(PREPARATION_BUDGET_NS - (System.nanoTime() - started));
		if (System.nanoTime() - started < PREPARATION_BUDGET_NS) {
			HordeClientHooks.prepare();
		}
	}

	private static void preparePipeline(long started) {
		synchronizeRenderPath();
		boolean iris = HordeRenderPolicy.settings().shaderPackActive();

		if (compiledPipelineCount == PIPELINE_COUNT) {
			return;
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
					case 0 -> device.precompilePipeline(HordeRenderPolicy.motionKernel() ? MOTION_LINES : MOTION).isValid();
					case 1 -> device.precompilePipeline(IrisVertices.bake(false)).isValid();
					case 2 -> HordeTerrainState.precompile();
					case 3 -> HordeIrisAnimation.precompile();
					case 4 -> device.precompilePipeline(NORMAL).isValid();
					case 5 -> device.precompilePipeline(REVERSE).isValid();
					case 6 -> device.precompilePipeline(CARD_NORMAL).isValid();
					case 7 -> device.precompilePipeline(CARD_REVERSE).isValid();
					case 8 -> HordeAvoidanceState.precompile();
					case 15 -> !HordePathVariant.facingCache() || device.precompilePipeline(HordePathVariant.facingKernel() ? FACING_LINES : FACING).isValid();
					case 9, 10, 11, 12, 13, 14 -> HordeLodSelection.precompile(index - 9);
					default -> throw new IllegalStateException("群体管线预热步骤无效");
				};
				if (!valid) {
					throw new IllegalStateException("群体管线预编译结果无效");
				}

				COMPILED_PIPELINES[index] = true;
				compiledPipelineCount++;
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

	private static void synchronizeRenderPath() {
		boolean iris = HordeIrisShadows.active();
		boolean changed = HordeRenderPolicy.refresh(iris);
		renderPathDirty |= changed || pipelineIrisMode != iris || pipelineGeneration != HordeIrisShadows.shaderGeneration();
		if (!renderPathDirty) {
			return;
		}

		// 仅清理依赖渲染路径的缓存，保留事件、路径、避让历史及烘焙几何。
		HordeParticleSamples.invalidate();
		LOD.close();
		IRIS.close();
		if (facingView != null) {
			facingView.close();
			facingView = null;
		}
		if (facing != null) {
			facing.close();
			facing = null;
		}
		if (motionView != null) {
			motionView.close();
			motionView = null;
		}
		if (motion != null) {
			motion.close();
			motion = null;
		}
		if (motionOutput != null) {
			motionOutput.close();
			motionOutput = null;
		}
		motionWidth = 0;
		motionHeight = 0;
		resetPipelineWarmup(iris);
		renderPathDirty = false;
	}

	private static void resetPipelineWarmup(boolean iris) {
		Arrays.fill(COMPILED_PIPELINES, false);
		compiledPipelineCount = 0;
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

	public static void draw(List<HordeClient.Batch> source, Matrix4fc modelView, boolean reverse) {
		HordeRenderMetrics.beginFrame(HordeIrisShadows.active());
		HordeClientHooks.metrics();
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
		synchronizeRenderPath();
		parameterVisual = null;
		layerMapActive = false;
		layerSupported = true;
		layerReadbackBytes = 0;
		layerInputCount = 0;
		layerVisibleCount = 0;
		layerStatus = "disabled";
		HordeDebugState.beginDraw();
		drawn = 0;
		batches = 0;
		HordeDebugState.setGroups(0);
		uploadedBytes = 0;
		gpuCopiedBytes = 0;
		if (source.isEmpty() || HordePathVariant.layered() && HordeVisualConfig.LAYER_COUNT.get() == 0) {
			layerStatus = "hidden";
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
		HordeNearTerrain.prepare();
		updateAvoidance(prepared);
		var lastPrepared = prepared.getLast();
		int totalInstances = Math.addExact(lastPrepared.motionOffset(), lastPrepared.count());
		ensureMotion(totalInstances);
		if (HordePathVariant.facingCache() && irisPath()) {
			ensureFacing(totalInstances);
		}
		List<Prepared> detailed;
		long motionStarted = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.MOTION);
		try {
			prepareFrameMotion(prepared);
			detailed = selectFrameLod(prepared, totalInstances, modelView, depthTarget, reverse);
		} finally {
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.MOTION, motionStarted);
		}
		layerInputCount = totalInstances;
		layerVisibleCount = totalInstances;
		if (HordePathVariant.layered() && HordeVisualConfig.LAYER_COUNT.get() > 0
				&& HordeVisualConfig.LAYER_COUNT.get() < totalInstances) {
			var selection = applyLayers(prepared, detailed, totalInstances, modelView, depthTarget, reverse);
			prepared = selection.cards();
			detailed = selection.details();
		}
		if (prepared.isEmpty() && detailed.isEmpty()) {
			return;
		}
		int groupCount = 0;
		for (int first = 0; first < prepared.size(); ) {
			int last = first;
			int instances = 0;
			while (last < prepared.size() && prepared.get(last).count() <= INSTANCES_PER_DRAW - instances) {
				instances += prepared.get(last++).count();
			}
			var group = prepared.subList(first, last);
			drawGroup(group, modelView, reverse, colorTarget, depthTarget);
			groupCount++;
			first = last;
		}
		if (!detailed.isEmpty()) {
			drawGroup(detailed, modelView, reverse, colorTarget, depthTarget);
		}
		HordeDebugState.setGroups(groupCount);
		HordeDebugState.drawn(drawn);

	}

	private static void prepareFrameMotion(List<Prepared> prepared) {
		for (int first = 0; first < prepared.size(); ) {
			int last = Math.min(prepared.size(), first + 32);
			try {
				prepareLight(prepared.subList(first, last));
			} catch (IllegalArgumentException failure) {
				last = first + 1;
				prepareLight(prepared.subList(first, last));
			}
			var group = prepared.subList(first, last);
			int alignment = RenderSystem.getDevice().getUniformOffsetAlignment();
			int stride = Math.ceilDiv(PARAMETER_BYTES, alignment) * alignment;
			int bytes = group.size() * stride;
			ensureParameters(bytes);
			upload.clear().limit(bytes);
			for (int i = 0; i < group.size(); i++) {
				var item = group.get(i);
				upload.position(i * stride);
				writeParameters(item, item.motionOffset(), 0);
			}
			upload.position(0).limit(bytes);
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(0, bytes), upload);
			uploadedBytes += bytes;
			drawMotion(group, stride);
			first = last;
		}
		if (HordePathVariant.facingCache() && irisPath()) {
			prepareFacing(prepared);
		}
	}

	private static Selection applyLayers(List<Prepared> cards, List<Prepared> details, int instances,
	                                     Matrix4fc modelView, GpuTextureView depth, boolean reverse) {
		var all = new ArrayList<Prepared>(cards.size() + details.size());
		all.addAll(cards);
		all.addAll(details);
		int alignment = RenderSystem.getDevice().getUniformOffsetAlignment();
		int stride = Math.ceilDiv(PARAMETER_BYTES, alignment) * alignment;
		int bytes = all.size() * stride;
		ensureParameters(bytes);
		upload.clear().limit(bytes);
		for (int i = 0; i < all.size(); i++) {
			upload.position(i * stride);
			writeParameters(all.get(i), all.get(i).motionOffset(), 0);
		}
		upload.position(0).limit(bytes);
		var device = RenderSystem.getDevice();
		device.createCommandEncoder().writeToBuffer(parameters.slice(0, bytes), upload);
		uploadedBytes += bytes;
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1), new Vector3f(), new Matrix4f());
		var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		int max = all.stream().mapToInt(item -> item.verticesPerInstance() / 4 * 6).max().orElse(0);
		var indexBuffer = indices.getBuffer(max);
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		var draws = new ArrayList<HordeLayerCulling.Draw>(all.size());
		var center = all.getFirst().geometry().layerCenter();
		float radius = 0;
		for (int i = 0; i < all.size(); i++) {
			var item = all.get(i);
			radius = Math.max(radius, item.geometry().layerRadius() + (float) item.geometry().layerCenter().distanceTo(center));
			var slice = parameters.slice((long) i * stride, PARAMETER_BYTES);
			boolean card = item.geometry().model.tier() == 3;
			var pipeline = card ? (reverse ? PEEL_CARD_REVERSE : PEEL_CARD) : (reverse ? PEEL_MODEL_REVERSE : PEEL_MODEL);
			draws.add(new HordeLayerCulling.Draw(pipeline, pass -> {
				pass.setUniform("DynamicTransforms", transforms);
				pass.setUniform("HordeParameters", slice);
				pass.bindTexture("Sampler0", item.geometry().base.getTextureView(), item.geometry().base.getSampler());
				pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(), linear);
				pass.bindTexture("HordeMotion", motionView, nearest);
				pass.bindTexture("HordeAnimation", item.geometry().animation.texture().getTextureView(), nearest);
				pass.bindTexture("HordeCardFacing", facingView != null ? facingView : motionView, nearest);
				pass.bindTexture("HordeLodSelected", LOD.selected(), nearest);
				pass.bindTexture("HordeLodCompact", LOD.compact(), nearest);
				pass.setVertexBuffer(0, item.geometry().vertices());
				pass.setIndexBuffer(indexBuffer, indices.type());
				pass.drawIndexed(0, 0, item.verticesPerInstance() / 4 * 6, item.count());
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
			}));
		}
		long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.LAYERS, instances);
		HordeLayerCulling.Result result;
		try {
			result = LAYERS.select(new HordeLayerCulling.Input(HordeVisualConfig.LAYER_COUNT.get(), instances,
					depth.getWidth(0), depth.getHeight(0), reverse, depth, motionView, modelView,
					new Vector3f((float) center.x, (float) center.y, (float) center.z), Math.nextUp(radius + 0.001f),
					512L * 1024 * 1024, draws, RenderSystem::bindDefaultUniforms));
		} finally {
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.LAYERS, started);
		}
		layerStatus = result.status();
		layerSupported = result.supported();
		layerReadbackBytes = result.readbackBytes();
		if (!result.supported()) {
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.DEGRADED, 1);
			return new Selection(cards, details);
		}
		int[] ids = result.visibleIDs();
		layerVisibleCount = ids.length;
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.LAYER_INPUT, instances);
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.LAYER_VISIBLE, ids.length);
		HordeRenderMetrics.add(HordeRenderMetrics.Counter.LAYER_READBACK_BYTES, layerReadbackBytes);
		visibleIDs = updateWordsTexture(visibleIDs, "Horde compact visible IDs", ids);
		visibleFlags = LAYERS.flagsView();
		var compactCards = new ArrayList<Prepared>();
		for (var item : cards) {
			int first = lowerBound(ids, item.motionOffset());
			int end = lowerBound(ids, item.motionOffset() + item.count());
			if (first < end) {
				compactCards.add(new Prepared(item.batch(), item.geometry(), item.event(), item.offset(), end - first,
						item.verticesPerInstance(), item.motionOffset(), -1, first));
			}
		}
		var compactDetails = compactLayerDetails(details, ids);
		layerMapActive = true;
		return new Selection(compactCards, compactDetails);
	}

	private static HordeDataTexture updateWordsTexture(HordeDataTexture current, String label, int[] data) {
		if (current == null || (long) current.texture().getPixels().getWidth() * current.texture().getPixels().getHeight() < data.length) {
			// 可见数量逐帧增长时按容量扩展，避免反复销毁并上传整张纹理。
			int capacity = Math.max(1024, Integer.highestOneBit(Math.max(1, data.length - 1)) << 1);
			var next = new HordeDataTexture(label, java.util.Arrays.copyOf(data, capacity));
			if (current != null) {
				current.close();
			}
			return next;
		}
		current.update(0, data, 0, data.length);
		return current;
	}

	private static int lowerBound(int[] ids, int key) {
		int low = 0, high = ids.length;
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (ids[middle] < key) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}
		return low;
	}

	private static List<Prepared> compactLayerDetails(List<Prepared> details, int[] visible) {
		int rows = LOD.compact().getHeight(0);
		long bytes = (long) rows * 128 * 4;
		if (compactReadback == null || compactReadback.size() < bytes) {
			if (compactReadback != null) {
				compactReadback.close();
			}
			compactReadback = RenderSystem.getDevice().createBuffer(() -> "Horde selected ID readback",
					GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, bytes);
		}
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.copyTextureToBuffer(LOD.compact().texture(), compactReadback, 0, () -> {}, 0, 0, 0, 128, rows);
		int[] ids = new int[rows * 128];
		Arrays.fill(ids, -1);
		int[] counts = new int[rows];
		try (var map = encoder.mapBuffer(compactReadback, true, false)) {
			var data = map.data();
			for (int material = 0; material < rows; material++) {
				for (int i = 0; i < 128; i++) {
					int at = (material * 128 + i) * 4;
					int id = (data.get(at) & 255) | ((data.get(at + 1) & 255) << 8)
							| ((data.get(at + 2) & 255) << 16) | ((data.get(at + 3) & 255) << 24);
					if (id >= 0 && Arrays.binarySearch(visible, id) >= 0) {
						ids[material * 128 + counts[material]++] = id;
					}
				}
			}
		}
		layerReadbackBytes += bytes;
		HordeRenderMetrics.add(HordeRenderMetrics.Counter.LAYER_READBACK_BYTES, bytes);
		layerCompact = updateWordsTexture(layerCompact, "Horde layer selected IDs", ids);
		var result = new ArrayList<Prepared>();
		var seen = new BitSet();
		for (var item : details) {
			int material = item.material();
			if (seen.get(material)) {
				continue;
			}
			seen.set(material);
			int limit = irisPath() ? IRIS.instanceLimit(item.verticesPerInstance()) : 128;
			for (int slot = 0; slot < counts[material]; ) {
				int count = Math.min(limit, counts[material] - slot);
				result.add(new Prepared(item.batch(), item.geometry(), item.event(), 0, count, item.verticesPerInstance(), slot, material));
				slot += count;
			}
		}
		return result;
	}

	private static GpuTextureView compactView() {
		return layerMapActive ? layerCompact.texture().getTextureView()
				: HordePathVariant.detailLod() ? LOD.compact() : motionView;
	}

	private static void prepareLayerDraw(List<Prepared> items, int stride) {
		int required = items.size() * stride;
		if (layerDrawParameters == null || layerDrawParameters.size() < required) {
			if (layerDrawParameters != null) {
				layerDrawParameters.close();
				MemoryUtil.memFree(layerDrawUpload);
			}
			layerDrawParameters = RenderSystem.getDevice().createBuffer(() -> "Horde layer draw offsets",
					GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, required);
			layerDrawUpload = MemoryUtil.memAlloc(required);
		}
		layerDrawUpload.clear().limit(required);
		for (int i = 0; i < items.size(); i++) {
			layerDrawUpload.position(i * stride);
			layerDrawUpload.putInt(Math.max(0, items.get(i).visibilityOffset())).putInt(0).putInt(0).putInt(0);
		}
		layerDrawUpload.position(0).limit(required);
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(layerDrawParameters.slice(0, required), layerDrawUpload);
		uploadedBytes += required;
	}

	private static void bindLayerDraw(RenderPass pass, int index, int stride) {
		pass.setUniform("HordeLayerDraw", layerDrawParameters.slice((long) index * stride, 16));
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		pass.bindTexture("HordeVisibleIDs", visibleIDs.texture().getTextureView(), nearest);
		pass.bindTexture("HordeVisibleFlags", visibleFlags, nearest);
	}

	public static boolean layerSupported() {
		return layerSupported;
	}

	public static String layerStatus() {
		return layerStatus;
	}

	public static int layerVisibleCount() {
		return layerVisibleCount;
	}

	private static List<Prepared> selectFrameLod(List<Prepared> prepared, int instances, Matrix4fc modelView,
	                                             GpuTextureView depth, boolean reverse) {
		if (!HordePathVariant.detailLod()) {
			return List.of();
		}
		var detailed = new ArrayList<Prepared>();
		var materials = new LinkedHashMap<GeometryKey, Integer>();
		float visibilityRadius = 0;
		int[] ranges = new int[prepared.size() * 3];
		for (int i = 0; i < prepared.size(); i++) {
			var item = prepared.get(i);
			var key = meshKey(item.batch().event().visual, 0);
			var existingMaterial = materials.get(key);
			boolean firstMaterial = existingMaterial == null;
			int material = firstMaterial ? materials.size() : existingMaterial;
			if (firstMaterial) {
				materials.put(key, material);
			}
			ranges[i * 3] = item.motionOffset();
			ranges[i * 3 + 1] = item.motionOffset() + item.count();
			ranges[i * 3 + 2] = material;
			if (firstMaterial) {
				var geometry = GEOMETRY.get(key);
				visibilityRadius = Math.max(visibilityRadius, geometry.visibilityRadius());
				int vertices = verticesPerInstance(geometry);
				int limit = irisPath() ? IRIS.instanceLimit(vertices) : HordeLodSelection.CAPACITY;
				for (int slot = 0; slot < HordeLodSelection.CAPACITY; ) {
					int count = Math.min(limit, HordeLodSelection.CAPACITY - slot);
					detailed.add(new Prepared(item.batch(), geometry, item.event(), 0,
							count, vertices, slot, material));
					slot += count;
				}
			}
		}
		LOD.select(motionView, instances, materials.size(), ranges,
				modelView, visibilityRadius, depth, reverse, HordePathVariant.optimized());
		return detailed;
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
		HordeRenderMetrics.add(HordeRenderMetrics.Counter.GROUPS, 1);
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.LIGHT_SECTIONS, LIGHT.activeSections());
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.LIGHT_SLOTS, LIGHT.sections());
		int total = 0;
		for (var item : prepared) {
			total = Math.addExact(total, item.count());
		}
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
		int alignment = RenderSystem.getDevice().getUniformOffsetAlignment();
		int stride = Math.ceilDiv(PARAMETER_BYTES, alignment) * alignment;
		int required = Math.multiplyExact(prepared.size(), stride);
		ensureParameters(required);
		upload.clear().limit(required);
		for (int i = 0; i < prepared.size(); i++) {
			upload.position(i * stride);
			writeParameters(prepared.get(i), prepared.get(i).motionOffset(), iris ? layout.offsets()[i] : 0);
		}
		upload.position(0).limit(required);
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(0, required), upload);
		uploadedBytes += required;
		if (layerMapActive) {
			prepareLayerDraw(prepared, stride);
		}
		var transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1), new Vector3f(), new Matrix4f());
		var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		int maximum = prepared.stream().mapToInt(item -> item.verticesPerInstance() / 4 * 6).max().orElse(0);
		var indexBuffer = indices.getBuffer(maximum);
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		if (iris) {
			IRIS.draw(prepared, layout, stride, transforms, colorTarget, depthTarget);
			return;
		}

		long baseStarted = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.BASE, total);
		try {
			// 方向片单独使用透明裁剪与背面剔除管线。
			drawNativePass(prepared, layerMapActive ? (reverse ? CARD_REVERSE_LAYER : CARD_NORMAL_LAYER) : (reverse ? CARD_REVERSE : CARD_NORMAL), true,
					transforms, indexBuffer, indices.type(), colorTarget, depthTarget, linear, nearest, stride);
			drawNativePass(prepared, layerMapActive ? (reverse ? REVERSE_LAYER : NORMAL_LAYER) : (reverse ? REVERSE : NORMAL), false,
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
			HordeIrisShadows.setNativePipeline(pass, pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(), linear);
			pass.bindTexture("HordeMotion", motionView, nearest);
			pass.bindTexture("HordeCardFacing", HordePathVariant.optimized() && facingView != null ? facingView : motionView, nearest);
			pass.bindTexture("HordeLodSelected", HordePathVariant.detailLod() ? LOD.selected() : motionView, nearest);
			pass.bindTexture("HordeLodCompact", compactView(), nearest);
			pass.setIndexBuffer(indexBuffer, indexType);
			Geometry boundGeometry = null;
			for (int i = 0; i < prepared.size(); i++) {
				var item = prepared.get(i);
				if ((item.geometry().model.tier() == 3) != card) {
					continue;
				}
				pass.setUniform("HordeParameters", parameters.slice((long) i * stride, PARAMETER_BYTES));
				if (layerMapActive) {
					bindLayerDraw(pass, i, stride);
				}
				if (boundGeometry != item.geometry()) {
					boundGeometry = item.geometry();
					pass.bindTexture("Sampler0", boundGeometry.base.getTextureView(), boundGeometry.base.getSampler());
					pass.bindTexture("Glowmask", boundGeometry.glow.getTextureView(), boundGeometry.glow.getSampler());
					pass.bindTexture("HordeAnimation", boundGeometry.animation.texture().getTextureView(), nearest);
					pass.setVertexBuffer(0, boundGeometry.vertices);
				}
				pass.drawIndexed(0, 0, item.verticesPerInstance() / 4 * 6, item.count());
				if (item.material() == -1) {
					drawn += item.count();
				}
				batches++;
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
			}
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
		}
	}

	private static List<Prepared> prepare(List<HordeClient.Batch> source) {
		var prepared = new ArrayList<Prepared>();
		// 只在本次准备中复用成功的分割更新，重入或下一次准备仍重新更新。
		var splitUpdates = SPLIT_DEDUPLICATION
				? new IdentityHashMap<HordeClient.ClientHorde, SplitPreparation>()
				: null;
		for (var batch : source) {
			var event = batch.event();
			if (event.failed) {
				continue;
			}
			int tier = 3;
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
			if (GEOMETRY.get(meshKey(event.visual, 0)) == null
					|| GEOMETRY.get(meshKey(event.visual, 3)) == null
					|| irisPath() && (!IRIS_ANIMATIONS.containsKey(meshKey(event.visual, 0))
					|| !IRIS_ANIMATIONS.containsKey(meshKey(event.visual, 3)))) {
				requestResources(event.visual);
				continue;
			}
			if (geometry == null || irisPath() && !IRIS_ANIMATIONS.containsKey(key)) {
				requestResources(event.visual);
				continue;
			}
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.LOD_CARD_BLOCKS, 1);
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.LOD_CARD_INSTANCES, batch.count());
			var data = EVENTS.computeIfAbsent(event.id, _ -> new EventData(event));
			data.updateHeights(event);
			if (splitUpdates == null) {
				event.split.update(event, batch.time());
			} else {
				var splitUpdate = splitUpdates.get(event);
				if (splitUpdate == null || !splitUpdate.matches(event, batch.time())) {
					var nextSplitUpdate = new SplitPreparation(event, batch.time());
					event.split.update(event, batch.time());
					splitUpdates.put(event, nextSplitUpdate);
				}
			}

			data.terrain.update(event, data.heights, batch.time());
			data.avoidance.prepare(event, batch.time());
			int verticesPerInstance = verticesPerInstance(geometry);
			for (int offset = 0; offset < batch.count(); ) {
				int limit = irisPath() ? IRIS.instanceLimit(verticesPerInstance) : INSTANCES_PER_DRAW;
				int count = Math.min(limit, batch.count() - offset);
				prepared.add(new Prepared(batch, geometry, data, offset, count, verticesPerInstance, 0, -1));
				offset += count;
			}
		}
		prepared.sort(Comparator.comparing((Prepared item) -> item.batch().event().id)
				.thenComparingInt(item -> item.batch().block().index()).thenComparingInt(Prepared::offset));
		int motionOffset = 0;
		for (int i = 0; i < prepared.size(); i++) {
			var item = prepared.get(i);
			prepared.set(i, new Prepared(item.batch(), item.geometry(), item.event(), item.offset(),
					item.count(), item.verticesPerInstance(), motionOffset, -1));
			motionOffset += item.count();
		}
		return prepared;
	}

	/// 每个片实例输出四个顶点，源帧缓存十个观察方向。
	private static int verticesPerInstance(Geometry geometry) {
		return geometry.model.tier() == 3 ? 4 : geometry.model.vertexCount();
	}

	@SuppressWarnings("resource")
	private static void writeParameters(Prepared item, int motionOffset, int vertexOffset) {
		var batch = item.batch();
		var event = batch.event();
		var camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		if (parameterVisual != event.visual) {
			parameterVisual = event.visual;
			parameterRadius = visualRadius(event.visual);
		}
		long offset = batch.offset() + item.offset();
		long row = batch.firstRow() + offset / batch.block().columns();
		int columnOffset = (int) (offset % batch.block().columns());
		var split = event.split;
		HordeDebugState.addSplit(split);
		HordeRenderMetrics.set(HordeRenderMetrics.Counter.REGIONS, split.count());
		put((float) (event.origin.x - camera.x), (float) (event.origin.y - camera.y),
				(float) (event.origin.z - camera.z), (float) batch.time());
		put((float) camera.x, (float) camera.y, (float) camera.z, (float) event.speed);
		put((float) event.rowSpacing(), (float) event.columnSpacing(), event.columns, (float) event.length);
		put((float) (batch.time() * event.speed - row * event.rowSpacing()), batch.block().firstColumn(), batch.block().columns(), columnOffset);
		put((float) batch.block().first(), (float) batch.block().last(), vertexOffset, split.count());
		put(item.geometry().model.vertexCount(), item.geometry().model.frames(), item.geometry().model.duration(), item.geometry().hasGlow ? 1 : 0);
		put(LIGHT.mask(), event.nodes.length, 1, event.entityHeight);
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
		put(SPLIT_STRENGTH, SPLIT_LEAD, SPLIT_MAXIMUM_OFFSET, parameterRadius);
		// 光照区段缺失时的回退亮度：露天（方块光0、天空光15），不伪造洞穴亮度也不隐藏单位。
		put(0, 15, item.material() + 1, 1);
		put((float) HordeVisualConfig.FRAME_DISTANCE.get(), HordeVisualConfig.ANIMATION_CYCLE.get() ? 1f : 0f,
				item.geometry().model.tier() == 3 ? (HordePathVariant.facingCache() && irisPath() ? 2f : 1f) : 0f, item.verticesPerInstance());
		long firstStateRow = item.event().avoidance.firstRow();
		upload.putInt(Math.toIntExact(row - firstStateRow)).putInt((int) firstStateRow)
				.putInt((int) (firstStateRow >>> 32)).putInt(item.event().avoidance.rows());
	}

	/// 迎潮侧提前分流的带宽（格）：随浪潮速度增大，低速时仍保留基础宽度。
	private static float splitBand(double speed) {
		return (float) HordeSplitState.band(speed);
	}

	public static int motionValues() {
		return MOTION_VALUES;
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
		DynamicTexture cardAtlas = null;
		DynamicTexture cardGlowAtlas = null;
		var uvData = new float[model.vertexCount() * 4];
		float radius = 0;
		float visibilityRadius = 0;
		for (int at = 0; at < model.frameVertices().length; at += 6) {
			radius = Math.max(radius, (float) Math.hypot(model.frameVertices()[at], model.frameVertices()[at + 2]));
			visibilityRadius = Math.max(visibilityRadius, (float) Math.sqrt(
					model.frameVertices()[at] * model.frameVertices()[at]
							+ model.frameVertices()[at + 1] * model.frameVertices()[at + 1]
							+ model.frameVertices()[at + 2] * model.frameVertices()[at + 2]));
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
			if (model.tier() == 3) {
				var source = HordeMeshCache.ready(visual, 0);
				NativeImage glowImage = null;
				try (var input = minecraft.getResourceManager().getResourceOrThrow(visual.texture()).open();
				     var image = NativeImage.read(input)) {
					if (hasGlow) {
						try (var glowInput = minecraft.getResourceManager().getResourceOrThrow(glowId).open()) {
							glowImage = NativeImage.read(glowInput);
						}
					}
					var atlas = HordeCardBake.bakeLayers(source, image, glowImage);
					if (atlas == null) {
						throw new IllegalStateException("群体片统一图集烘焙失败：" + visual.texture());
					}
					// 已交接图像由纹理拥有；构造失败时释放尚未交接的另一层。
					boolean baseTransferred = false;
					boolean glowTransferred = false;
					try {
						cardAtlas = new CardAtlasTexture(atlas.base());
						baseTransferred = true;
						if (atlas.glow() != null) {
							cardGlowAtlas = new CardAtlasTexture(atlas.glow());
							glowTransferred = true;
						}
					} finally {
						if (!baseTransferred) {
							atlas.base().close();
						}
						if (!glowTransferred && atlas.glow() != null) {
							atlas.glow().close();
						}
					}
					base = cardAtlas;
					glow = cardGlowAtlas != null ? cardGlowAtlas : cardAtlas;
					hasGlow = cardGlowAtlas != null;
				} catch (IOException failure) {
					throw new IllegalStateException("群体片材质读取失败：" + visual.texture(), failure);
				} finally {
					if (glowImage != null) {
						glowImage.close();
					}
				}
			}
			preparations++;
			var bounds = layerBounds(model);
			return new Geometry(buffer, vertices.length / HordeMeshCache.VERTEX_STRIDE / 4 * 6, animation, uvs, model, base, glow, hasGlow, radius, visibilityRadius, bounds.center(), bounds.radius(), cardAtlas, cardGlowAtlas);
		} catch (RuntimeException exception) {
			if (cardAtlas != null) {
				cardAtlas.close();
			}
			if (cardGlowAtlas != null) {
				cardGlowAtlas.close();
			}
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

	private static LayerBounds layerBounds(HordeMeshCache.Model model) {
		var values = model.frameVertices();
		double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
		double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
		for (int i = 0; i < values.length; i += 6) {
			minX = Math.min(minX, values[i]);
			minY = Math.min(minY, values[i + 1]);
			minZ = Math.min(minZ, values[i + 2]);
			maxX = Math.max(maxX, values[i]);
			maxY = Math.max(maxY, values[i + 1]);
			maxZ = Math.max(maxZ, values[i + 2]);
		}
		var center = model.tier() == 3 ? new Vec3((values[0] + values[12]) * 0.5,
				(values[1] + values[13]) * 0.5, (values[2] + values[14]) * 0.5)
				: new Vec3((minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
		double radius = 0;
		for (int i = 0; i < values.length; i += 6) {
			radius = Math.max(radius, Math.sqrt(Math.pow(values[i] - center.x, 2)
					+ Math.pow(values[i + 1] - center.y, 2) + Math.pow(values[i + 2] - center.z, 2)));
		}
		return new LayerBounds(center, Math.nextUp((float) radius));
	}

	private static void ensureMotion(int instances) {
		long pixels = Math.multiplyExact((long) instances, MOTION_VALUES);
		if (motion != null && (long) motionWidth * motionHeight >= pixels) {
			return;
		}
		var device = RenderSystem.getDevice();
		int limit = device.getMaxTextureSize();
		int alignment = HordeRenderPolicy.motionKernel() || HordePathVariant.facingKernel() ? 36 : 1;
		int maximum = limit / alignment * alignment;
		int width = Math.min(MOTION_ROW_WIDTH, maximum) / alignment * alignment;
		while (Math.ceilDiv(pixels, width) > limit && width < maximum) {
			width = (int) Math.min((long) width * 2, maximum);
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
		if (facing != null) {
			facingView.close();
			facing.close();
			facingView = null;
			facing = null;
		}
		if (motionView != null) {
			motionView.close();
			motion.close();
		}
		motion = replacement;
		motionView = view;
		motionWidth = width;
		motionHeight = height;
		if (motionOutput != null) {
			motionOutput.close();
		}
		try (var stack = MemoryStack.stackPush()) {
			var size = stack.malloc(16).putInt(width).putInt(height).putInt(0).putInt(0).flip();
			motionOutput = device.createBuffer(() -> "Horde motion output size", GpuBuffer.USAGE_UNIFORM, size);
		}
	}

	// EventData由EVENTS缓存拥有，remove/clear统一关闭，此处只借用。
	@SuppressWarnings("resource")
	private static void drawMotion(List<Prepared> prepared, int stride) {
		// 纹理构造包含上传命令，必须在打开RenderPass之前全部完成。
		var poses = new ArrayList<GpuTextureView>(prepared.size());
		for (var item : prepared) {
			poses.add(HordePathVariant.optimized()
					? HordePoseCache.view(item.batch().event().columns, item.geometry().model.animationFrames())
					: item.event().heights.texture().getTextureView());
		}

		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Lobecorp horde motion cache", motionView, OptionalInt.empty())) {
			HordeIrisShadows.setNativePipeline(pass, HordeRenderPolicy.motionKernel() ? MOTION_LINES : MOTION);
			if (HordeRenderPolicy.motionKernel()) {
				pass.setUniform("HordeLineOutput", motionOutput);
			}
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
			pass.setViewport(0, 0, motionWidth, motionHeight);
			pass.bindTexture("HordeLight", LIGHT.atlas().getTextureView(), nearest);
			pass.bindTexture("HordeLightIndex", LIGHT.lookup().getTextureView(), nearest);
			pass.bindTexture("HordeNearTerrain", HordeNearTerrain.view(), nearest);
			pass.setUniform("HordeNearTerrainParameters", HordeNearTerrain.parameters());
			EventData boundEvent = null;
			GpuTextureView boundPose = null;
			for (int i = 0; i < prepared.size(); i++) {
				var item = prepared.get(i);
				pass.setUniform("HordeParameters", parameters.slice((long) i * stride, PARAMETER_BYTES));
				if (boundEvent != item.event()) {
					boundEvent = item.event();
					pass.bindTexture("HordeRoute", boundEvent.route.texture().getTextureView(), nearest);
					pass.bindTexture("HordeHeights", boundEvent.heights.texture().getTextureView(), nearest);
					pass.bindTexture("HordeTerrain", boundEvent.terrain.view(), nearest);
					pass.bindTexture("HordeSplit", item.batch().event().split.view(), nearest);
					pass.bindTexture("HordeAvoidance", boundEvent.avoidance.view(), nearest);
				}
				if (boundPose != poses.get(i)) {
					boundPose = poses.get(i);
					pass.bindTexture("HordePose", boundPose, nearest);
				}
				int first = Math.multiplyExact(item.motionOffset(), MOTION_VALUES) / motionWidth;
				int last = Math.toIntExact(Math.ceilDiv((long) (item.motionOffset() + item.count()) * MOTION_VALUES, motionWidth));
				pass.enableScissor(0, first, motionWidth, last - first);
				if (HordeRenderPolicy.motionKernel()) {
					pass.draw(item.motionOffset() * 2, item.count() * 2);
				} else {
					pass.draw(0, 3);
				}
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
			}
			pass.disableScissor();
		}
	}

	private static void ensureFacing(int instances) {
		int rows = HordePathVariant.facingKernel() ? motionHeight
				: Math.ceilDiv(Math.multiplyExact(instances, HordeCardFacing.WORDS_PER_INSTANCE), motionWidth);
		if (facing != null && facing.getWidth(0) == motionWidth && facing.getHeight(0) >= rows) {
			return;
		}
		if (facing != null) {
			facingView.close();
			facing.close();
		}
		facing = RenderSystem.getDevice().createTexture(() -> "Horde per-view card basis",
				GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
				TextureFormat.RGBA8, motionWidth, Math.max(1, rows), 1, 1);
		facingView = RenderSystem.getDevice().createTextureView(facing);
	}

	/// 面向缓存只依赖运动结果与模型中心，连续同模型区间可跨光照组一起生成。
	private static void prepareFacing(List<Prepared> prepared) {
		int alignment = RenderSystem.getDevice().getUniformOffsetAlignment();
		int stride = Math.ceilDiv(PARAMETER_BYTES, alignment) * alignment;
		int runs = 0;
		for (int first = 0; first < prepared.size(); first = facingRunEnd(prepared, first)) {
			runs++;
		}
		int bytes = Math.multiplyExact(runs, stride);
		ensureParameters(bytes);
		upload.clear().limit(bytes);
		int index = 0;
		for (int first = 0; first < prepared.size(); ) {
			int last = facingRunEnd(prepared, first);
			var item = prepared.get(first);
			var tail = prepared.get(last - 1);
			upload.position(index * stride);
			writeParameters(item, item.motionOffset(), 0);
			// MotionInfo.y 为本次连续区间数量，其余字段保持模型首批的参数。
			upload.putInt(index * stride + 164, tail.motionOffset() + tail.count() - item.motionOffset());
			index++;
			first = last;
		}
		upload.position(0).limit(bytes);
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(parameters.slice(0, bytes), upload);
		uploadedBytes += bytes;
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Horde per-instance card facing", facingView, OptionalInt.empty())) {
			HordeIrisShadows.setNativePipeline(pass, HordePathVariant.facingKernel() ? FACING_LINES : FACING);
			pass.setViewport(0, 0, motionWidth, facing.getHeight(0));
			pass.bindTexture("HordeMotion", motionView, nearest);
			index = 0;
			for (int first = 0; first < prepared.size(); ) {
				int last = facingRunEnd(prepared, first);
				var item = prepared.get(first);
				if (item.geometry().model.tier() == 3) {
					var tail = prepared.get(last - 1);
					pass.setUniform("HordeParameters", parameters.slice((long) index * stride, PARAMETER_BYTES));
					pass.bindTexture("HordeAnimation", item.geometry().animation.texture().getTextureView(), nearest);
					int top = item.motionOffset() * HordeCardFacing.WORDS_PER_INSTANCE / motionWidth;
					int bottom = Math.ceilDiv((tail.motionOffset() + tail.count()) * HordeCardFacing.WORDS_PER_INSTANCE, motionWidth);
					pass.enableScissor(0, top, motionWidth, bottom - top);
					if (HordePathVariant.facingKernel()) {
						pass.draw(item.motionOffset() * 2, (tail.motionOffset() + tail.count() - item.motionOffset()) * 2);
					} else {
						pass.draw(0, 3);
					}
					HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
				}
				index++;
				first = last;
			}
			pass.disableScissor();
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
		}
	}

	private static int facingRunEnd(List<Prepared> prepared, int first) {
		var geometry = prepared.get(first).geometry();
		int last = first + 1;
		while (last < prepared.size() && prepared.get(last).geometry() == geometry) {
			last++;
		}
		return last;
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

	public static void clearModels() {
		HordeClientHooks.releaseResources();
		HordeGroundFog.clear();
		HordeNearTerrain.clear();
		LOD.close();
		LAYERS.close();
		if (layerCompact != null) {
			layerCompact.close();
			layerCompact = null;
		}
		if (compactReadback != null) {
			compactReadback.close();
			compactReadback = null;
		}
		if (visibleIDs != null) {
			visibleIDs.close();
			visibleIDs = null;
		}
		if (layerDrawParameters != null) {
			layerDrawParameters.close();
			layerDrawParameters = null;
		}
		if (layerDrawUpload != null) {
			MemoryUtil.memFree(layerDrawUpload);
			layerDrawUpload = null;
		}
		if (motionOutput != null) {
			motionOutput.close();
			motionOutput = null;
		}
		layerMapActive = false;
		HordeParticleSamples.invalidate();
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
		HordePoseCache.clear();
		MODEL_KEYS.clear();
		PENDING_VISUALS.clear();
		resetPipelineWarmup(false);
		VISUAL_RETRIES.clear();
		IRIS_ANIMATIONS.values().forEach(HordeIrisAnimation::close);
		IRIS_ANIMATIONS.clear();
	}

	public static void clear() {
		HordeNearTerrain.clear();
		HordeGroundFog.clear();
		HordeParticleSamples.invalidate();
		EVENTS.values().forEach(EventData::close);
		EVENTS.clear();
		LIGHT.close();
		IRIS.close();
		LOD.close();
		LAYERS.close();
		if (layerCompact != null) {
			layerCompact.close();
			layerCompact = null;
		}
		if (compactReadback != null) {
			compactReadback.close();
			compactReadback = null;
		}
		if (visibleIDs != null) {
			visibleIDs.close();
			visibleIDs = null;
		}
		if (layerDrawParameters != null) {
			layerDrawParameters.close();
			layerDrawParameters = null;
		}
		if (layerDrawUpload != null) {
			MemoryUtil.memFree(layerDrawUpload);
			layerDrawUpload = null;
		}
		if (motionOutput != null) {
			motionOutput.close();
			motionOutput = null;
		}
		layerMapActive = false;
		if (facing != null) {
			facingView.close();
			facing.close();
			facingView = null;
			facing = null;
		}
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
		result.addProperty("lod_enabled", true);
		result.addProperty("lod_high_capacity", HordeLodSelection.CAPACITY);
		result.addProperty("card_distance", HordeLodSelection.MAX_DISTANCE);
		result.addProperty("lod_distance_step", HordeLodSelection.DISTANCE_STEP);
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
		return "variant=" + HordePathVariant.current() + ", GPU instances=" + drawn + ", batches=" + batches + ", upload=" + uploadedBytes
				+ "B, gpuCopy=" + gpuCopiedBytes + "B, lightSections=" + LIGHT.sections() + ", modelBuilds=" + preparations
				+ ", irisMiWords=" + IRIS.budgetWords / (1024 * 1024) + ", irisSlots=" + IRIS.slotCount()
				+ ", degraded=" + (HordeIrisShadows.active() && HordeIrisShadows.entityVertexWords() == 0 ? "unsupported_layout" : IRIS.degradation) + ", shadows=0";
	}

	private record PreparationRetry(int failures, long atNanos) {
	}

	private record SplitPreparation(double time, Vec3 origin,
	                                Map<String, HordeClient.RegionBox> regions) {
		private SplitPreparation(HordeClient.ClientHorde event, double time) {
			this(time, event.origin, Map.copyOf(event.regions));
		}

		private boolean matches(HordeClient.ClientHorde event, double nextTime) {
			return time == nextTime && origin.equals(event.origin) && regions.equals(event.regions);
		}
	}

	private record Selection(List<Prepared> cards, List<Prepared> details) { }

	private record LayerBounds(Vec3 center, float radius) { }

	private record GeometryKey(HordeVisualDefinition visual, int tier, int settings) {
	}

	/// 片图集用夹边最近邻采样，避免远距离mipmap把相邻方向/帧tile混成噪点。
	private static final class CardAtlasTexture extends DynamicTexture {
		private CardAtlasTexture(NativeImage image) {
			super(() -> "Horde card atlas", image.getWidth(), image.getHeight(), true);
			try {
				setPixels(image);
				this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
				upload();
			} catch (RuntimeException | Error failure) {
				close();
				throw failure;
			}
		}
	}

	private record Geometry(GpuBuffer vertices, int indices, HordeDataTexture animation, HordeDataTexture uvs,
	                        HordeMeshCache.Model model,
	                        AbstractTexture base, AbstractTexture glow, boolean hasGlow, float radius,
	                        float visibilityRadius, Vec3 layerCenter, float layerRadius,
	                        @Nullable DynamicTexture cardAtlas, @Nullable DynamicTexture cardGlowAtlas) {
	}

	private record Prepared(HordeClient.Batch batch, Geometry geometry, EventData event, int offset, int count,
	                        int verticesPerInstance, int motionOffset, int material, int visibilityOffset) {
		private Prepared(HordeClient.Batch batch, Geometry geometry, EventData event, int offset, int count,
		                 int verticesPerInstance, int motionOffset, int material) {
			this(batch, geometry, event, offset, count, verticesPerInstance, motionOffset, material, -1);
		}
	}

	/// GPU展开原版实体顶点，再交给Iris已有材质管线；不映射或读取回CPU。
	private static class IrisVertices implements AutoCloseable {
		// 单槽纹理与VBO共128MiB，双槽上限256MiB；仅限制工作集，不减少单位。
		private static final int MAX_WORDS = 16 * 1024 * 1024;
		private static final int MIN_WORDS = 4 * 1024 * 1024;
		private static final long RETRY_NANOS = 5_000_000_000L;
		private static final RenderPipeline BAKE = bakePipeline(false);
		private static final RenderPipeline BAKE_LAYER = bakePipeline(true);

		private static RenderPipeline bakePipeline(boolean layered) {
			var builder = shaderFlags(RenderPipeline.builder(), layered)
					.withLocation(Lobecorp.id("pipeline/horde_iris_vertices" + (layered ? "_layer" : "")))
					.withVertexShader(Lobecorp.id(HordePathVariant.irisKernel() ? "core/horde_iris_vertices_line" : "core/horde_motion"))
					.withFragmentShader(Lobecorp.id(HordePathVariant.irisKernel() ? "core/horde_iris_vertices_line" : "core/horde_iris_vertices"))
					.withSampler("HordeMotion").withSampler("HordeIrisAnimation").withSampler("HordeUVs")
					.withSampler("HordeLodSelected").withSampler("HordeLodCompact").withSampler("HordeCardFacing")
					.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER);
			if (HordePathVariant.irisKernel()) {
				builder.withUniform("HordeIrisOutput", UniformType.UNIFORM_BUFFER);
			}
			return builder.withVertexFormat(DefaultVertexFormat.EMPTY,
					HordePathVariant.irisKernel() ? VertexFormat.Mode.DEBUG_LINES : VertexFormat.Mode.TRIANGLES)
					.withCull(false).withColorTargetState(ColorTargetState.DEFAULT).build();
		}

		private static RenderPipeline bake(boolean layered) {
			return layered ? BAKE_LAYER : BAKE;
		}

		private final Slot[] slots = {new Slot(), new Slot()};
		private int wordsPerVertex = 9;
		private int configuredWords;
		private int budgetWords = MAX_WORDS;
		private long recoveryAt;
		private long disabledUntil;
		private String degradation = "";

		private static int rowWidth() {
			int width = Integer.highestOneBit(Math.min(4096, RenderSystem.getDevice().getMaxTextureSize()));
			int words = Math.max(1, HordeIrisShadows.entityVertexWords());
			return HordePathVariant.irisKernel() ? width / words * words : width;
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
				HordeIrisShadows.setNativePipeline(pass, bake(layerMapActive));
				if (HordePathVariant.irisKernel()) {
					pass.setUniform("HordeIrisOutput", slot.outputSize);
				}
				pass.setViewport(0, 0, slot.width, slot.height);
				pass.bindTexture("HordeMotion", motionView, nearest);
			pass.bindTexture("HordeCardFacing", HordePathVariant.optimized() && facingView != null ? facingView : motionView, nearest);
				pass.bindTexture("HordeLodSelected", HordePathVariant.detailLod() ? LOD.selected() : motionView, nearest);
				pass.bindTexture("HordeLodCompact", compactView(), nearest);
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
				for (int i = first; i < last; i++) {
					var item = items.get(i);
					pass.setUniform("HordeParameters", parameters.slice((long) i * stride, PARAMETER_BYTES));
				if (layerMapActive) {
					bindLayerDraw(pass, i, stride);
				}
					pass.bindTexture("HordeIrisAnimation", IRIS_ANIMATIONS.get(meshKey(item.batch().event().visual, item.geometry().model.tier())).view(), nearest);
					pass.bindTexture("HordeUVs", item.geometry().uvs.texture().getTextureView(), nearest);
					int low = Math.multiplyExact(offsets[i], wordsPerVertex) / slot.width;
					int high = Math.ceilDiv(Math.multiplyExact(offsets[i]
							+ Math.multiplyExact(item.count(), item.verticesPerInstance()), wordsPerVertex), slot.width);
					pass.enableScissor(0, low, slot.width, high - low);
					if (HordePathVariant.irisKernel()) {
						pass.draw(offsets[i] * 2, item.count() * item.verticesPerInstance() * 2);
					} else {
						pass.draw(0, 3);
					}
					HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
					if (item.material() == -1) {
						drawn += item.count();
					}
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
			private GpuBuffer outputSize;
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
					try (var stack = MemoryStack.stackPush()) {
						var size = stack.malloc(16).putInt(targetWidth).putInt(targetHeight).putInt(0).putInt(0).flip();
						outputSize = device.createBuffer(() -> "Horde Iris output size", GpuBuffer.USAGE_UNIFORM, size);
					}
					width = targetWidth;
					height = targetHeight;
				} catch (RuntimeException | OutOfMemoryError failure) {
					close();
					throw failure;
				}
			}

			@Override
			public void close() {
				if (outputSize != null) {
					outputSize.close();
					outputSize = null;
				}
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
		private final float[] packedRoute;
		private final int[] packedHeights;
		private final HordeTerrainState terrain = new HordeTerrainState();
		private final HordeAvoidanceState avoidance = new HordeAvoidanceState();

		private EventData(HordeClient.ClientHorde event) {
			packedRoute = HordeGpuPacking.route(event.routeData, event.nodes.length);
			packedHeights = new int[Math.ceilDiv(event.heightData.length, 2)];
			HordeGpuPacking.heights(event.heightData, packedHeights, 0, event.heightData.length);
			route = new HordeDataTexture("Horde fixed route " + event.id, packedRoute);
			try {
				heights = new HordeDataTexture("Horde ground heights " + event.id, packedHeights);
			} catch (RuntimeException exception) {
				route.close();
				throw exception;
			}
		}

		private void updateHeights(HordeClient.ClientHorde event) {
			for (int first = event.changedRoute.nextSetBit(0); first >= 0; ) {
				int last = event.changedRoute.nextClearBit(first);
				HordeGpuPacking.copyRoute(event.routeData, packedRoute, first, last);
				int at = first * HordeGpuPacking.ROUTE_WORDS;
				route.update(at, packedRoute, at, (last - first) * HordeGpuPacking.ROUTE_WORDS);
				first = event.changedRoute.nextSetBit(last);
			}
			event.changedRoute.clear();
			if (event.lookupChanged) {
				int first = event.nodes.length * HordeGpuPacking.ROUTE_WORDS;
				System.arraycopy(event.routeData, event.nodes.length * HordeGpuPacking.SOURCE_ROUTE_WORDS,
						packedRoute, first, packedRoute.length - first);
				route.update(first, packedRoute, first, packedRoute.length - first);
				event.lookupChanged = false;
			}

			for (int first = event.changedHeights.nextSetBit(0); first >= 0; ) {
				int last = event.changedHeights.nextClearBit(first);
				int firstSample = first * event.heightColumns;
				int lastSample = last * event.heightColumns;
				HordeGpuPacking.heights(event.heightData, packedHeights, firstSample, lastSample);
				int firstWord = firstSample / 2;
				heights.update(firstWord, packedHeights, firstWord, Math.ceilDiv(lastSample, 2) - firstWord);
				first = event.changedHeights.nextSetBit(last);
			}
			event.changedHeights.clear();
		}


		@Override
		public void close() {
			route.close();
			heights.close();
			terrain.close();
			avoidance.close();
		}
	}
}
