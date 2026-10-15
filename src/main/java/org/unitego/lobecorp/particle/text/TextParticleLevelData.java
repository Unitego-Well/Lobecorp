package org.unitego.lobecorp.particle.text;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// 当前 Level 中不写入世界存档的服务端文本展示实例。
public class TextParticleLevelData {
	private final Map<UUID, TextParticleInstance> instances = new LinkedHashMap<>();

	public boolean add(TextParticleInstance instance) {
		return instances.putIfAbsent(instance.id(), instance) == null;
	}

	@Nullable
	public TextParticleInstance get(UUID id) {
		return instances.get(id);
	}

	public boolean remove(TextParticleInstance instance) {
		return instances.remove(instance.id(), instance);
	}

	public List<TextParticleSnapshot> snapshots() {
		return instances.values().stream().map(TextParticleInstance::snapshot).toList();
	}

	public List<TextParticleInstance> instances() {
		return List.copyOf(instances.values());
	}
}
