package org.unitego.lobecorp.generator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import org.unitego.lobecorp.registry.world.LcLevels;
import org.unitego.lobecorp.registry.world.LcWorldClocks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/// 从当前版本原版定义派生，避免复制旧版维度字段及日夜曲线。
public class CityDimensionGenerator implements DataProvider {
	private final Path output;

	public CityDimensionGenerator(PackOutput output) {
		this.output = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(LcWorldClocks.MIDNIGHT_CLEANUP_CLOCK.identifier().getNamespace());
	}

	private static <T> JsonObject vanilla(HolderLookup.Provider lookup, ResourceKey<? extends Registry<T>> registry,
	                                    String name, Codec<T> codec) {
		var value = lookup.lookupOrThrow(registry).getOrThrow(ResourceKey.create(registry, Identifier.withDefaultNamespace(name))).value();
		return codec.encodeStart(lookup.createSerializationContext(JsonOps.INSTANCE), value).getOrThrow().getAsJsonObject();
	}

	@Override
	public CompletableFuture<?> run(CachedOutput cache) {
		return generate(cache, VanillaRegistries.createLookup());
	}

	private CompletableFuture<?> generate(CachedOutput cache, HolderLookup.Provider provider) {
		var id = LcLevels.MIDNIGHT_CLEANUP.identifier().toString();
		var path = LcLevels.MIDNIGHT_CLEANUP.identifier().getPath();
		var writes = new ArrayList<CompletableFuture<?>>();
		writes.add(save(cache, "world_clock/" + path, new JsonObject()));

		var biome = vanilla(provider, Registries.BIOME, "plains", Biome.DIRECT_CODEC);
		biome.add("spawners", new JsonObject());
		biome.add("spawn_costs", new JsonObject());
		biome.add("carvers", new JsonArray());
		biome.add("features", new JsonArray());
		writes.add(save(cache, "worldgen/biome/" + path, biome));

		var type = vanilla(provider, Registries.DIMENSION_TYPE, "overworld", DimensionType.DIRECT_CODEC);
		type.addProperty("default_clock", id);
		var timelines = new JsonArray();
		for (var name : new String[]{"day", "moon", "villager_schedule"}) {
			var timeline = vanilla(provider, Registries.TIMELINE, name, Timeline.DIRECT_CODEC);
			timeline.addProperty("clock", id);
			timelines.add(id + "/" + name);
			writes.add(save(cache, "timeline/" + path + "/" + name, timeline));
		}
		type.add("timelines", timelines);
		writes.add(save(cache, "dimension_type/" + path, type));

		var flat = FlatLevelGeneratorSettings.getDefault(provider.lookupOrThrow(Registries.BIOME),
				provider.lookupOrThrow(Registries.STRUCTURE_SET), provider.lookupOrThrow(Registries.PLACED_FEATURE));
		var settings = FlatLevelGeneratorSettings.CODEC.encodeStart(provider.createSerializationContext(JsonOps.INSTANCE), flat)
				.getOrThrow().getAsJsonObject();
		settings.addProperty("biome", id);
		settings.add("structure_overrides", new JsonArray());
		settings.addProperty("features", false);
		settings.addProperty("lakes", false);
		var generator = new JsonObject();
		generator.addProperty("type", "minecraft:flat");
		generator.add("settings", settings);
		var dimension = new JsonObject();
		dimension.addProperty("type", id);
		dimension.add("generator", generator);
		writes.add(save(cache, "dimension/" + path, dimension));
		return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
	}

	private CompletableFuture<?> save(CachedOutput cache, String path, JsonObject data) {
		return DataProvider.saveStable(cache, data, output.resolve(path + ".json"));
	}

	@Override
	public String getName() {
		return "City dimension, biome and independent clock";
	}
}
