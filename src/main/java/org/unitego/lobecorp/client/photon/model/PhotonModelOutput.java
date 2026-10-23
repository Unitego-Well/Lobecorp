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
import org.unitego.lobecorp.util.photon.model.PhotonModelGeometryUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelSourceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceOwnerUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Stage;
import org.unitego.lobecorp.util.photon.performance.PhotonPerformanceTraceUtil.Metric;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/// 独立模型输出复用Photon生命周期与原生材质批次；绘制回调只消费准备后的数据。
@ParametersAreNonnullByDefault
public class PhotonModelOutput extends Emitter implements PhotonEmissionAccess {
	@Persisted(subPersisted = true)
	public final PhotonModelOutputConfig config = new PhotonModelOutputConfig();
	private final RendererSetting.Runtime modelRenderer = new RendererSetting.Runtime(config.modelRenderer) {
	};
	private final RendererSetting.Runtime overrideRenderer = new RendererSetting.Runtime(config.renderer) {
	};
	private final PhotonModelClock clock = new PhotonModelClock();
	private final Map<String, RuntimeValue<?>> slots = new LinkedHashMap<>();
	private final Map<Object, PhotonModelClip.Sample> playback = new LinkedHashMap<>();
	private static final Comparator<PhotonModelClip.Sample> PLAYBACK_ORDER = Comparator.comparingInt(PhotonModelClip.Sample::priority)
			.thenComparingLong(PhotonModelClip.Sample::order);
	private CompoundTag original = new CompoundTag();
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
		original = tag.copy();
	}

	public CompoundTag source() {
		return original.copy();
	}

	public void prepare() throws IOException {
		if (sampler == null) {
			var definition = loadDefinition();
			var topology = PhotonModelTopologyUtil.prepare(definition);
			sampler = new PhotonModelPoseSampler(topology);
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

	private PhotonModelDefinition loadDefinition() throws IOException {
		return PhotonModelSourceUtil.load(config).definition();
	}

	private PhotonModelDefinition definition() {
		if (sampler == null) {
			throw new IllegalStateException("模型尚未准备。");
		}

		return sampler.prepared().definition();
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
		} else if (PhotonPerformanceTraceUtil.enabled(Stage.MODEL_SAMPLE)) {
			PhotonPerformanceTraceUtil.counter(PhotonPerformanceOwnerUtil.object(this), Metric.CACHE_HIT, 1);
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
			bakeModelGroup(settings, out, camera, partialTicks, sampled, group, world, color, light);
		}
	}

	private void bakeModelGroup(PhotonViewSettings settings, List<PhotonWorldRenderState.DrawJob> out, Camera camera,
	                            float partialTicks, PhotonModelPoseSampler.Frame sampled, int group,
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
						int triangles = PhotonModelGeometryUtil.write(output, sampled, group, world, groupColor, light);

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
						(failure != null ? "invalid" : sampler == null ? "missing" : "ok"))));

		if (failure != null) {
			container.addChild(view.fxObjectInfoView.createInformation(Component.literal(getName()),
					() -> Component.literal(Objects.toString(failure, ""))));
		}
	}

	private void invalidate() {
		materials = null;
		sampler = null;
		dynamic = null;
		playback.clear();
		sampledInput = null;
		frame = null;

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
}
