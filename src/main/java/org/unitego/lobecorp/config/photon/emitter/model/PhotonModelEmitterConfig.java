package org.unitego.lobecorp.config.photon.emitter.model;

import com.lowdragmc.lowdraglib2.configurator.ConfiguratorParser;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ColorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Anchor;
import org.unitego.lobecorp.util.photon.model.PhotonModelIOUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelMaterialDataUtil;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/// 专用单模型实例的保存配置，与旧 OBJ 粒子配置分离。
public class PhotonModelEmitterConfig implements IConfigurable, IPersistedSerializable {
	/// 默认实例寿命，单位为游戏 tick。
	public static final int DEFAULT_DURATION = 100;
	/// 默认未染色的 ARGB 颜色。
	public static final int DEFAULT_COLOR = 0xFFFFFFFF;
	/// 模型长度和宽度默认使用一个模型单位。
	public static final float DEFAULT_DIMENSION = 1;
	/// 默认模型动画播放倍率。
	public static final float DEFAULT_PLAYBACK_SPEED = 1;
	/// 支持的外部模型文件后缀。
	public static final String MODEL_SUFFIX = ".lcmodel.json";
	/// 普通 OBJ 回退的文件后缀。
	public static final String OBJ_SUFFIX = ".obj";
	/// 内嵌模型沿用原保存键，使用 UTF-8 字节数组避开 NBT 字符串长度上限。
	private static final String ASSET_JSON_KEY = "assetJson";
	/// 由内嵌资产派生的资源引用，仅供原生 FXPack 打包与垃圾回收扫描。
	private static final String ASSET_DEPENDENCIES_KEY = "assetDependencies";
	/// 材质的结构化派生依赖供原生打包重写，载入时回填资产内的材质快照。
	private static final String ASSET_MATERIAL_DEPENDENCIES_KEY = "assetMaterialDependencies";
	/// 模型默认渲染设置是新定义的必需字段，不读取缺少该字段的旧配置。
	private static final String MODEL_RENDERER_KEY = "modelRenderer";

	@Persisted
	public Source source = Source.EMBEDDED;
	@Nullable
	@Persisted
	public Identifier resource;
	@Persisted
	public String file = "";
	public String assetJson = "";
	@Persisted
	public boolean flipObjV;
	@Persisted
	public boolean enabled = true;
	@Persisted
	public int duration = DEFAULT_DURATION;
	@Persisted
	public int startDelay;
	@Persisted
	public boolean looping = true;
	@Persisted
	public boolean paused;
	@Persisted
	public String snapshotSource = "";
	@Persisted
	public String clip = "";
	@Persisted
	public float playbackSpeed = DEFAULT_PLAYBACK_SPEED;
	@Persisted
	public float animationOffset;
	@Persisted
	public boolean useReferenceDimensions = true;
	private float referenceLength = DEFAULT_DIMENSION;
	private float referenceWidth = DEFAULT_DIMENSION;
	@Persisted
	public float length = DEFAULT_DIMENSION;
	@Persisted
	public float width = DEFAULT_DIMENSION;
	@Persisted
	public Anchor anchor = Anchor.START;
	@Persisted
	public float opacity = 1;
	@Persisted
	public int color = DEFAULT_COLOR;
	@Persisted
	public boolean overrideRenderer;
	@Persisted(subPersisted = true)
	public final RendererSetting modelRenderer = createRenderer();
	@Persisted(subPersisted = true)
	public final RendererSetting renderer = createRenderer();

	@Override
	public void serialize(@NotNull ValueOutput output) {
		IPersistedSerializable.super.serialize(output);
		output.store(ASSET_JSON_KEY, ExtraCodecs.NBT, new ByteArrayTag(assetJson.getBytes(StandardCharsets.UTF_8)));
		output.store(ASSET_DEPENDENCIES_KEY, Identifier.CODEC.listOf(), assetDependencies());
		output.store(ASSET_MATERIAL_DEPENDENCIES_KEY, ExtraCodecs.NBT, assetMaterialDependencies());
	}

	private List<Identifier> assetDependencies() {
		if (assetJson.isBlank()) {
			return List.of();
		}

		try {
			return PhotonModelIOUtil.textureDependencies(assetJson).stream().map(Identifier::parse).toList();
		} catch (RuntimeException _) {
			// 损坏快照继续由模型载入逻辑报告，不让派生依赖阻止保存原配置。
			return List.of();
		}
	}

	@Override
	public void deserialize(@NotNull ValueInput input) {
		if (!(input.read(MODEL_RENDERER_KEY, ExtraCodecs.NBT).orElse(null) instanceof CompoundTag)) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("invalid_renderer"));
		}

		IPersistedSerializable.super.deserialize(input);
		var tag = input.read(ASSET_JSON_KEY, ExtraCodecs.NBT).orElse(null);

		if (tag instanceof ByteArrayTag bytes) {
			assetJson = new String(bytes.getAsByteArray(), StandardCharsets.UTF_8);
		} else if (tag instanceof StringTag(var legacy)) {
			assetJson = legacy;
		} else {
			assetJson = "";
		}

		var dependencies = input.read(ASSET_MATERIAL_DEPENDENCIES_KEY, ExtraCodecs.NBT).orElse(null);
		if (!(dependencies instanceof CompoundTag compound) || compound.isEmpty() || assetJson.isBlank()) {
			return;
		}

		try {
			if (compound.equals(PhotonModelIOUtil.materialDependencies(assetJson))) {
				return;
			}

			var asset = PhotonModelIOUtil.readJson(assetJson);
			PhotonModelMaterialDataUtil.applyDependencies(asset, compound);
			assetJson = PhotonModelIOUtil.writeJson(asset);
		} catch (RuntimeException _) {
			// 损坏快照继续由模型载入逻辑报告，保留原始资产数据。
		}
	}

	private CompoundTag assetMaterialDependencies() {
		if (assetJson.isBlank()) {
			return new CompoundTag();
		}

		try {
			return PhotonModelIOUtil.materialDependencies(assetJson);
		} catch (RuntimeException _) {
			// 损坏快照继续由模型载入逻辑报告，不让派生依赖阻止保存原配置。
			return new CompoundTag();
		}
	}

	private static RendererSetting createRenderer() {
		var renderer = new RendererSetting();
		renderer.getMaterials().add(new MaterialSetting(new TextureMaterial()));
		return renderer;
	}

	@Override
	public void buildConfigurator(ConfiguratorGroup father) {
		var settings = new ConfiguratorGroup(PhotonModelTextUtil.key("settings"), false);
		father.addConfigurator(settings);
		buildSource(settings);
		settings.addConfigurators(
				new BooleanConfigurator(PhotonModelTextUtil.key("enabled"), () -> enabled, value -> enabled = value, true, true),
				new NumberConfigurator(PhotonModelTextUtil.key("duration"), () -> duration, value -> duration = value.intValue(), DEFAULT_DURATION, true).setRange(1, Integer.MAX_VALUE),
				new NumberConfigurator(PhotonModelTextUtil.key("start_delay"), () -> startDelay, value -> startDelay = value.intValue(), 0, true).setRange(0, Integer.MAX_VALUE),
				new BooleanConfigurator(PhotonModelTextUtil.key("looping"), () -> looping, value -> looping = value, true, true),
				new BooleanConfigurator(PhotonModelTextUtil.key("pause"), () -> paused, value -> paused = value, false, true),
				new StringConfigurator(PhotonModelTextUtil.key("clip"), () -> clip, value -> clip = value, "", true),
				new NumberConfigurator(PhotonModelTextUtil.key("playback_speed"), () -> playbackSpeed, value -> playbackSpeed = value.floatValue(), DEFAULT_PLAYBACK_SPEED, true).setRange(0, Float.MAX_VALUE),
				new NumberConfigurator(PhotonModelTextUtil.key("animation_offset"), () -> animationOffset, value -> animationOffset = value.floatValue(), 0f, true),
				new BooleanConfigurator(PhotonModelTextUtil.key("use_reference_dimensions"), () -> useReferenceDimensions, value -> useReferenceDimensions = value, true, true),
				new NumberConfigurator(PhotonModelTextUtil.key("length"), this::getLength, value -> setLength(value.floatValue()), DEFAULT_DIMENSION, true).setRange(0, Float.MAX_VALUE),
				new NumberConfigurator(PhotonModelTextUtil.key("width"), this::getWidth, value -> setWidth(value.floatValue()), DEFAULT_DIMENSION, true).setRange(0, Float.MAX_VALUE),
				new SelectorConfigurator<>(PhotonModelTextUtil.key("anchor"), () -> anchor, value -> anchor = value, Anchor.START, true, List.of(Anchor.values()), value -> PhotonModelTextUtil.key("anchor." + value.name().toLowerCase(Locale.ROOT))),
				new NumberConfigurator(PhotonModelTextUtil.key("opacity"), () -> opacity, value -> opacity = value.floatValue(), 1f, true).setRange(0, 1),
				new ColorConfigurator(PhotonModelTextUtil.key("color"), () -> color, value -> color = value, DEFAULT_COLOR, true));
		var rendererGroup = new ConfiguratorGroup(PhotonModelTextUtil.key("renderer"), true);
		ConfiguratorParser.createConfigurators(rendererGroup, renderer);
		rendererGroup.setDisplay(overrideRenderer);
		father.addConfigurator(new BooleanConfigurator(PhotonModelTextUtil.key("override_renderer"),
				() -> overrideRenderer, value -> {
			overrideRenderer = value;
			rendererGroup.setDisplay(value);
		}, false, true));
		father.addConfigurator(rendererGroup);
	}

	public RendererSetting renderer() {
		return overrideRenderer ? renderer : modelRenderer;
	}

	private void buildSource(ConfiguratorGroup group) {
		group.addConfigurators(
				new SelectorConfigurator<>(PhotonModelTextUtil.key("source"), () -> source, value -> source = value, Source.EMBEDDED, true, List.of(Source.values()), value -> PhotonModelTextUtil.key("source." + value.name().toLowerCase(Locale.ROOT))),
				new StringConfigurator(PhotonModelTextUtil.key("resource"), () -> resource == null ? "" : resource.toString(), value -> resource = value.isBlank() ? null : Identifier.tryParse(value), "", true),
				new StringConfigurator(PhotonModelTextUtil.key("file"), () -> file, value -> file = value, "", true),
				new BooleanConfigurator(PhotonModelTextUtil.key("flip_obj_v"), () -> flipObjV, value -> flipObjV = value, false, true));
	}

	public void setReferenceDimensions(float modelLength, float modelWidth) {
		referenceLength = modelLength;
		referenceWidth = modelWidth;
	}

	public float getLength() {
		return useReferenceDimensions ? referenceLength : length;
	}

	public float getWidth() {
		return useReferenceDimensions ? referenceWidth : width;
	}

	private void setLength(float value) {
		width = getWidth();
		length = value;
		useReferenceDimensions = false;
	}

	private void setWidth(float value) {
		length = getLength();
		width = value;
		useReferenceDimensions = false;
	}

	public enum Source {
		EMBEDDED, RESOURCE, FILE
	}
}
