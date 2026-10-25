package org.unitego.lobecorp.client.horde;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.config.client.HordeVisualConfig;
import org.unitego.lobecorp.util.horde.HordeSamplingUtil;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.nio.ByteBuffer;
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
	private static final int MAX_RETRY_SECONDS = 5;
	private static int resourceFailures;
	private static long retryAtNanos;
    private static final int MOTION_ROW_WIDTH = 1024;
    private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION).add("UV0", VertexFormatElement.UV0)
            .add("Color", VertexFormatElement.COLOR).add("Normal", VertexFormatElement.NORMAL).padding(1).build();
    private static final RenderPipeline NORMAL = pipeline(false);
    private static final RenderPipeline REVERSE = pipeline(true);
    private static final RenderPipeline MOTION = RenderPipeline.builder()
            .withLocation(Lobecorp.id("pipeline/horde_motion"))
            .withVertexShader(Lobecorp.id("core/horde_motion")).withFragmentShader(Lobecorp.id("core/horde_motion"))
		    .withSampler("HordeRoute").withSampler("HordeHeights").withSampler("HordeRegions").withSampler("HordeRegionState")
		    .withSampler("HordeLight").withSampler("HordeLightIndex").withSampler("HordeTerrain")
		    .withSampler("HordeSpatial32").withSampler("HordeSpatial16").withSampler("HordeSpatial8")
		    .withSampler("HordeUnitCurrent").withSampler("HordeUnitIndex")
            .withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES).withCull(false)
            .withColorTargetState(ColorTargetState.DEFAULT).build();
	private static final RenderPipeline UNIT_UPDATE = RenderPipeline.builder()
			.withLocation(Lobecorp.id("pipeline/horde_unit_update"))
			.withVertexShader(Lobecorp.id("core/horde_unit")).withFragmentShader(Lobecorp.id("core/horde_unit"))
			.withSampler("HordeRoute").withSampler("HordeHeights").withSampler("HordeRegions").withSampler("HordeRegionState")
			.withSampler("HordeTerrain").withSampler("HordeSpatial32").withSampler("HordeSpatial16").withSampler("HordeSpatial8")
			.withSampler("HordeUnitPrevious").withSampler("HordeUnitIndex")
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
	private static final int PIPELINE_COUNT = 10;
	private static final int[] NATIVE_PIPELINE_ORDER = {2, 3, 4, 8, 0, 6, 7, 1, 5, 9};
	private static final int[] IRIS_PIPELINE_ORDER = {2, 3, 4, 8, 0, 1, 5, 6, 7, 9};
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

	private static RenderPipeline pipeline(boolean reverse) {
		return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
				.withLocation(Lobecorp.id(reverse ? "pipeline/horde_gpu_reverse" : "pipeline/horde_gpu"))
				.withVertexShader(Lobecorp.id("core/horde_instanced")).withFragmentShader(Lobecorp.id("core/horde_instanced"))
				.withSampler("Sampler0").withSampler("Sampler2").withSampler("Glowmask")
				.withSampler("HordeMotion").withSampler("HordeAnimation")
				.withUniform("HordeParameters", UniformType.UNIFORM_BUFFER)
				.withVertexFormat(FORMAT, VertexFormat.Mode.QUADS).withCull(false)
				.withDepthStencilState(new DepthStencilState(reverse ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.LESS_THAN_OR_EQUAL, true))
				.withColorTargetState(ColorTargetState.DEFAULT).build();
	}

	private static boolean irisPath() {
		return HordeIrisShadows.active() && HordeIrisShadows.entityVertexWords() > 0 && IRIS.available();
	}

	private static GeometryKey meshKey(HordeVisualDefinition visual, int tier) {
		int settings = HordeMeshCache.settingsKey();
		var keys = MODEL_KEYS.get(visual);
		if (keys == null || keys[0].settings() != settings) {
			keys = new GeometryKey[]{new GeometryKey(visual, 0, settings),
					new GeometryKey(visual, 1, settings), new GeometryKey(visual, 2, settings)};
			MODEL_KEYS.put(visual, keys);
		}
		return keys[tier];
	}

	private static float visualRadius(HordeVisualDefinition visual) {
		float radius = 0;
		for (int tier = 0; tier < 3; tier++) {
			var model = GEOMETRY.get(meshKey(visual, tier));
			if (model != null) {
				radius = Math.max(radius, model.radius());
			}
		}
		return radius;
	}

	public static void requestResources(HordeVisualDefinition visual) {
		HordeMeshCache.request(visual);
		int tiers = HordeVisualConfig.LOD_ENABLED.get() ? 3 : 1;
		for (int tier = 0; tier < tiers; tier++) {
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
		double near = HordeVisualConfig.LOD_NEAR.get();
		double far = Math.max(near + 1, HordeVisualConfig.LOD_FAR.get());
		double band = Math.min(2, near * 0.1);
		double distance = batch.block().center().distanceTo(Minecraft.getInstance().gameRenderer.getMainCamera().position());
		var states = LOD_STATES.computeIfAbsent(batch.event().id, _ -> new HashMap<>());
		int initial = distance <= near ? 0 : distance <= far ? 1 : 2;
		int previous = states.getOrDefault(batch.block().index(), initial);
		int tier = switch (previous) {
			case 0 -> distance > far + band ? 2 : distance > near + band ? 1 : 0;
			case 1 -> distance < near - band ? 0 : distance > far + band ? 2 : 1;
			default -> distance < near - band ? 0 : distance < far - band ? 1 : 2;
		};
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
					case 2 -> HordeRegionState.precompile();
					case 3 -> HordeTerrainState.precompile();
					case 4 -> HordeSpatialIndex.precompile();
					case 5 -> HordeIrisAnimation.precompile();
					case 6 -> device.precompilePipeline(NORMAL).isValid();
					case 7 -> device.precompilePipeline(REVERSE).isValid();
					case 8 -> device.precompilePipeline(UNIT_UPDATE).isValid();
					case 9 -> HordeUnitStats.precompile();
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
		prepareUnitHistories(prepared);
		for (int first = 0; first < prepared.size(); ) {
			int last = first;
			int instances = 0;
			while (last < prepared.size() && prepared.get(last).count() <= INSTANCES_PER_DRAW - instances) {
				instances += prepared.get(last++).count();
			}
			drawGroup(prepared.subList(first, last), modelView, reverse, colorTarget, depthTarget);
			first = last;
		}
		if (HordeRenderMetrics.collecting() || HordeVisualConfig.STATISTICS.get()) {
			var seen = new HashSet<EventData>();
			for (var item : prepared) {
				if (seen.add(item.event())) {
					item.event().unitStats.capture(item.event().units,
							PhotonFrameCapture.renderFrameId(), drawSerial);
				}
			}
		}
	}

	private static void prepareUnitHistories(List<Prepared> prepared) {
		var ranges = new LinkedHashMap<EventData, List<HordeUnitState.Range>>();
		for (var item : prepared) {
			ranges.computeIfAbsent(item.event(), _ -> new ArrayList<>()).add(
					new HordeUnitState.Range(item.batch(), item.offset(), item.count()));
		}
		long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.UNITS, prepared.size());
		try {
			for (var entry : ranges.entrySet()) {
				entry.getKey().units.prepare(entry.getValue(), drawSerial);
			}
		} finally {
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.UNITS, started);
		}
	}

	private static void drawUnitHistories(List<Prepared> prepared, int stride) {
		var groups = new LinkedHashMap<EventData, List<Integer>>();
		for (int index = 0; index < prepared.size(); index++) {
			groups.computeIfAbsent(prepared.get(index).event(), _ -> new ArrayList<>()).add(index);
		}
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		for (var entry : groups.entrySet()) {
			var data = entry.getKey();
			var state = data.units;
			long started = HordeRenderMetrics.begin(HordeRenderMetrics.Stage.UNITS, entry.getValue().size());
			try {
				long bytes = state.beginFrame(drawSerial);
				if (bytes > 0) {
					HordeRenderMetrics.add(HordeRenderMetrics.Counter.HISTORY_COPIES, 1);
					HordeRenderMetrics.add(HordeRenderMetrics.Counter.HISTORY_COPY_BYTES, bytes);
				}
				try (var pass = encoder.createRenderPass(() -> "Horde stable unit movement", state.current(), OptionalInt.empty())) {
					pass.setPipeline(UNIT_UPDATE);
					pass.setViewport(0, 0, state.width(), state.height());
					pass.bindTexture("HordeUnitPrevious", state.previous(), nearest);
					pass.bindTexture("HordeUnitIndex", state.index(), nearest);
					pass.bindTexture("HordeRoute", data.route.texture().getTextureView(), nearest);
					pass.bindTexture("HordeHeights", data.heights.texture().getTextureView(), nearest);
					pass.bindTexture("HordeRegions", data.regions.raw().texture().getTextureView(), nearest);
					pass.bindTexture("HordeRegionState", data.regions.view(), nearest);
					pass.bindTexture("HordeTerrain", data.terrain.view(), nearest);
					pass.bindTexture("HordeSpatial32", data.spatial.view(0, data.regions), nearest);
					pass.bindTexture("HordeSpatial16", data.spatial.view(1, data.regions), nearest);
					pass.bindTexture("HordeSpatial8", data.spatial.view(2, data.regions), nearest);
					for (int index : entry.getValue()) {
						var item = prepared.get(index);
						pass.setUniform("HordeParameters", parameters.slice((long) index * stride, PARAMETER_BYTES));
						pass.draw(0, Math.multiplyExact(6, item.count()));
						HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
						HordeRenderMetrics.add(HordeRenderMetrics.Counter.HISTORY_INSTANCES, item.count());
					}
					HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
				}
			} finally {
				HordeRenderMetrics.end(HordeRenderMetrics.Stage.UNITS, started);
			}
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
					item.count(), item.geometry().model.vertexCount())).toArray();
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
		int maximum = prepared.stream().mapToInt(item -> item.geometry().indices).max().orElse(0);
		var indexBuffer = indices.getBuffer(maximum);
		var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		drawUnitHistories(prepared, stride);
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
		try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> "Lobecorp GPU horde", colorTarget, OptionalInt.empty(),
				depthTarget, OptionalDouble.empty())) {
			pass.setPipeline(reverse ? REVERSE : NORMAL);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), linear);
			pass.bindTexture("HordeMotion", motionView, nearest);
			pass.setIndexBuffer(indexBuffer, indices.type());
			for (int i = 0; i < prepared.size(); i++) {
				var item = prepared.get(i);
				pass.setUniform("HordeParameters", parameters.slice((long) i * stride, PARAMETER_BYTES));
				pass.bindTexture("Sampler0", item.geometry().base.getTextureView(), item.geometry().base.getSampler());
				pass.bindTexture("Glowmask", item.geometry().glow.getTextureView(), item.geometry().glow.getSampler());
				pass.bindTexture("HordeAnimation", item.geometry().animation.texture().getTextureView(), nearest);
				pass.setVertexBuffer(0, item.geometry().vertices);
				pass.drawIndexed(0, 0, item.geometry().indices, item.count());
				drawn += item.count();
				batches++;
				HordeRenderMetrics.add(HordeRenderMetrics.Counter.DRAWS, 1);
			}
			HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
		} finally {
			HordeRenderMetrics.end(HordeRenderMetrics.Stage.BASE, baseStarted);
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
				default -> HordeRenderMetrics.Counter.LOD_NEAR_BLOCKS;
			}, 1);
			var data = EVENTS.computeIfAbsent(event.id, _ -> new EventData(event));
			data.updateHeights(event);
			data.regions.update(event, batch.time());
			data.terrain.update(event, data.heights, batch.time());
			data.spatial.update(event, data.regions, batch.time(), visualRadius(event.visual));
			for (int offset = 0; offset < batch.count(); ) {
				int limit = irisPath() ? IRIS.instanceLimit(geometry.model.vertexCount()) : INSTANCES_PER_DRAW;
				int count = Math.min(limit, batch.count() - offset);
				prepared.add(new Prepared(batch, geometry, data, offset, count));
				offset += count;
			}
		}
		return prepared;
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
		put((float) origin.x, (float) origin.y, (float) origin.z, (float) batch.time());
		put((float) camera.x, (float) camera.y, (float) camera.z, (float) event.speed);
		put((float) event.rules.rowSpacing(), (float) event.rules.columnSpacing(), event.columns, (float) event.length);
		put((float) (batch.time() * event.speed - row * event.rules.rowSpacing()), batch.block().firstColumn(), batch.block().columns(), columnOffset);
		put((float) batch.block().first(), (float) batch.block().last(), vertexOffset, item.event().regions.activeCount());
		put(item.geometry().model.vertexCount(), item.geometry().model.frames(), item.geometry().model.duration(), item.geometry().hasGlow ? 1 : 0);
		put(LIGHT.mask(), event.nodes.length, item.event().regions.slotCount(), event.entityHeight);
		int sx = (int) Math.floor(event.origin.x / 16);
		int sy = (int) Math.floor(event.origin.y / 16);
		int sz = (int) Math.floor(event.origin.z / 16);
		put(sx, sy, sz, (float) (row % 1024));
		put((float) event.groundWidth,
				(float) (event.origin.x - sx * 16d), (float) (event.origin.y - sy * 16d), (float) (event.origin.z - sz * 16d));
		put(event.lightProbeHeight, event.heightColumns, IrisVertices.rowWidth(), (float) HordeSamplingUtil.MAX_VISUAL_OFFSET);
		upload.putInt(motionOffset).putInt(item.count()).putInt(motionWidth).putInt(IRIS.wordsPerVertex);
		var spatial = item.event().spatial;
		var grid = spatial.grid();
		upload.putInt(grid.x()).putInt(grid.y()).putInt(grid.z()).putInt(grid.stride());
		upload.putInt(grid.nx()).putInt(grid.ny()).putInt(grid.nz()).putInt(grid.flags());
		upload.putInt(spatial.width(0)).putInt(spatial.width(1)).putInt(spatial.width(2))
				.putInt(Float.floatToRawIntBits(visualRadius(event.visual)));
		upload.putInt(item.event().units.mask()).putInt((int) row).putInt((int) (row >>> 32)).putInt((int) drawSerial);
		put(0.75f, 8, 0.05f, HordeRenderMetrics.collecting() || HordeVisualConfig.STATISTICS.get() ? 1 : 0);
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
			var base = minecraft.getTextureManager().getTexture(visual.texture());
			Identifier glowId = visual.texture().withPath(path -> path.replace(".png", "_glowmask.png"));
			boolean hasGlow = minecraft.getResourceManager().getResource(glowId).isPresent();
			var glow = hasGlow ? minecraft.getTextureManager().getTexture(glowId) : base;
			preparations++;
			return new Geometry(buffer, vertices.length / HordeMeshCache.VERTEX_STRIDE / 4 * 6, animation, uvs, model, base, glow, hasGlow, radius);
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
				pass.bindTexture("HordeUnitCurrent", item.event().units.current(), nearest);
				pass.bindTexture("HordeUnitIndex", item.event().units.index(), nearest);
				pass.bindTexture("HordeRoute", item.event().route.texture().getTextureView(), nearest);
				pass.bindTexture("HordeHeights", item.event().heights.texture().getTextureView(), nearest);
				pass.bindTexture("HordeRegions", item.event().regions.raw().texture().getTextureView(), nearest);
				pass.bindTexture("HordeRegionState", item.event().regions.view(), nearest);
				pass.bindTexture("HordeTerrain", item.event().terrain.view(), nearest);
				pass.bindTexture("HordeSpatial32", item.event().spatial.view(0, item.event().regions), nearest);
				pass.bindTexture("HordeSpatial16", item.event().spatial.view(1, item.event().regions), nearest);
				pass.bindTexture("HordeSpatial8", item.event().spatial.view(2, item.event().regions), nearest);
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
		GEOMETRY.values().forEach(geometry -> {
			geometry.vertices.close();
			geometry.animation.close();
			geometry.uvs.close();
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

	private record Geometry(GpuBuffer vertices, int indices, HordeDataTexture animation, HordeDataTexture uvs,
	                        HordeMeshCache.Model model,
	                        AbstractTexture base, AbstractTexture glow, boolean hasGlow, float radius) {
	}

	private record Prepared(HordeClient.Batch batch, Geometry geometry, EventData event, int offset, int count) {
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
			int vertices = offsets[last - 1] + Math.multiplyExact(tail.count(), tail.geometry().model.vertexCount());
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
							+ Math.multiplyExact(item.count(), item.geometry().model.vertexCount()), wordsPerVertex), slot.width);
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
			for (int layer = 0; layer < (glow ? 2 : 1); layer++) {
				var stage = layer == 0 ? HordeRenderMetrics.Stage.BASE : HordeRenderMetrics.Stage.GLOW;
				long started = HordeRenderMetrics.begin(stage, vertices);
				try (var pass = encoder.createRenderPass(() -> "Horde Iris merged entity material", colorTarget,
						OptionalInt.empty(), depthTarget, OptionalDouble.empty())) {
					pass.setPipeline(layer == 0 ? RenderPipelines.ENTITY_CUTOUT : RenderPipelines.EYES);
					RenderSystem.bindDefaultUniforms(pass);
					pass.setUniform("DynamicTransforms", transforms);
					if (layer == 0) {
						pass.bindTexture("Sampler1", minecraft.gameRenderer.overlayTexture().getTextureView(), nearest);
					}
					pass.bindTexture("Sampler2", minecraft.gameRenderer.lightmap(), linear);
					pass.setIndexBuffer(indexBuffer, indices.type());
					HordeRenderMetrics.add(HordeRenderMetrics.Counter.PASSES, 1);
					for (var chunk : chunks) {
						pass.setVertexBuffer(0, chunk.slot().vertices);
						for (int i = chunk.first(); i < chunk.last(); ) {
							var item = items.get(i);
							if (layer != 0 && !item.geometry().hasGlow) {
								i++;
								continue;
							}
							var material = layer == 0 ? item.geometry().base : item.geometry().glow;
							int begin = offsets[i];
							int count = item.count() * item.geometry().model.vertexCount();
							i++;
							while (i < chunk.last()) {
								var next = items.get(i);
								if (layer != 0 && !next.geometry().hasGlow
										|| (layer == 0 ? next.geometry().base : next.geometry().glow) != material) {
									break;
								}
								count += next.count() * next.geometry().model.vertexCount();
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
		private final HordeRegionState regions = new HordeRegionState();
		private final HordeTerrainState terrain = new HordeTerrainState();
		private final HordeSpatialIndex spatial = new HordeSpatialIndex();
		private final HordeUnitState units = new HordeUnitState();
		private final HordeUnitStats unitStats = new HordeUnitStats();

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
			regions.close();
			terrain.close();
			spatial.close();
			units.close();
			unitStats.close();
		}
	}
}
