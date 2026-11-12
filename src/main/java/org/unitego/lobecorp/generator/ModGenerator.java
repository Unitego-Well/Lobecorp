package org.unitego.lobecorp.generator;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import org.unitego.lobecorp.generator.lang.EnUsLangGenerator;
import org.unitego.lobecorp.generator.lang.ZhCnLangGenerator;
import org.unitego.lobecorp.generator.tag.DamageTypeTagGenerator;
import org.unitego.lobecorp.generator.tag.EntityTypeTagGenerator;
import org.unitego.lobecorp.generator.tag.EntitySkillTagGenerator;
import org.unitego.lobecorp.registry.translation.TranslationRegistry;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("UnusedReturnValue")
public class ModGenerator {
	public static void gatherData(GatherDataEvent.Client event) {
		CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
		DataGenerator generator = event.getGenerator();
		PackOutput packOutput = generator.getPackOutput();
		client(event, lookupProvider, generator, packOutput);
		server(event, lookupProvider, generator, packOutput);
	}

	private static void server(GatherDataEvent.Client event, CompletableFuture<HolderLookup.Provider> lookupProvider, DataGenerator generator, PackOutput packOutput) {
		build(event, new CityDimensionGenerator(packOutput));
		var damageTypes = new DamageTypeGenerator(packOutput, lookupProvider);
		generator.addProvider(true, damageTypes);
		var registries = damageTypes.getRegistryProvider();
		build(event, new EntityTypeTagGenerator(packOutput, registries));
		build(event, new DamageTypeTagGenerator(packOutput, registries));
		build(event, new EntitySkillTagGenerator(packOutput, registries));
	}

	private static void client(GatherDataEvent.Client event, CompletableFuture<HolderLookup.Provider> lookupProvider, DataGenerator generator, PackOutput packOutput) {
		TranslationRegistry.init();
		build(event, EnUsLangGenerator::new);
		build(event, ZhCnLangGenerator::new);
		build(event, ParticleGenerator::new);
		build(event, SoundGenerator::new);
		build(event, ItemModelProvider::new);
	}

	public static <T extends DataProvider> T build(GatherDataEvent event, DataProvider provider) {
		return event.getGenerator().addProvider(true, (T) provider);
	}

	public static <T extends DataProvider> T build(GatherDataEvent event, DataProvider.Factory<T> provider) {
		return event.getGenerator().addProvider(true, provider);
	}
}
