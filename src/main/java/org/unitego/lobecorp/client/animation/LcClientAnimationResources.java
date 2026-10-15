package org.unitego.lobecorp.client.animation;

import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.animation.BakedAnimations;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.loading.loader.GeckoLibGsonLoader;
import com.geckolib.loading.math.MathParser;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.animation.pose.LcAnimationClip;
import org.unitego.lobecorp.animation.pose.LcSkeleton;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/// 本地资源包仅覆盖视觉；服务器基础内容与 GeckoLib 本地缓存分别保存。
public class LcClientAnimationResources {
	private static final String MODEL_PREFIX = "geckolib/models/";
	private static final String ANIMATION_PREFIX = "geckolib/animations/";
	private static final Map<String, BakedGeoModel> MODELS = new HashMap<>();
	private static final Map<String, BakedAnimations> ANIMATIONS = new HashMap<>();
	private static final Map<Identifier, String> LOCAL_MODELS = new HashMap<>();
	private static final Map<Identifier, String> LOCAL_ANIMATIONS = new HashMap<>();
	private static final Map<String, LcAnimationClip> CLIPS = new HashMap<>();
	private static final Map<ContentClipKey, LcAnimationClip> CONTENT_CLIPS = new HashMap<>();
	private static final Map<String, LcSkeleton> SKELETONS = new HashMap<>();
	private static @Nullable Object generation;

	public static BakedGeoModel model(LcAnimationContent content) {
		refresh();
		if (localModel(content) != null) {
			return GeckoLibResources.getBakedModels().getModel(content.modelId());
		}

		return MODELS.computeIfAbsent(content.version(), _ -> new GeckoLibGsonLoader()
				.bakeGeckoLibModelFile(content.modelId(), JsonParser.parseString(content.modelJson()).getAsJsonObject()));
	}

	public static @Nullable Animation animation(LcAnimationContent content, String name) {
		refresh();
		if (localAnimation(content) != null) {
			return GeckoLibResources.getBakedAnimations().getAnimation(content.animationId(), new Identifier[0], name);
		}

		var animations = ANIMATIONS.computeIfAbsent(content.version(), _ -> new GeckoLibGsonLoader()
				.bakeGeckoLibAnimationsFile(content.animationId(), JsonParser.parseString(content.animationJson())
						.getAsJsonObject(), MathParser.createWithDeduplication()));
		return animations.getAnimation(name);
	}

	public static @Nullable LcAnimationClip visualClip(LcAnimationContent content, String name) {
		refresh();
		var json = localAnimation(content);
		if (json == null) {
			return null;
		}

		return CLIPS.computeIfAbsent(content.animationId() + "/" + name, _ -> LcAnimationClip.parse(json, name));
	}

	public static LcAnimationClip contentClip(LcAnimationContent content, String name) {
		return CONTENT_CLIPS.computeIfAbsent(new ContentClipKey(content.version(), name),
				_ -> LcAnimationClip.parse(content.animationJson(), name));
	}

	public static LcSkeleton skeleton(LcAnimationContent content) {
		refresh();
		var local = localModel(content);
		var key = local == null ? content.version() : content.modelId().toString();
		return SKELETONS.computeIfAbsent(key, _ -> LcSkeleton.parse(local == null ? content.modelJson() : local));
	}

	private static @Nullable String localModel(LcAnimationContent content) {
		var file = content.modelId().withPath(MODEL_PREFIX + content.modelId().getPath() + ".geo.json");
		return LOCAL_MODELS.computeIfAbsent(file, LcClientAnimationResources::read);
	}

	private static @Nullable String localAnimation(LcAnimationContent content) {
		var file = content.animationId().withPath(ANIMATION_PREFIX + content.animationId().getPath() + ".animation.json");
		return LOCAL_ANIMATIONS.computeIfAbsent(file, LcClientAnimationResources::read);
	}

	private static @Nullable String read(Identifier file) {
		var resource = Minecraft.getInstance().getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return null;
		}

		try (var reader = resource.get().openAsReader()) {
			var text = new StringWriter();
			reader.transferTo(text);
			return text.toString();
		} catch (IOException exception) {
			throw new IllegalStateException(file.toString(), exception);
		}
	}

	private static void refresh() {
		var current = GeckoLibResources.getBakedModels();
		if (generation != current) {
			generation = current;
			LOCAL_MODELS.clear();
			LOCAL_ANIMATIONS.clear();
			CLIPS.clear();
			SKELETONS.clear();
		}
	}

	public static void clear() {
		MODELS.clear();
		ANIMATIONS.clear();
		LOCAL_MODELS.clear();
		LOCAL_ANIMATIONS.clear();
		CLIPS.clear();
		CONTENT_CLIPS.clear();
		SKELETONS.clear();
		generation = null;
	}

	private record ContentClipKey(String contentVersion, String animationName) {
	}
}
