package org.unitego.lobecorp.registry.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.registry.translation.TranslationRegistry;

public interface LcLevels {
	ResourceKey<Level> MIDNIGHT_CLEANUP = register("city/backstreets/midnight_cleanup", "The City - Backstreets - Midnight Cleanup", "都市-后巷-深夜清扫");

	static ResourceKey<Level> register(String path, String enUs, String zhCn) {
		var key = Lobecorp.<Level>createKey(Registries.DIMENSION, path);
		TranslationRegistry.register(key.identifier().toLanguageKey("dimension").replace('/', '.'), enUs, zhCn);
		return key;
	}
}
