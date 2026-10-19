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
import com.lowdragmc.photon.client.gameobject.emitter.Emitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.render.PhotonCameraUtils;
import com.lowdragmc.photon.client.render.PhotonViewSettings;
import com.lowdragmc.photon.client.render.PhotonWorldRenderState;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.GrassColor;
import org.joml.Matrix4f;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Tint;
import org.unitego.lobecorp.config.photon.model.PhotonModelOutputConfig;
import org.unitego.lobecorp.registry.photon.client.LcPhotonModelOutputTypes;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceJsonUtil;
import org.unitego.lobecorp.util.photon.compat.PhotonResourceLimits;
import org.unitego.lobecorp.util.photon.model.PhotonModelAssetUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelCodec;
import org.unitego.lobecorp.util.photon.model.PhotonModelGeometryUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler;
import org.unitego.lobecorp.util.photon.model.PhotonModelTopologyUtil;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/// 独立模型输出复用Photon生命周期与原生材质批次；绘制回调只消费准备后的数据。
@ParametersAreNonnullByDefault
public class PhotonModelOutput extends Emitter {
	@Persisted(subPersisted = true)
	public final PhotonModelOutputConfig config = new PhotonModelOutputConfig();
	private final RendererSetting.Runtime modelRenderer = new RendererSetting.Runtime(config.modelRenderer) {
	};
	private final RendererSetting.Runtime overrideRenderer = new RendererSetting.Runtime(config.renderer) {
	};
	private final PhotonModelClock clock = new PhotonModelClock();
	private final Map<String, RuntimeValue<?>> slots = new LinkedHashMap<>();
	private CompoundTag original = new CompoundTag();
	private @Nullable PhotonModelPoseSampler sampler;
	private @Nullable PhotonModelMaterials materials;
	private @Nullable PhotonModelPoseSampler.Input sampledInput;
	private @Nullable PhotonModelPoseSampler.Frame frame;
	private List<Integer> groupOrder = List.of();
	private @Nullable String failure;
	private boolean preparedOverride;
	private boolean measure;

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

		if (value == null) {
			throw new IllegalArgumentException("模型运行属性不存在：" + path);
		}

		return value;
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
				value("looping", Boolean.class), value("duration", Integer.class), value("startDelay", Integer.class),
				value("playbackSpeed", Float.class));
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

		if (materials == null || preparedOverride != config.overrideRenderer || materials.materialsChanged()) {
			materials = new PhotonModelMaterials(config.overrideRenderer ? config.renderer : config.modelRenderer,
					rendererRuntime(), sampler.prepared());
			preparedOverride = config.overrideRenderer;
		}

		failure = null;
	}

	private PhotonModelDefinition loadDefinition() throws IOException {
		var snapshot = config.assetSnapshot();
		boolean empty = snapshot instanceof ByteArrayTag bytes && bytes.getAsByteArray().length == 0
				|| snapshot instanceof StringTag(var text) && text.isBlank();
		var key = config.source.name() + "|" + config.resource + "|" + config.file + "|" + config.flipObjV;

		if (!empty && (config.source == PhotonModelOutputConfig.Source.EMBEDDED || key.equals(config.snapshotSource))) {
			return PhotonModelAssetUtil.read(snapshot, config.assetMaterialDependencies);
		}

		if (config.source == PhotonModelOutputConfig.Source.EMBEDDED) {
			throw new IOException("模型实例没有内嵌资产。");
		}

		byte[] source;

		if (config.source == PhotonModelOutputConfig.Source.FILE) {
			var path = Path.of(config.file);

			if (Files.size(path) > PhotonResourceLimits.DEFAULT.sourceBytes()) {
				throw new IOException("模型来源超过读取预算。");
			}

			try (var input = Files.newInputStream(path)) {
				source = input.readNBytes((int) PhotonResourceLimits.DEFAULT.sourceBytes() + 1);
			}
		} else {
			if (config.resource == null) {
				throw new IOException("模型资源ID为空。");
			}

			try (var input = Minecraft.getInstance().getResourceManager().open(config.resource)) {
				source = input.readNBytes((int) PhotonResourceLimits.DEFAULT.sourceBytes() + 1);
			}
		}

		if (source.length > PhotonResourceLimits.DEFAULT.sourceBytes()) {
			throw new IOException("模型来源超过读取预算。");
		}

		return PhotonModelCodec.read(PhotonResourceJsonUtil.read(source, PhotonResourceLimits.DEFAULT).getAsJsonObject());
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
		modelRenderer.clear();
		overrideRenderer.clear();
		sampledInput = null;
		frame = null;

		try {
			prepare();
			clock.reset(settings(), false);
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
		}
	}

	@Override
	public int getParticleAmount() {
		return sampler != null && clock.visible(settings()) ? 1 : 0;
	}

	@Override
	public int getLifetime() {
		return value("duration", Integer.class);
	}

	@Override
	public int getStartDelay() {
		return value("startDelay", Integer.class);
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
	protected void bakeBatches(PhotonViewSettings settings, List<PhotonWorldRenderState.DrawJob> out,
	                           Camera camera, float partialTicks) {
		if (getParticleAmount() == 0 || materials == null || sampler == null) {
			return;
		}

		var input = new PhotonModelPoseSampler.Input(clock.animationTime(partialTicks, timeScale(), settings())
				+ value("animationOffset", Float.class), config.clip, isLooping(), value("length", Float.class),
				value("width", Float.class), config.anchor, value("opacity", Float.class), Map.of());

		if (!input.equals(sampledInput)) {
			frame = sampler.sample(input, measure);
			sampledInput = input;
		}

		var sampled = Objects.requireNonNull(frame);
		var origin = PhotonCameraUtils.renderOrigin(camera);
		var world = new Matrix4f().translation(-origin.x, -origin.y, -origin.z).mul(transform().localToWorldMatrix());
		var function = value("color", NumberFunction.class);
		int color = function.get(getT(partialTicks), () -> getMemRandom(function)).intValue();
		int light = getLightColor(BlockPos.containing(transform().position().x, transform().position().y, transform().position().z));

		for (int group : groupOrder) {
			var topology = sampled.prepared().groups().get(group);
			var material = sampled.prepared().definition().materials().get(topology.material());
			int groupColor = material != null && material.tint() == Tint.GRASS
					? ARGB.multiply(color, ARGB.opaque(GrassColor.getDefaultColor())) : color;
			bakeGroup(settings, out, camera, partialTicks, materials.group(group), geometryMode(),
					(output, _, _) -> PhotonModelGeometryUtil.write(output, sampled, group, world, groupColor, light));
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
