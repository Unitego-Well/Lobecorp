package org.unitego.lobecorp.client.photon.emitter.model;

import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.MaterialSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.emitter.model.PhotonModelEmitterConfig;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Material;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Transform;
import org.unitego.lobecorp.util.photon.model.PhotonModelSamplerUtil.SampleParameters;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.model.editor.PhotonModelPreviewMaterialUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 原生时间轴只写本实例的槽，模型定义和其他实例不受影响。
public class PhotonModelEmitterRuntime {
	/// 欧拉角配置使用度，蒙皮采样使用弧度。
	private static final float DEGREES_PER_RADIAN = 180f / (float) Math.PI;
	/// 部件、骨骼和形状键动画的稳定路径前缀。
	public static final String PART_PREFIX = "parts.";
	/// 骨骼 TRS 动画属性使用的稳定前缀。
	public static final String BONE_PREFIX = "bones.";
	/// 形状键权重动画属性使用的稳定前缀。
	public static final String MORPH_PREFIX = "morphs.";
	public final RuntimeValue<Boolean> enabled;
	public final RuntimeValue<Integer> duration;
	public final RuntimeValue<Integer> startDelay;
	public final RuntimeValue<Boolean> looping;
	public final RuntimeValue<Boolean> paused;
	public final RuntimeValue<Float> playbackSpeed;
	public final RuntimeValue<Float> animationOffset;
	public final RuntimeValue<Float> length;
	public final RuntimeValue<Float> width;
	public final RuntimeValue<Float> opacity;
	public final RuntimeValue<NumberFunction> color;
	public final RendererSetting.Runtime renderer;
	private final RendererSetting.Runtime modelRenderer;
	private final PhotonModelEmitterConfig config;
	private final Map<String, RuntimeValue<?>> dynamicSlots = new LinkedHashMap<>();
	/// 缺失 ID 的缓存绑定仅写本实例临时槽，不进入资产定义或属性菜单。
	private final Map<String, RuntimeValue<?>> missingSlots = new LinkedHashMap<>();
	private final Map<String, Object> dynamicDefaults = new LinkedHashMap<>();
	private List<RuntimeBinding> dynamicBindings = List.of();
	private final Map<String, AnimatedPropertyType> dynamicProperties = new LinkedHashMap<>();
	/// 仅缓存模型私有着色器槽的材质副本；原生纹理材质继续实时复制，保留其可变参数。
	private @Nullable Map<RendererKey, CachedRenderer> renderers;

	private record RendererKey(Material material, boolean doubleSided) {
	}

	private record CachedRenderer(String data, String texture, boolean overlay, List<List<Object>> settings,
	                              RendererSetting.Runtime value) {
	}

	public PhotonModelEmitterRuntime(PhotonModelEmitterConfig config) {
		this.config = config;
		enabled = new RuntimeValue<>(() -> config.enabled);
		duration = new RuntimeValue<>(() -> config.duration);
		startDelay = new RuntimeValue<>(() -> config.startDelay);
		looping = new RuntimeValue<>(() -> config.looping);
		paused = new RuntimeValue<>(() -> config.paused);
		playbackSpeed = new RuntimeValue<>(() -> config.playbackSpeed);
		animationOffset = new RuntimeValue<>(() -> config.animationOffset);
		length = new RuntimeValue<>(config::getLength);
		width = new RuntimeValue<>(config::getWidth);
		opacity = new RuntimeValue<>(() -> config.opacity);
		color = new RuntimeValue<>(() -> NumberFunction.color(config.color));
		renderer = new RendererSetting.Runtime(config.renderer) {
		};
		modelRenderer = new RendererSetting.Runtime(config.modelRenderer) {
		};
	}

	public void syncDefinition(PhotonModelAsset asset) {
		if (renderers != null) {
			renderers.clear();
		}

		var bindings = new ArrayList<RuntimeBinding>();

		for (var part : asset.parts) {
			addTransform(bindings, PART_PREFIX + part.id + ".", part.transform);
		}

		for (var bone : asset.bones) {
			addTransform(bindings, BONE_PREFIX + bone.id + ".", bone.bind);
		}

		for (var morph : asset.morphs) {
			addBinding(bindings, MORPH_PREFIX + morph.id + ".weight", morph.weight,
					ConfigValueType.FLOAT, "morph_weight");
		}

		var paths = bindings.stream().map(binding -> binding.path).toList();
		dynamicSlots.keySet().retainAll(paths);
		dynamicDefaults.keySet().retainAll(paths);
		dynamicProperties.keySet().retainAll(paths);
		paths.forEach(missingSlots::remove);
		dynamicBindings = List.copyOf(bindings);
	}

	/// 切换默认与覆盖时清空非活动槽，活动侧的时间轴覆盖继续生效。
	public RendererSetting.Runtime renderer() {
		if (config.overrideRenderer) {
			modelRenderer.clear();
			return renderer;
		}

		renderer.clear();
		return modelRenderer;
	}

	/// 部件双面与逐面纹理仅覆盖本次绘制的材质副本，其余设置跟随模型默认或实例覆盖。
	public RendererSetting.Runtime rendererFor(@Nullable Material material, boolean doubleSided) {
		var renderer = renderer();
		RendererSetting.Runtime result;

		if (material == null || material.materialData.isEmpty()) {
			result = materialRenderer(renderer, material, doubleSided);
		} else {
			var key = new RendererKey(material, doubleSided);
			var settings = materialState(renderer.getMaterials());
			var cache = rendererCache();
			var cached = cache.get(key);

			if (cached == null || !cached.data().equals(material.materialData) || !cached.texture().equals(material.texture)
					|| cached.overlay() != material.overlay || !cached.settings().equals(settings)) {
				result = materialRenderer(renderer, material, doubleSided);
				cache.put(key, new CachedRenderer(material.materialData, material.texture, material.overlay, settings, result));
			} else {
				result = cached.value();
			}
		}

		result.layer.set(renderer.getLayer());
		result.cull.set(renderer.getCull());
		result.orderInLayer.set(renderer.getOrderInLayer());
		result.vertexSortingMode.set(renderer.getVertexSortingMode());
		result.compositeMode.set(renderer.getCompositeMode());
		result.writeCustomMask.set(renderer.isWriteCustomMask());
		result.maskGroup.set(renderer.getMaskGroup());
		result.maskAlphaCutoff.set(renderer.getMaskAlphaCutoff());
		return result;
	}

	private RendererSetting.Runtime materialRenderer(RendererSetting.Runtime renderer, @Nullable Material material, boolean doubleSided) {
		var result = new RendererSetting.Runtime(config.renderer()) {
		};
		result.materials.set(renderer.getMaterials().stream()
				.map(setting -> PhotonModelPreviewMaterialUtil.materialSetting(setting, material, doubleSided)).toList());
		return result;
	}

	/// 热替换前已存在的实例没有执行新增字段初始化器，首次使用时补建缓存。
	private Map<RendererKey, CachedRenderer> rendererCache() {
		if (renderers == null) {
			renderers = new LinkedHashMap<>();
		}

		return renderers;
	}

	private static List<List<Object>> materialState(List<MaterialSetting> materials) {
		var states = new ArrayList<List<Object>>(materials.size());

		for (var setting : materials) {
			var blend = setting.getBlendMode();
			states.add(List.of(setting.isDepthMask(), setting.isDepthTest(), setting.isCull(), blend.isEnableBlend(),
					blend.getBlendFunc(), blend.getSrcColorFactor(), blend.getDstColorFactor(), blend.getSrcAlphaFactor(), blend.getDstAlphaFactor()));
		}

		return states;
	}

	private void addTransform(List<RuntimeBinding> bindings, String prefix, Transform transform) {
		addBinding(bindings, prefix + "position", vector(transform.translation),
				ConfigValueType.NUMBER_FUNCTION3, "position");
		addBinding(bindings, prefix + "rotation", vector(transform.rotation.getEulerAnglesXYZ(new Vector3f()).mul(DEGREES_PER_RADIAN)),
				ConfigValueType.NUMBER_FUNCTION3, "rotation");
		addBinding(bindings, prefix + "scale", vector(transform.scale),
				ConfigValueType.NUMBER_FUNCTION3, "scale");
	}

	private void addBinding(List<RuntimeBinding> bindings, String path, Object value,
	                        ConfigValueType type, String label) {
		dynamicDefaults.put(path, value);
		dynamicSlots.computeIfAbsent(path, key -> new RuntimeValue<>(() -> dynamicDefaults.get(key)));
		bindings.add(new RuntimeBinding(path, PhotonModelTextUtil.key(label), type,
				object -> ((PhotonModelEmitter) object).runtime().slot(path)));
	}

	private static NumberFunction3 vector(Vector3f value) {
		return new NumberFunction3(value.x, value.y, value.z);
	}

	public List<AnimatedPropertyType> dynamicProperties() {
		return dynamicBindings.stream().map(binding -> dynamicProperties.computeIfAbsent(binding.path,
				_ -> ConfigPropertyType.fromBinding(binding))).toList();
	}

	@Nullable
	public RuntimeBinding resolveDynamicBinding(String path) {
		return dynamicBindings.stream().filter(binding -> binding.path.equals(path)).findFirst().orElse(null);
	}

	public RuntimeValue<?> slot(String path) {
		var present = dynamicBindings.stream().anyMatch(binding -> binding.path.equals(path));

		if (present) {
			return dynamicSlots.get(path);
		}

		return missingSlots.computeIfAbsent(path, key -> new RuntimeValue<>(() -> neutralValue(key)));
	}

	private static Object neutralValue(String path) {
		if (path.startsWith(MORPH_PREFIX)) {
			return 0f;
		}

		var value = path.endsWith(".scale") ? 1f : 0f;
		return new NumberFunction3(value, value, value);
	}

	public SampleParameters parameters(PhotonModelAsset asset, PhotonModelEmitterConfig config, RandomSource random, float progress) {
		var parameters = new SampleParameters();
		parameters.length = nonnegative(length.get(), asset.referenceLength);
		parameters.width = nonnegative(width.get(), asset.referenceWidth);
		parameters.opacity = Math.clamp(nonnegative(opacity.get(), 1), 0, 1);
		parameters.loop = looping.get();
		parameters.anchor = config.anchor;

		for (var part : asset.parts) {
			var transform = overriddenTransform(PART_PREFIX + part.id + ".", random, progress);

			if (transform != null) {
				parameters.partOverrides.put(part.id, transform);
			}
		}

		for (var bone : asset.bones) {
			var transform = overriddenTransform(BONE_PREFIX + bone.id + ".", random, progress);

			if (transform != null) {
				parameters.boneOverrides.put(bone.id, transform);
			}
		}

		for (var morph : asset.morphs) {
			var slot = dynamicSlots.get(MORPH_PREFIX + morph.id + ".weight");

			if (slot != null && slot.isOverridden()) {
				parameters.morphOverrides.put(morph.id, ((Number) slot.get()).floatValue());
			}
		}

		return parameters;
	}

	@Nullable
	private Transform overriddenTransform(String prefix, RandomSource random, float progress) {
		var position = vectorOverride(prefix + "position", random, progress);
		var rotation = vectorOverride(prefix + "rotation", random, progress);
		var scale = vectorOverride(prefix + "scale", random, progress);

		if (position == null && rotation == null && scale == null) {
			return null;
		}

		var transform = new Transform();
		transform.translation = position;
		transform.rotation = rotation == null ? null : new Quaternionf().rotationXYZ(
				rotation.x / DEGREES_PER_RADIAN, rotation.y / DEGREES_PER_RADIAN, rotation.z / DEGREES_PER_RADIAN);
		transform.scale = scale;
		return transform;
	}

	@Nullable
	private Vector3f vectorOverride(String path, RandomSource random, float progress) {
		var slot = dynamicSlots.get(path);
		return slot != null && slot.isOverridden() ? ((NumberFunction3) slot.get()).get(random, progress) : null;
	}

	private static float nonnegative(float value, float fallback) {
		return Float.isFinite(value) ? Math.max(0, value) : fallback;
	}

	public void clear() {
		if (renderers != null) {
			renderers.clear();
		}

		enabled.clear();
		duration.clear();
		startDelay.clear();
		looping.clear();
		paused.clear();
		playbackSpeed.clear();
		animationOffset.clear();
		length.clear();
		width.clear();
		opacity.clear();
		color.clear();
		renderer.clear();
		modelRenderer.clear();
		dynamicSlots.values().forEach(RuntimeValue::clear);
		missingSlots.values().forEach(RuntimeValue::clear);
	}
}
