package org.unitego.lobecorp.registry.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.clock.WorldClock;
import org.unitego.lobecorp.Lobecorp;

public interface LcWorldClocks {
	ResourceKey<WorldClock> MIDNIGHT_CLEANUP_CLOCK = register(LcLevels.MIDNIGHT_CLEANUP.identifier());

	static ResourceKey<WorldClock> register(String path) {
		return register(Lobecorp.id(path));
	}

	static ResourceKey<WorldClock> register(Identifier id) {
		return Lobecorp.createKey(Registries.WORLD_CLOCK, id);
	}
}
