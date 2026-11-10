package org.unitego.lobecorp.registry.client.encounter;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.client.encounter.IEncounterDisplay;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EncounterDisplays {
	private static final Map<Identifier, IEncounterDisplay> displays = new LinkedHashMap<>();

	public static void register(Identifier type, IEncounterDisplay display) {
		if (displays.putIfAbsent(type, display) != null) {
			throw new IllegalArgumentException("重复的遭遇表现：" + type);
		}
	}

	public static @Nullable IEncounterDisplay get(Identifier type) {
		return displays.get(type);
	}

	public static Collection<IEncounterDisplay> values() {
		return List.copyOf(displays.values());
	}
}
