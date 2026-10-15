package org.unitego.lobecorp.animation.resource;

import com.google.gson.JsonParser;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.resource.ListenerKey;
import org.unitego.lobecorp.Lobecorp;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/// 仓库归本次服务端资源集所有；活动播放保留已解析内容，不随重载切换版本。
public class LcAnimationResources extends SimplePreparableReloadListener<LcAnimationResources.Prepared> {
	public static final ListenerKey<LcAnimationResources> KEY = ListenerKey.create(Lobecorp.id("animation_resources"));
	public static final String MODEL_DIRECTORY = "animation/models";
	public static final String ANIMATION_DIRECTORY = "animation/animations";
	private static final FileToIdConverter MODELS = new FileToIdConverter(MODEL_DIRECTORY, ".geo.json");
	private static final FileToIdConverter ANIMATIONS = new FileToIdConverter(ANIMATION_DIRECTORY, ".animation.json");
	private Map<Identifier, String> models = Map.of();
	private Map<Identifier, String> animations = Map.of();

	public static LcAnimationResources get(MinecraftServer server) {
		return server.getServerResources().managers().getListener(KEY);
	}

	public LcAnimationContent resolve(Identifier modelId, Identifier animationId) {
		var model = models.get(modelId);
		var animation = animations.get(animationId);
		if (model == null || animation == null) {
			throw new NoSuchElementException(model == null ? modelId.toString() : animationId.toString());
		}

		return LcAnimationContent.of(modelId, animationId, model, animation);
	}

	@Override
	protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
		return new Prepared(read(manager, MODELS), read(manager, ANIMATIONS));
	}

	@Override
	protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
		models = prepared.models();
		animations = prepared.animations();
	}

	private static Map<Identifier, String> read(ResourceManager manager, FileToIdConverter converter) {
		Map<Identifier, String> values = new LinkedHashMap<>();
		for (var entry : converter.listMatchingResources(manager).entrySet()) {
			try (var reader = entry.getValue().openAsReader()) {
				var text = new StringWriter();
				reader.transferTo(text);
				var json = text.toString();
				if (!JsonParser.parseString(json).isJsonObject()) {
					throw new IllegalStateException(entry.getKey().toString());
				}

				values.put(converter.fileToId(entry.getKey()), json);
			} catch (IOException | RuntimeException exception) {
				throw new IllegalStateException(entry.getKey().toString(), exception);
			}
		}

		return Map.copyOf(values);
	}

	public record Prepared(Map<Identifier, String> models, Map<Identifier, String> animations) {
	}
}
