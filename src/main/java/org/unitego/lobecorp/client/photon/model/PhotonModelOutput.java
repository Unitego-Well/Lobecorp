package org.unitego.lobecorp.client.photon.model;

import com.lowdragmc.photon.client.fx.PreloadContext;
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
import java.util.Comparator;
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

		/// 静态程序可共享，每个输出仍须独立创建工作区。
		default PhotonModelPoseSampler sampler() throws IOException {
			return new PhotonModelPoseSampler(get());
		}
	}

	@Persisted(subPersisted = true)
	public final PhotonModelOutputConfig config;
	private final RendererSetting.Runtime modelRenderer;
	private final RendererSetting.Runtime overrideRenderer;
	private @Nullable Supplier<PhotonModelTopologyUtil.Prepared> compiledSupplier;
	private final PhotonModelClock clock = new PhotonModelClock();
	private final NormalCache normalCache = new NormalCache();
	private final PhotonModelPoseBuffer poseBuffer = new PhotonModelPoseBuffer();
	private @Nullable PhotonModelPoseSampler.Workspace poseWorkspace;
	private final Map<String, RuntimeValue<?>> slots = new LinkedHashMap<>();
	private final Map<Object, PhotonModelClip.Sample> playback = new LinkedHashMap<>();
	private @Nullable PhotonModelClip.Sample playbackSelection;
	private static final Comparator<PhotonModelClip.Sample> PLAYBACK_ORDER = Comparator.comparingInt(PhotonModelClip.Sample::priority)
			.thenComparingLong(PhotonModelClip.Sample::order);
	private static final String SAMPLE_FAILURE_OPERATION = "采样";
	private CompoundTag original = new CompoundTag();
	private @Nullable PhotonFrozenTag compiledOriginal;
	private @Nullable PhotonModelPoseSampler sampler;
	private @Nullable PhotonModelMaterials materials;
	private @Nullable PhotonModelBindings dynamic;
	private @Nullable PhotonModelPoseSampler.Input sampledInput;
	private @Nullable PhotonModelPoseSampler.Frame publicFrame;
	private @Nullable PhotonModelClock.Settings settingsSnapshot;
	private List<Integer> groupOrder = List.of();
	private final PhotonModelFailureState failures = new PhotonModelFailureState();
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
		return !id.isEmpty() && sampler != null && sampler.clip(id) != null;
	}

	public float animationDuration(String id) {
		var clip = sampler == null ? null : sampler.clip(id);
		return Objects.requireNonNull(clip, "模型动画片段不存在：" + id).duration();
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

		playbackSelection = playback.values().stream().max(PLAYBACK_ORDER).orElse(null);
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
		var current = new PhotonModelClock.Settings(value("enabled", Boolean.class), value("paused", Boolean.class),
				value("looping", Boolean.class), getLifetime(), getStartDelay(),
				nonnegative(value("playbackSpeed", Float.class), 1));

		if (!current.equals(settingsSnapshot)) {
			settingsSnapshot = current;
		}

		return settingsSnapshot;
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
		settingsSnapshot = null;
		playbackSelection = null;
		closeWorkspace();
		publicFrame = null;
		poseBuffer.clear();
	}

	public void prepare() throws IOException {
		if (compiledSupplier instanceof PreparedSource source && !source.matches(config)) {
			compiledPreparation(null);
			}
		if (sampler == null) {
			try {
				sampler = compiledSupplier == null ? PhotonModelSourceUtil.prepare(config)
						: compiledSupplier instanceof PreparedSource source ? source.sampler()
						: new PhotonModelPoseSampler(compiledSupplier.get());
			} catch (UncheckedIOException invalid) {
				throw invalid.getCause();
			}
			var topology = sampler.prepared();
			groupOrder = topology.groupOrder();
			closeWorkspace();
			poseWorkspace = sampler.newWorkspace();
		}

		if (dynamic == null) {
			dynamic = new PhotonModelBindings(sampler.prepared().definition());
		}

		if (materials == null || preparedOverride != config.overrideRenderer || materials.materialsChanged()) {
			materials = new PhotonModelMaterials(config.overrideRenderer ? config.renderer : config.modelRenderer,
					rendererRuntime(), sampler.prepared());
			preparedOverride = config.overrideRenderer;
		}

		failures.clear();
	}

	/// 接入原生预加载：按模型实际分组准备材质、纹理和CPU管线，不生成运行时间线。
	@Override
	public void preload(PreloadContext context) {
		context.onRenderThread(() -> {
			try {
				prepare();
			} catch (IOException invalid) {
				throw new IllegalStateException("模型预加载失败：" + getName(), invalid);
			}

			for (int group : groupOrder) {
				var renderer = materials.group(group);
				preloadGroup(context, renderer, geometryMode(), null, true, false,
						renderer.isWriteCustomMask() || context.isAnimated(this, "renderer.writeCustomMask"));
			}
		});
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

		modelRenderer.clear();
		overrideRenderer.clear();
		playback.clear();
		playbackSelection = null;
		settingsSnapshot = null;
		sampledInput = null;
		publicFrame = null;
		poseBuffer.clear();

		try {
			prepare();
			if (poseWorkspace == null && sampler != null) {
				poseWorkspace = sampler.newWorkspace();
			}
			clock.reset(settings(), preserveParticles);
		} catch (IOException | RuntimeException error) {
			failPreparation("重置", error);
		}
	}

	@Override
	protected void update(float dt) {
		if (materials != null && (preparedOverride != config.overrideRenderer || materials.materialsChanged())) {
			try {
				prepare();
			} catch (IOException error) {
				failPreparation("更新", error);
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
			closeWorkspace();
			sampledInput = null;
			publicFrame = null;
			playback.clear();
			playbackSelection = null;
			settingsSnapshot = null;
			poseBuffer.clear();
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
		clock.clear();
		sampledInput = null;
		publicFrame = null;
		poseBuffer.clear();
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
		return failures.message();
	}

	public void measure(boolean enabled) {
		measure = enabled;
		sampledInput = null;
	}

	public @Nullable PhotonModelPoseSampler.Frame sampledFrame() {
		return publicFrame;
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
		var sampled = sampleLocalPose(partialTicks);
		return sampled == null ? null : publicFrame();
	}

	private @Nullable PhotonModelPoseSampler.Frame publicFrame() {
		if (!poseBuffer.complete()) {
			return null;
		}

		if (publicFrame == null) {
			publicFrame = poseBuffer.frame();
			poseBuffer.invalidateFrameSnapshot();
		}

		return publicFrame;
	}

	private @Nullable PhotonModelPoseBuffer sampleLocalPose(float partialTicks) {
		if (getParticleAmount() == 0 || sampler == null) {
			return null;
		}

		if (poseWorkspace == null) {
			closeWorkspace();
			poseWorkspace = sampler.newWorkspace();
		}

		long scope = PhotonPerformanceTraceUtil.enabled(Stage.MODEL_SAMPLE)
				? PhotonPerformanceTraceUtil.begin(Stage.MODEL_SAMPLE, PhotonPerformanceOwnerUtil.object(this)) : 0;
		boolean completed = false;

		try {
			var sampled = evaluateLocalPose(partialTicks);
			completed = sampled != null;

			if (sampled != null) {
				failures.clear(SAMPLE_FAILURE_OPERATION);
			}

			return sampled;
		} catch (IllegalArgumentException | IllegalStateException invalid) {
			failSampling(invalid);
			return null;
		} finally {
			PhotonPerformanceTraceUtil.end(scope, completed);
		}
	}

	private @Nullable PhotonModelPoseBuffer evaluateLocalPose(float partialTicks) {
		var sampler = this.sampler;

		if (sampler == null || poseWorkspace == null) {
			return null;
		}

		var bindings = Objects.requireNonNull(dynamic);
		// 无片段时局部几何与时间无关；外部TRS/Morph覆盖仍作为缓存键的一部分。
		var clipId = config.clip;
		boolean loop = isLooping();
		float time = clipId.isEmpty() ? 0 : clock.animationTime(partialTicks, nonnegative(timeScale(), 1), settings())
				+ value("animationOffset", Float.class);
		var selected = playbackSelection;

		if (selected != null) {
			clipId = selected.animation();
			loop = selected.effectiveLoop();
			var clip = sampler.clip(selected.animation());

			if (clip == null) {
				throw new IllegalArgumentException("模型片段不存在：" + selected.animation());
			}

			time = selected.assetTime(clip.duration(), clip.loop());
		}

		var input = new PhotonModelPoseSampler.Input(time, clipId, loop,
				nonnegative(value("length", Float.class), definition().referenceLength()),
				nonnegative(value("width", Float.class), definition().referenceWidth()), config.anchor,
				Math.clamp(nonnegative(value("opacity", Float.class), 1), 0, 1), bindings.morphWeights(),
				bindings.partOverrides(this, getT(partialTicks)), bindings.boneOverrides(this, getT(partialTicks)));

		if (!input.equals(sampledInput)) {
			try {
				sampler.sampleInto(input, measure || PhotonPerformanceTraceUtil.enabled(Stage.MODEL_POSE), poseWorkspace, poseBuffer);
			} catch (RuntimeException failure) {
				poseBuffer.failCandidate();
				sampledInput = null;
				throw failure;
			}

			if (!poseBuffer.complete()) {
				poseBuffer.failCandidate();
				sampledInput = null;
				return null;
			}

			sampledInput = input;
			publicFrame = null;

			if (PhotonPerformanceTraceUtil.enabled(Stage.MODEL_SAMPLE)) {
				PhotonPerformanceTraceUtil.counter(PhotonPerformanceOwnerUtil.object(this), Metric.CACHE_MISS, 1);
			}

			var timings = poseBuffer.timings();

			if (PhotonPerformanceTraceUtil.enabled(Stage.MODEL_POSE) && timings != null) {
				int node = PhotonPerformanceOwnerUtil.object(this);
				phase(node, "curves", timings.curvesNanos());
				phase(node, "deformation", timings.deformationNanos());
				phase(node, "normals_frame", timings.normalsNanos());
			}
		} else {
			if (PhotonPerformanceTraceUtil.enabled(Stage.MODEL_SAMPLE)) {
			PhotonPerformanceTraceUtil.counter(PhotonPerformanceOwnerUtil.object(this), Metric.CACHE_HIT, 1);
		}
		}

		return poseBuffer;
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

		var sampled = sampleLocalPose(partialTicks);

		if (sampled == null) {
			return;
		}

		var origin = PhotonCameraUtils.renderOrigin(camera);
		var world = new Matrix4f().translation(-origin.x, -origin.y, -origin.z).mul(transform().localToWorldMatrix());
		var function = value("color", NumberFunction.class);
		int color = function.get(getT(partialTicks), () -> getMemRandom(function)).intValue();
		int light = getLightColor(BlockPos.containing(transform().position().x, transform().position().y, transform().position().z));

		for (int group : groupOrder) {
			bakeModelGroup(settings, out, camera, partialTicks, sampled, group, world, color, light);
		}
	}

	private void bakeModelGroup(PhotonViewSettings settings, List<PhotonWorldRenderState.DrawJob> out, Camera camera,
	                            float partialTicks, PhotonModelPoseBuffer sampled, int group,
	                            Matrix4f world, int color, int light) {
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
						int triangles = PhotonModelGeometryUtil.writePrimitive(output, sampled, group, world,
								groupColor, light, normalCache);

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
						(failures.current() != null ? "invalid" : sampler == null ? "missing" : "ok"))));
		var failure = failures.current();

		if (failure != null) {
			container.addChild(view.fxObjectInfoView.createInformation(Component.literal(getName()),
					() -> {
						var current = failures.current();
						return Component.literal(current == null ? "" : current.detail());
					}));
		}
	}

	private void failPreparation(String operation, Exception error) {
		reportFailure(operation, error);
		clock.clear();
	}

	private void failSampling(RuntimeException error) {
		reportFailure(SAMPLE_FAILURE_OPERATION, error);
	}

	private void reportFailure(String operation, Throwable error) {
		if (failures.record(operation, error)) {
			Photon.LOGGER.error("模型输出{}失败：{}", operation, getName(), error);
		}
	}

	private void invalidate() {
		compiledSupplier = null;
		materials = null;
		sampler = null;
		dynamic = null;
		playback.clear();
		playbackSelection = null;
		settingsSnapshot = null;
		sampledInput = null;
		publicFrame = null;
		closeWorkspace();
		poseBuffer.clear();

		try {
			prepare();
		} catch (IOException | RuntimeException error) {
			failPreparation("配置变更", error);
		}
	}

	@Override
	public IFXObject shallowCopy() {
		var copied = (PhotonModelOutput) deepCopy();
		copied.sampler = sampler;
		copied.groupOrder = List.copyOf(groupOrder);
		copied.closeWorkspace();
		copied.publicFrame = null;
		copied.sampledInput = null;
		copied.poseBuffer.clear();
		if (copied.sampler != null) {
			copied.poseWorkspace = copied.sampler.newWorkspace();
		}
		return copied;
	}

	private void closeWorkspace() {
		if (poseWorkspace != null) {
			try {
				poseWorkspace.close();
			} catch (IllegalStateException busy) {
				// 销毁顺序由客户端生命周期保证；忙时不能清掉仍在求值的私有 scratch。
			}
			poseWorkspace = null;
		}
	}
}
