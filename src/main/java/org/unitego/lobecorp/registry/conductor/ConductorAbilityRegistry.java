package org.unitego.lobecorp.registry.conductor;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.unitego.lobecorp.conductor.ability.IConductorAbility;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/// 指挥家能力的登记定义及注册校验入口。
public class ConductorAbilityRegistry {
	/// 按实体类型归集的能力定义，保留登记顺序以供能力实例创建。
	public static final Map<EntityType<?>, Map<Identifier, Definition>> DEFINITIONS = new HashMap<>();

	public static void register(EntityType<?> type, Identifier id, Supplier<IConductorAbility> factory, boolean initial) {
		Map<Identifier, Definition> abilities = DEFINITIONS.computeIfAbsent(type, _ -> new LinkedHashMap<>());

		if (abilities.containsKey(id)) {
			throw new IllegalArgumentException("Duplicate conductor ability: " + type + " / " + id);
		}

		IConductorAbility ability = factory.get();

		if (ability == null || !id.equals(ability.id())) {
			throw new IllegalArgumentException("Invalid conductor ability: " + type + " / " + id);
		}

		abilities.put(id, new Definition(ability, initial));
	}

	public record Definition(IConductorAbility ability, boolean initial) {
	}
}
