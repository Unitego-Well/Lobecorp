package org.unitego.lobecorp.animation;

import com.geckolib.animatable.GeoAnimatable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class LayerCollector implements LcAnimationLayerRegistrar {
	private final Map<String, LcLayerDefinition> definitions = new LinkedHashMap<>();

	@Override
	public void add(LcLayerDefinition definition) {
		Objects.requireNonNull(definition);
		if (definitions.putIfAbsent(definition.name(), definition) != null) {
			throw new IllegalArgumentException("Duplicate animation layer: " + definition.name());
		}
	}

	public  <A extends GeoAnimatable> Map<String, LayerRuntime<A>> build() {
		Map<String, LayerRuntime<A>> result = new LinkedHashMap<>(definitions.size());
		for (LcLayerDefinition definition : definitions.values()) {
			result.put(definition.name(), new LayerRuntime<>(definition));
		}
		return result;
	}
}
