package org.unitego.lobecorp.registry.animation;

import net.minecraft.resources.Identifier;
import org.unitego.lobecorp.animation.definition.LcAnimationDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/// 仅保存代码定义的延迟入口；世界、资源版本和播放状态不存放在此处。
public class LcAnimationDefinitions {
	private static final String DUPLICATE_DEFINITION = "Duplicate code animation definition: ";
	private static final String UNKNOWN_DEFINITION = "Unknown code animation definition: ";
	private static final Map<Identifier, Supplier<LcAnimationDefinition>> DEFINITIONS = new LinkedHashMap<>();

	public static void register(Identifier id, Supplier<LcAnimationDefinition> definition) {
		if (DEFINITIONS.putIfAbsent(id, definition) != null) {
			throw new IllegalArgumentException(DUPLICATE_DEFINITION + id);
		}
	}

	public static LcAnimationDefinition resolve(Identifier id) {
		Supplier<LcAnimationDefinition> definition = DEFINITIONS.get(id);

		if (definition == null) {
			throw new IllegalArgumentException(UNKNOWN_DEFINITION + id);
		}

		return definition.get();
	}
}
