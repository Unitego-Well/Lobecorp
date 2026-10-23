package org.unitego.lobecorp.client.photon.model;

import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigPropertyType;
import com.lowdragmc.photon.client.fx.timeline.property.ConfigValueType;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.gameobject.RuntimeValue;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Rotation;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Vector3;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Transform;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Part;
import org.unitego.lobecorp.config.photon.model.PhotonModelDefinition.Bone;
import org.unitego.lobecorp.util.photon.model.PhotonModelPoseSampler.PartOverride;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// 模型部件/Morph通过原生属性槽驱动；未覆盖分量不截断片段本身的TRS动画。
public class PhotonModelBindings {
	private static final float DEGREES_PER_RADIAN = 180f / (float) Math.PI;
	private final PhotonModelDefinition definition;
	private final Map<String, RuntimeValue<?>> slots = new LinkedHashMap<>();
	private final Map<String, RuntimeBinding> bindings = new LinkedHashMap<>();
	private final List<AnimatedPropertyType> properties;
	private final List<String> partIds;
	private final List<String> boneIds;

	public PhotonModelBindings(PhotonModelDefinition definition) {
		this.definition = definition;
		partIds = definition.parts().stream().map(Part::id).toList();
		boneIds = definition.bones().stream().map(Bone::id).toList();

		for (var part : definition.parts()) {
			transform("parts." + part.id(), part.transform());
		}
		for (var bone : definition.bones()) {
			transform("bones." + bone.id(), bone.bind());
		}

		for (var morph : definition.morphs()) {
			add("morphs." + morph.id() + ".weight", "morph_weight", ConfigValueType.FLOAT, morph.weight());
		}

		properties = bindings.values().stream().map(ConfigPropertyType::fromBinding)
				.map(AnimatedPropertyType.class::cast).toList();
	}

	private void add(String path, String label, ConfigValueType type, Object value) {
		slots.put(path, new RuntimeValue<>(() -> value));
		bindings.put(path, new RuntimeBinding(path, "photon.lobecorp.model." + label, type,
				object -> ((PhotonModelOutput) object).slot(path)));
	}

	private void transform(String prefix, Transform transform) {
		var rotation = transform.rotation();
		var quaternion = new Quaternionf(rotation.x(), rotation.y(), rotation.z(), rotation.w());
		if (quaternion.lengthSquared() == 0) {
			quaternion.identity();
		}
		var angles = quaternion.normalize().getEulerAnglesXYZ(new Vector3f()).mul(DEGREES_PER_RADIAN);
		add(prefix + ".position", "position", ConfigValueType.NUMBER_FUNCTION3, function(transform.translation()));
		add(prefix + ".rotation", "rotation", ConfigValueType.NUMBER_FUNCTION3, new NumberFunction3(angles.x, angles.y, angles.z));
		add(prefix + ".scale", "scale", ConfigValueType.NUMBER_FUNCTION3, function(transform.scale()));
	}

	public @Nullable RuntimeValue<?> slot(String path) {
		return slots.get(path);
	}

	public @Nullable RuntimeBinding binding(String path) {
		return bindings.get(path);
	}

	public List<AnimatedPropertyType> properties() {
		return properties;
	}

	public Map<String, Float> morphWeights() {
		var result = new LinkedHashMap<String, Float>();

		for (var morph : definition.morphs()) {
			var slot = slots.get("morphs." + morph.id() + ".weight");

			if (slot.isOverridden()) {
				result.put(morph.id(), ((Number) slot.get()).floatValue());
			}
		}

		return Map.copyOf(result);
	}

	public Map<String, PartOverride> partOverrides(PhotonModelOutput owner, float progress) {
		return transformOverrides(owner, progress, "parts.", partIds);
	}

	public Map<String, PartOverride> boneOverrides(PhotonModelOutput owner, float progress) {
		return transformOverrides(owner, progress, "bones.", boneIds);
	}

	private Map<String, PartOverride> transformOverrides(PhotonModelOutput owner, float progress, String kind, List<String> ids) {
		var result = new LinkedHashMap<String, PartOverride>();

		for (var id : ids) {
			var prefix = kind + id + ".";
			var position = override(prefix + "position", owner, progress);
			var rotation = override(prefix + "rotation", owner, progress);
			var scale = override(prefix + "scale", owner, progress);

			if (position == null && rotation == null && scale == null) {
				continue;
			}

			Rotation quaternion = null;

			if (rotation != null) {
				var value = new Quaternionf().rotationXYZ(rotation.x() / DEGREES_PER_RADIAN,
						rotation.y() / DEGREES_PER_RADIAN, rotation.z() / DEGREES_PER_RADIAN);
				quaternion = new Rotation(value.x, value.y, value.z, value.w);
			}

			result.put(id, new PartOverride(position, quaternion, scale));
		}

		return Map.copyOf(result);
	}

	private @Nullable Vector3 override(String path, PhotonModelOutput owner, float progress) {
		var slot = slots.get(path);

		if (!slot.isOverridden()) {
			return null;
		}

		var value = ((NumberFunction3) slot.get()).get(progress, () -> owner.getMemRandom(slot));
		return new Vector3(value.x, value.y, value.z);
	}

	public void clear() {
		slots.values().forEach(RuntimeValue::clear);
	}

	private static NumberFunction3 function(Vector3 value) {
		return new NumberFunction3(value.x(), value.y(), value.z());
	}
}
