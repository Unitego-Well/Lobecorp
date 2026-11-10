package org.unitego.lobecorp.registry.encounter;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.registry.LcRegistries;
import org.unitego.lobecorp.world.encounter.IEncounterDefinition;

import java.util.Collection;

public class EncounterDefinitions {
	public static @Nullable IEncounterDefinition get(Identifier id) {
		var definition = LcRegistries.ENCOUNTER.getValue(id);
		return definition == null ? null : definition.get();
	}

	public static Collection<IEncounterDefinition> values() {
		return LcRegistries.ENCOUNTER.stream().map(definition -> (IEncounterDefinition) definition.get()).toList();
	}
}
