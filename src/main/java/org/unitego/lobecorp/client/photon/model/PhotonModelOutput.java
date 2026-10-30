package org.unitego.lobecorp.client.photon.model;

import com.lowdragmc.lowdraglib2.configurator.ConfiguratorParser;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.Photon;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.render.PhotonCameraUtils;
import com.lowdragmc.photon.client.render.PhotonViewSettings;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Tint;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.client.photon.playback.PhotonEmissionAccess;
import org.unitego.lobecorp.registry.photon.client.LcPhotonModelOutputTypes;
import org.unitego.lobecorp.client.photon.model.timeline.PhotonModelClip;
import org.unitego.lobecorp.client.photon.performance.PhotonRenderPrewarm;
import org.unitego.lobecorp.util.photon.model.PhotonModelGeometryUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelRuntimeCopyUtil;
import org.unitego.lobecorp.util.photon.model.PhotonPackedVertexUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelGeometryUtil.NormalCache;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler;
import org.unitego.lobecorp.util.photon.model.PhotonModelSourceUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;
import org.unitego.lobecorp.util.photon.creation.PhotonFrozenTag;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Metric;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/// 独立模型输出复用Photon生命周期与原生材质批次；绘制回调只消费准备后的数据。
@ParametersAreNonnullByDefault
public class PhotonModelOutput extends Emitter implements PhotonEmissionAccess {
	public interface PreparedSource extends Supplier<PhotonModelTopologyUtil.Prepared> {
		boolean matches(PhotonModelOutputConfig current);
	}

	@Persisted(subPersisted = true)
	public final PhotonModelOutputConfig config;
	private final RendererSetting.Runtime modelRenderer;
	private final RendererSetting.Runtime overrideRenderer;
	private @Nullable Supplier<PhotonModelTopologyUtil.Prepared> compiledSupplier;
	private final PhotonModelClock clock = new PhotonModelClock();
	private final NormalCache normalCache = new NormalCache();
	private static final int MAX_SNAPSHOT_VERTICES = PhotonPackedVertexUtil.MAX_VERTICES;
	private static final boolean PACKED_SNAPSHOT_SUBMISSION = Boolean.getBoolean(PhotonPackedVertexUtil.ENABLE_PROPERTY);
	private final Map<Integer, GeometrySnapshot> geometrySnapshots = new HashMap<>();
	private @Nullable PhotonModelPoseSampler.Frame snapshotFrame;
	private boolean frameReused;
	private final Map<String, RuntimeValue<?>> slots = new LinkedHashMap<>();
	private final Map<Object, PhotonModelClip.Sample> playback = new LinkedHashMap<>();
	private static final Comparator<PhotonModelClip.Sample> PLAYBACK_ORDER = Comparator.comparingInt(PhotonModelClip.Sample::priority)
			.thenComparingLong(PhotonModelClip.Sample::order);
	private CompoundTag original = new CompoundTag();
	private @Nullable PhotonFrozenTag compiledOriginal;
	private @Nullable PhotonModelPoseSampler sampler;
	private @Nullable PhotonModelMaterials materials;
	private @Nullable PhotonModelBindings dynamic;
	private @Nullable PhotonModelPoseSampler.Input sampledInput;
	private @Nullable PhotonModelPoseSampler.Frame frame;
	private List<Integer> groupOrder = List.of();
	private @Nullable String failure;
	private boolean preparedOverride;
	private boolean measure;
	private boolean preserveParticles;

	public PhotonModelOutput() {
		this(new PhotonModelOutputConfig(), null);
	}

	public PhotonModelOutput(PhotonModelOutputConfig config, @Nullable Supplier<PhotonModelTopologyUtil.Prepared> prepared) {
		this.config = Objects.requireNonNull(config);
		compiledSupplier = prepared;
		modelRenderer = new RendererSetting.Runtime(config.modelRenderer) {
		};
		overrideRenderer = new RendererSetting.Runtime(config.renderer) {
		};
		bind("enabled", () -> config.enabled);
		bind("duration", () -> config.duration);
		bind("startDelay", () -> config.startDelay);
		bind("looping", () -> config.looping);
		bind("paused", () -> config.paused);
		bind("playbackSpeed", () -> config.playbackSpeed);
		bind("animationOffset", () -> config.animationOffset);
		bind("length", () -> dimension(true));
		bind("width", () -> dimension(false));
		bind("opacity", () -> config.opacity);
		bind("color", () -> NumberFunction.color(config.color));
	}

	private void bind(String path, Supplier<?> fallback) {
		slots.put(path, new RuntimeValue<>(fallback));
	}

	public RuntimeValue<?> slot(String path) {
		var value = slots.get(path);

		if (value == null && dynamic != null) {
			value = dynamic.slot(path);
		}

		if (value == null) {
			throw new IllegalArgumentException("模型运行属性不存在：" + path);
		}

		return value;
	}

	public @Nullable RuntimeBinding dynamicBinding(String path) {
		return dynamic == null ? null : dynamic.binding(path);
	}

	public List<AnimatedPropertyType> dynamicProperties() {
		return dynamic == null ? List.of() : dynamic.properties();
	}

	public List<String> animationIds() {
		return sampler == null ? List.of() : definition().clips().stream().map(PhotonModelDefinition.Clip::id).toList();
	}

	public boolean supportsAnimation(String id) {
		return !id.isEmpty() && sampler != null && definition().clips().stream().anyMatch(clip -> clip.id().equals(id));
	}

	public float animationDuration(String id) {
		return definition().clips().stream().filter(clip -> clip.id().equals(id)).findFirst().orElseThrow().duration();
	}

	public void playback(Object owner, @Nullable PhotonModelClip.Sample sample) {
		if (sample == null) {
			playback.remove(owner);
		} else {
			if (!supportsAnimation(sample.animation())) {
				throw new IllegalArgumentException("模型动画片段不存在：" + sample.animation());
			}

			playback.put(owner, sample);
		}
	}

	private <T> T value(String path, Class<T> type) {
		return type.cast(slot(path).get());
	}

	private float dimension(boolean length) {
		if (config.useReferenceDimensions && sampler != null) {
			var definition = definition();
			return length ? definition.referenceLength() : definition.referenceWidth();
		}

		return length ? config.length : config.width;
	}

	private PhotonModelClock.Settings settings() {
		return new PhotonModelClock.Settings(value("enabled", Boolean.class), value("paused", Boolean.class),
				value("looping", Boolean.class), getLifetime(), getStartDelay(),
				nonnegative(value("playbackSpeed", Float.class), 1));
	}

	private static float nonnegative(float value, float fallback) {
		return Float.isFinite(value) ? Math.max(0, value) : fallback;
	}

	public void rememberSource(CompoundTag tag) {
		compiledOriginal = null;
		original = tag.copy();
	}

	/// 冻结来源由定义持有，实例保存时才复制；编辑来源时通过 rememberSource 脱离共享。
	public void compiledSource(PhotonFrozenTag source) {
		compiledOriginal = Objects.requireNonNull(source);
		original = new CompoundTag();
	}

	public CompoundTag source() {
		return compiledOriginal == null ? original.copy() : compiledOriginal.compoundCopy();
	}

	public PhotonFrozenTag compileSource() {
		if (compiledOriginal == null) {
			compiledOriginal = new PhotonFrozenTag(original);
			original = new CompoundTag();
		}

		return compiledOriginal;
	}

	/// 只替换未接线节点的准备来源，不加载资源或推进播放。
	public void compiledPreparation(@Nullable Supplier<PhotonModelTopologyUtil.Prepared> prepared) {
		compiledSupplier = prepared;
		materials = null;
		sampler = null;
		dynamic = null;
		sampledInput = null;
		frame = null;
		snapshotFrame = null;
		discardPackedSnapshots();
		geometrySnapshots.clear();
	}

	public void prepare() throws IOException {
		if (compiledSupplier instanceof PreparedSource source && !source.matches(config)) {
			compiledPreparation(null);
		}
		if (sampler == null) {
			try {
				sampler = compiledSupplier == null ? PhotonModelSourceUtil.prepare(config)
						: new PhotonModelPoseSampler(compiledSupplier.get());
			} catch (UncheckedIOException invalid) {
				throw invalid.getCause();
			}
			var topology = sampler.prepared();
			var definition = topology.definition();
			var order = new ArrayList<Integer>();

			for (int index = 0; index < topology.groups().size(); index++) {
				order.add(index);
			}

			order.sort(Comparator.comparing(index -> {
				var material = definition.materials().get(topology.groups().get(index).material());
				return material != null && material.overlay();
			}));
			groupOrder = List.copyOf(order);
		}

		if (dynamic == null) {
			dynamic = new PhotonModelBindings(sampler.prepared().definition());
		}

		if (materials == null || preparedOverride != config.overrideRenderer || materials.materialsChanged()) {
			materials = new PhotonModelMaterials(config.overrideRenderer ? config.renderer : config.modelRenderer,
					rendererRuntime(), sampler.prepared());
			preparedOverride = config.overrideRenderer;
		}

		failure = null;
	}

	/// 预热渲染侧：准备模型资源并预构建其材质渲染类型；不发射、不推进时间线。
	public int prewarmRenderTypes() {
		try {
			prepare();
		} catch (IOException invalid) {
			Photon.LOGGER.warn("模型渲染预热准备失败：{}", getName(), invalid);
			return 0;
		}

		if (materials == null) {
			return 0;
		}

		int built = 0;

		for (int group : groupOrder) {
			built += PhotonRenderPrewarm.prebuild(materials.group(group), geometryMode());
		}

		return built;
	}

	/// 队列的模型准备任务：只准备并逐组登记材质，实际获取留给后续独立任务。
	public void queuePrewarmRenderTypes(PhotonRenderPrewarm.Queue queue, BooleanSupplier alive) {
		try {
			prepare();
		} catch (IOException invalid) {
			throw new IllegalStateException("模型渲染预热准备失败：" + getName(), invalid);
		}

		if (materials == null) {
			return;
		}

		for (int group : groupOrder) {
			try {
				queue.addMaterials(alive, materials.group(group), geometryMode());
			} catch (RuntimeException invalid) {
				Photon.LOGGER.warn("模型材质组预热登记失败：{} / {}", getName(), group, invalid);
			}
		}
	}

	private PhotonModelDefinition definition() {
		if (sampler == null) {
			throw new IllegalStateException("模型尚未准备。");
		}

		return sampler.prepared().definition();
	}

	@Override
	public IFXObject deepCopy() {
		return PhotonModelRuntimeCopyUtil.deepCopy(this);
	}

	@Override
	public FXObjectType getFXObjectType() {
		return LcPhotonModelOutputTypes.MODEL;
	}

	@Override
	public void reset() {
		super.reset();
		slots.values().forEach(RuntimeValue::clear);

		if (dynamic != null) {
			dynamic.clear();
		}

		discardPackedSnapshots();
		modelRenderer.clear();
		overrideRenderer.clear();
		playback.clear();
		sampledInput = null;
		frame = null;

		try {
			prepare();
			clock.reset(settings(), preserveParticles);
		} catch (IOException | RuntimeException error) {
			failure = error.getMessage();
			clock.clear();
			Photon.LOGGER.error("模型输出准备失败：{}", getName(), error);
		}
	}

	@Override
	protected void update(float dt) {
		if (materials != null && (preparedOverride != config.overrideRenderer || materials.materialsChanged())) {
			try {
				prepare();
			} catch (IOException error) {
				failure = error.getMessage();
				clock.clear();
			}
		}

		clock.advance(dt, settings());
		ageF = clock.age();
		age = (int) ageF;
		t = clock.progress(0, 1, settings());

		if (clock.finished()) {
			super.remove(false);
		}
	}

	@Override
	public void remove(boolean force) {
		super.remove(force);
		clock.stop(settings(), force);

		if (force) {
			discardPackedSnapshots();
			frame = null;
			sampledInput = null;
			playback.clear();
		}
	}

	@Override
	public int getParticleAmount() {
		return sampler != null && clock.visible(settings()) ? 1 : 0;
	}

	@Override
	public boolean lobecorp$emissionStopped() {
		return clock.emissionStopped();
	}

	@Override
	public void lobecorp$emissionStopped(boolean stopped) {
		clock.emissionStopped(settings(), stopped);
	}

	@Override
	public void lobecorp$preserveParticles(boolean preserve) {
		preserveParticles = preserve;
	}

	@Override
	public void lobecorp$clearParticles() {
		discardPackedSnapshots();
		clock.clear();
		frame = null;
		sampledInput = null;
	}

	@Override
	public int getLifetime() {
		return Math.max(1, value("duration", Integer.class));
	}

	@Override
	public int getStartDelay() {
		return Math.max(0, value("startDelay", Integer.class));
	}

	@Override
	public boolean isLooping() {
		return value("looping", Boolean.class);
	}

	@Override
	public float getT(float partialTicks) {
		return clock.progress(partialTicks, timeScale(), settings());
	}

	public @Nullable String failure() {
		return failure;
	}

	public void measure(boolean enabled) {
		measure = enabled;
		sampledInput = null;
	}

	public @Nullable PhotonModelPoseSampler.Frame sampledFrame() {
		return frame;
	}

	@Override
	public RendererSetting.Runtime rendererRuntime() {
		return config.overrideRenderer ? overrideRenderer : modelRenderer;
	}

	@Override
	public @Nullable AABB getCullBox(float partialTicks) {
		var cull = rendererRuntime().getCull();
		return cull.isEnable() ? cull.getCullAABB(this, partialTicks) : null;
	}

	/// 运行属性读取属于客户端线程；返回的不可变局部帧可交给多个视图消费。
	public @Nullable PhotonModelPoseSampler.Frame sampleLocal(float partialTicks) {
		long scope = PhotonPerformanceTraceUtil.enabled(Stage.MODEL_SAMPLE)
				? PhotonPerformanceTraceUtil.begin(Stage.MODEL_SAMPLE, PhotonPerformanceOwnerUtil.object(this)) : 0;
		boolean completed = false;

		try {
			var sampled = sampleLocalFrame(partialTicks);

			if (sampled != null) {
				failure = null;
			}
			completed = true;
			return sampled;
		} catch (IllegalArgumentException invalid) {
			failure = invalid.getMessage();
			return null;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	private @Nullable PhotonModelPoseSampler.Frame sampleLocalFrame(float partialTicks) {
		if (getParticleAmount() == 0 || sampler == null) {
			return null;
		}

		var bindings = Objects.requireNonNull(dynamic);
		// 无片段时局部几何与时间无关；外部TRS/Morph覆盖仍作为缓存键的一部分。
		var clipId = config.clip;
		boolean loop = isLooping();
		float time = clipId.isEmpty() ? 0 : clock.animationTime(partialTicks, nonnegative(timeScale(), 1), settings())
				+ value("animationOffset", Float.class);
		var selected = playback.values().stream().max(PLAYBACK_ORDER).orElse(null);

		if (selected != null) {
			clipId = selected.animation();
			loop = selected.effectiveLoop();
			var clip = definition().clips().stream().filter(candidate -> candidate.id().equals(selected.animation())).findFirst().orElseThrow();
			time = selected.assetTime(clip.duration(), clip.loop());
		}

		var input = new PhotonModelPoseSampler.Input(time, clipId, loop,
				nonnegative(value("length", Float.class), definition().referenceLength()),
				nonnegative(value("width", Float.class), definition().referenceWidth()), config.anchor,
				Math.clamp(nonnegative(value("opacity", Float.class), 1), 0, 1), bindings.morphWeights(),
				bindings.partOverrides(this, getT(partialTicks)), bindings.boneOverrides(this, getT(partialTicks)));

		if (!input.equals(sampledInput)) {
			var computed = sampler.sample(input, measure || PhotonPerformanceTraceUtil.enabled(Stage.MODEL_POSE));
			frame = computed;
			sampledInput = input;
			frameReused = false;

			if (PhotonPerformanceTraceUtil.enabled(Stage.MODEL_SAMPLE)) {
				PhotonPerformanceTraceUtil.counter(PhotonPerformanceOwnerUtil.object(this), Metric.CACHE_MISS, 1);
			}

			if (PhotonPerformanceTraceUtil.enabled(Stage.MODEL_POSE) && computed.timings() != null) {
				int node = PhotonPerformanceOwnerUtil.object(this);
				var timings = computed.timings();
				phase(node, "curves", timings.curvesNanos());
				phase(node, "deformation", timings.deformationNanos());
				phase(node, "normals_frame", timings.normalsNanos());
			}
		} else {
			frameReused = true;

			if (PhotonPerformanceTraceUtil.enabled(Stage.MODEL_SAMPLE)) {
				PhotonPerformanceTraceUtil.counter(PhotonPerformanceOwnerUtil.object(this), Metric.CACHE_HIT, 1);
			}
		}

		return frame;
	}

	private static void phase(int parent, String name, long nanos) {
		int node = PhotonPerformanceTraceUtil.namedNode(parent, "model_cpu_phase", name, name, "");
		PhotonPerformanceTraceUtil.mark(Stage.MODEL_POSE, node, "duration_ns", "cpu", nanos, 0);
	}

	@Override
	protected void bakeBatches(PhotonViewSettings settings, List<PhotonWorldRenderState.DrawJob> out,
	                           Camera camera, float partialTicks) {
		if (materials == null) {
			return;
		}

		var sampled = sampleLocal(partialTicks);

		if (sampled == null) {
			return;
		}

		var origin = PhotonCameraUtils.renderOrigin(camera);
		var world = new Matrix4f().translation(-origin.x, -origin.y, -origin.z).mul(transform().localToWorldMatrix());
		var function = value("color", NumberFunction.class);
		int color = function.get(getT(partialTicks), () -> getMemRandom(function)).intValue();
		int light = getLightColor(BlockPos.containing(transform().position().x, transform().position().y, transform().position().z));

		for (int group : groupOrder) {
			bakeModelGroup(settings, out, camera, partialTicks, sampled, group, world, color, light, frameReused);
		}
	}

	private void bakeModelGroup(PhotonViewSettings settings, List<PhotonWorldRenderState.DrawJob> out, Camera camera,
	                            float partialTicks, PhotonModelPoseSampler.Frame sampled, int group,
	                            Matrix4f world, int color, int light, boolean stableFrame) {
		var topology = sampled.prepared().groups().get(group);
		var definition = sampled.prepared().definition();
		var part = definition.parts().get(topology.part());
		var material = definition.materials().get(topology.material());
		int groupColor = material != null && material.tint() == Tint.GRASS
				? ARGB.multiply(color, ARGB.opaque(GrassColor.getDefaultColor())) : color;
		int node = 0;

		if (PhotonPerformanceTraceUtil.enabled(Stage.MODEL_PART)) {
			int partNode = PhotonPerformanceTraceUtil.namedNode(PhotonPerformanceOwnerUtil.object(this),
					"model_part", part.id(), part.id(), "");
			node = PhotonPerformanceTraceUtil.namedNode(partNode, "material_group", topology.material(), topology.material(), "");
			PhotonPerformanceTraceUtil.counter(node, Metric.INPUT_TRIANGLES, topology.indices().size() / 3);
		}

		int owner = node;
		long scope = PhotonPerformanceTraceUtil.begin(Stage.MODEL_PART, node);
		int start = out.size();
		boolean completed = false;

		try {
			bakeGroup(settings, out, camera, partialTicks, Objects.requireNonNull(materials).group(group), geometryMode(),
					(output, _, _) -> {
						GeometrySnapshot cached = null;

						if (stableFrame) {
							if (snapshotFrame != sampled) {
								geometrySnapshots.clear();
								snapshotFrame = sampled;
							}

							cached = geometrySnapshots.get(group);

							if (cached != null && !cached.matches(world, groupColor, light)) {
								cached = null;
							}
						}

						int triangles;

						if (cached != null) {
							cached.replay(output, allowPackedSnapshot(cached));
							triangles = cached.triangles();
						} else if (stableFrame) {
							var snapshot = GeometrySnapshot.captureAndSubmit(output, sampled, group, world, groupColor, light, normalCache);
							triangles = topology.indices().size() / 3;

							if (snapshot != null) {
								geometrySnapshots.put(group, snapshot);
							}
						} else {
							triangles = PhotonModelGeometryUtil.write(output, sampled, group, world, groupColor, light, normalCache);
						}

						if (owner != 0) {
							PhotonPerformanceTraceUtil.counter(owner, Metric.VERTICES, triangles * 4L);
							PhotonPerformanceTraceUtil.counter(owner, Metric.TRIANGLES, triangles);
							PhotonPerformanceTraceUtil.counter(owner, Metric.SUBMITTED_TRIANGLES, triangles * 2L);
						}
					});
			completed = true;
		} finally {
			if (owner != 0) {
				PhotonPerformanceTraceUtil.counter(owner, Metric.DRAW_JOBS, Math.max(0, out.size() - start));
			}

			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	private boolean allowPackedSnapshot(GeometrySnapshot cached) {
		if (!PACKED_SNAPSHOT_SUBMISSION) {
			return false;
		}

		if (cached.packedBytes() != 0) {
			return true;
		}

		return GeometrySnapshot.withinPackedBudget(geometrySnapshots.values());
	}

	private void discardPackedSnapshots() {
		geometrySnapshots.values().forEach(GeometrySnapshot::discardPacked);
	}

	@Override
	protected void bakeGeometry(VertexConsumer geometry, Camera camera, float partialTicks) {
		// 多材质由bakeBatches逐组提交，原生默认单组入口不重复写入。
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		super.buildConfigurator(father);
		var group = new ConfiguratorGroup("lobecorp_model_emitter");
		ConfiguratorParser.createConfigurators(group, config);
		group.addEventListener(Configurator.CHANGE_EVENT, _ -> invalidate());
		father.addConfigurator(group);
	}

	@Override
	public void inspectSceneInformation(SceneView view, UIElement container) {
		super.inspectSceneInformation(view, container);
		container.addChild(view.fxObjectInfoView.createInformation(
				Component.translatable("photon.lobecorp.model.model_status"),
				() -> Component.translatable("photon.lobecorp.model.status." +
						(failure != null ? "invalid" : sampler == null ? "missing" : "ok"))));

		if (failure != null) {
			container.addChild(view.fxObjectInfoView.createInformation(Component.literal(getName()),
					() -> Component.literal(Objects.toString(failure, ""))));
		}
	}

	private void invalidate() {
		compiledSupplier = null;
		materials = null;
		sampler = null;
		dynamic = null;
		playback.clear();
		sampledInput = null;
		frame = null;
		snapshotFrame = null;
		geometrySnapshots.clear();

		try {
			prepare();
		} catch (IOException | RuntimeException error) {
			failure = error.getMessage();
			clock.clear();
		}
	}

	@Override
	public IFXObject shallowCopy() {
		var copied = (PhotonModelOutput) deepCopy();
		copied.sampler = sampler;
		copied.groupOrder = groupOrder;
		return copied;
	}

	/// 稳定帧的最终顶点快照；只在姿态输入未变化时复用，避免每帧重复计算世界变换、法线与颜色。
	public static final class GeometrySnapshot {
		private static final int STRIDE = 12;
		private final float[] matrix = new float[16];
		private final int color;
		private final int light;
		private final float[] vertices;
		private final int[] lights;
		private final int count;
		private final int triangles;
		private @Nullable byte[] packed;
		private boolean packedAttempted;

		public GeometrySnapshot(Matrix4f world, int color, int light, float[] vertices, int[] lights, int count, int triangles) {
			matrix[0] = world.m00();
			matrix[1] = world.m01();
			matrix[2] = world.m02();
			matrix[3] = world.m03();
			matrix[4] = world.m10();
			matrix[5] = world.m11();
			matrix[6] = world.m12();
			matrix[7] = world.m13();
			matrix[8] = world.m20();
			matrix[9] = world.m21();
			matrix[10] = world.m22();
			matrix[11] = world.m23();
			matrix[12] = world.m30();
			matrix[13] = world.m31();
			matrix[14] = world.m32();
			matrix[15] = world.m33();
			this.color = color;
			this.light = light;
			this.vertices = vertices;
			this.lights = lights;
			this.count = count;
			this.triangles = triangles;
		}

		/// 当次始终通过原逐顶点链提交；超出顶点预算时直接写入且不保留快照。
		public static @Nullable GeometrySnapshot captureAndSubmit(VertexConsumer output, PhotonModelPoseSampler.Frame frame,
		                                                          int group, Matrix4f world, int color, int light, NormalCache cache) {
			long vertexCount = (long) (frame.prepared().groups().get(group).indices().size() / 3) * 4;
			if (vertexCount > MAX_SNAPSHOT_VERTICES) {
				PhotonModelGeometryUtil.write(output, frame, group, world, color, light, cache);
				return null;
			}

			var recorder = new SnapshotRecorder();
			int triangles = PhotonModelGeometryUtil.write(recorder, frame, group, world, color, light, cache);
			recorder.finish();
			var snapshot = new GeometrySnapshot(world, color, light, recorder.vertices(), recorder.lights(), recorder.count(), triangles);
			snapshot.replay(output);
			return snapshot;
		}

		boolean matches(Matrix4f world, int color, int light) {
			return this.color == color && this.light == light
					&& same(world.m00(), matrix[0]) && same(world.m01(), matrix[1])
					&& same(world.m02(), matrix[2]) && same(world.m03(), matrix[3])
					&& same(world.m10(), matrix[4]) && same(world.m11(), matrix[5])
					&& same(world.m12(), matrix[6]) && same(world.m13(), matrix[7])
					&& same(world.m20(), matrix[8]) && same(world.m21(), matrix[9])
					&& same(world.m22(), matrix[10]) && same(world.m23(), matrix[11])
					&& same(world.m30(), matrix[12]) && same(world.m31(), matrix[13])
					&& same(world.m32(), matrix[14]) && same(world.m33(), matrix[15]);
		}

		public void replay(VertexConsumer output) {
			replay(output, false);
		}

		public void replay(VertexConsumer output, boolean allowPacked) {
			if (allowPacked && output instanceof IPhotonPackedVertexSink sink) {
				if (!packedAttempted) {
					packed = PhotonPackedVertexUtil.pack(vertices, lights, count);
					packedAttempted = true;
				}

				if (packed != null && sink.lobecorp$tryAppendPhotonSnapshot(packed, count)) {
					return;
				}
			}

			for (int index = 0, offset = 0; index < count; index++, offset += STRIDE) {
				output.addVertex(vertices[offset], vertices[offset + 1], vertices[offset + 2])
						.setUv(vertices[offset + 6], vertices[offset + 7])
						.setColor(vertices[offset + 8], vertices[offset + 9], vertices[offset + 10], vertices[offset + 11])
						.setLight(lights[index])
						.setNormal(vertices[offset + 3], vertices[offset + 4], vertices[offset + 5]);
			}
		}

		public static boolean withinPackedBudget(Iterable<GeometrySnapshot> snapshots) {
			long bytes = 0;
			for (var snapshot : snapshots) {
				if (snapshot.count < 0) {
					return false;
				}
				bytes += (long) snapshot.count * PhotonPackedVertexUtil.VERTEX_STRIDE;
				if (bytes > PhotonPackedVertexUtil.MAX_CACHED_BYTES) {
					return false;
				}
			}
			return true;
		}

		public int packedBytes() {
			return packed == null ? 0 : packed.length;
		}

		public void discardPacked() {
			packed = null;
			packedAttempted = false;
		}

		int triangles() {
			return triangles;
		}

		private static boolean same(float first, float second) {
			return Float.floatToRawIntBits(first) == Float.floatToRawIntBits(second);
		}
	}

	/// 记录一次稳定帧写入的最终顶点属性；数组在输出内复用，不改变 Photon 的提交顺序。
	static final class SnapshotRecorder implements VertexConsumer {
		private static final int STRIDE = 12;
		private float[] vertices = new float[256 * STRIDE];
		private int[] lights = new int[256];
		private int count;
		private int current = -1;
		private float x, y, z, nx, ny, nz, u, v, r, g, b, a;
		private int light;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			flush();

			if (count == lights.length) {
				int size = Math.max(1, count * 2);
				var grownVertices = new float[size * STRIDE];
				System.arraycopy(vertices, 0, grownVertices, 0, count * STRIDE);
				vertices = grownVertices;
				var grownLights = new int[size];
				System.arraycopy(lights, 0, grownLights, 0, count);
				lights = grownLights;
			}

			current = count++;
			this.x = x;
			this.y = y;
			this.z = z;
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			return setColor(red / 255f, green / 255f, blue / 255f, alpha / 255f);
		}

		@Override
		public VertexConsumer setColor(int color) {
			return setColor(color >>> 16 & 255, color >>> 8 & 255, color & 255, color >>> 24);
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			this.u = u;
			this.v = v;
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			light = u | v << 16;
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			nx = x;
			ny = y;
			nz = z;
			return this;
		}

		@Override
		public VertexConsumer setColor(float red, float green, float blue, float alpha) {
			r = red;
			g = green;
			b = blue;
			a = alpha;
			return this;
		}

		@Override
		public VertexConsumer setLight(int light) {
			this.light = light;
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}

		void finish() {
			flush();
		}

		float[] vertices() {
			return vertices;
		}

		int[] lights() {
			return lights;
		}

		int count() {
			return count;
		}

		private void flush() {
			if (current < 0) {
				return;
			}

			int offset = current * STRIDE;
			vertices[offset] = x;
			vertices[offset + 1] = y;
			vertices[offset + 2] = z;
			vertices[offset + 3] = nx;
			vertices[offset + 4] = ny;
			vertices[offset + 5] = nz;
			vertices[offset + 6] = u;
			vertices[offset + 7] = v;
			vertices[offset + 8] = r;
			vertices[offset + 9] = g;
			vertices[offset + 10] = b;
			vertices[offset + 11] = a;
			lights[current] = light;
			current = -1;
		}
	}
}
