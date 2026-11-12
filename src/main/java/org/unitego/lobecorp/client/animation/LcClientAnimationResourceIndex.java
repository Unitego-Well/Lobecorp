package org.unitego.lobecorp.client.animation;

import com.geckolib.cache.BakedAnimationCache;
import com.geckolib.cache.BakedModelCache;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.animation.BakedAnimations;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.loading.definition.animation.ActorAnimations;
import com.geckolib.loading.loader.GeckoLibGsonLoader;
import com.geckolib.loading.math.MathParser;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.pose.LcAnimationClip;
import org.unitego.lobecorp.animation.pose.LcSkeleton;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/// 一个本地资源代次内共享的只读模型、动画、骨架与 LC 关键帧准备结果。
///
/// 该对象只保存不可变目录和 GeckoLib 已烘焙缓存；控制器、姿态、时钟与事件状态仍由实例独占。
public record LcClientAnimationResourceIndex(
		long revision,
		BakedModelCache modelGeneration,
		BakedAnimationCache animationGeneration,
		Map<Identifier, BakedGeoModel> localModels,
		Map<Identifier, BakedAnimations> localAnimations,
		Map<LocalClipKey, LcAnimationClip> localClips,
		Map<LocalSkeletonKey, LcSkeleton> localSkeletons,
		Map<String, String> failures) {
	private static final String MODEL_DIRECTORY = "geckolib/models";
	private static final String ANIMATION_DIRECTORY = "geckolib/animations";
	private static final String MODEL_SUFFIX = ".geo.json";
	private static final String ANIMATION_SUFFIX = ".animation.json";

	public LcClientAnimationResourceIndex {
		Objects.requireNonNull(modelGeneration);
		Objects.requireNonNull(animationGeneration);
		localModels = immutableMap(localModels);
		localAnimations = immutableMap(localAnimations);
		localClips = immutableMap(localClips);
		localSkeletons = immutableMap(localSkeletons);
		failures = immutableMap(failures);
	}

	/// 从当前资源管理器和 GeckoLib 已发布缓存建立只读准备结果。
	///
	/// 单个资源失败会记录后继续处理其他资源；调用方应在资源 reload 的应用阶段调用。
	@SuppressWarnings("UnstableApiUsage")
	public static LcClientAnimationResourceIndex prepare(ResourceManager manager, long revision,
	                                                     BakedModelCache models, BakedAnimationCache animations) {
		Map<Identifier, BakedGeoModel> bakedModels = new LinkedHashMap<>();
		Map<Identifier, BakedAnimations> bakedAnimations = new LinkedHashMap<>();
		Map<LocalClipKey, LcAnimationClip> clips = new LinkedHashMap<>();
		Map<LocalSkeletonKey, LcSkeleton> skeletons = new LinkedHashMap<>();
		Map<String, String> failures = new LinkedHashMap<>();

		for (var entry : manager.listResources(MODEL_DIRECTORY, LcClientAnimationResourceIndex::isModel).entrySet()) {
			var file = entry.getKey();
			var modelId = GeckoLibResources.stripPrefixAndSuffix(file);

			try {
				var json = read(entry.getValue());
				var baked = models.cache().get(modelId);

				if (baked == null) {
					failures.put("model:" + file, "GeckoLib 未烘焙本地模型");
				} else {
					baked.boneLookup().get();
					bakedModels.put(modelId, baked);
				}

				skeletons.put(new LocalSkeletonKey(revision, modelId), LcSkeleton.parse(json));
			} catch (IOException | RuntimeException exception) {
				failures.put("model:" + file, failure(exception));
			}
		}

		for (var entry : manager.listResources(ANIMATION_DIRECTORY, LcClientAnimationResourceIndex::isAnimation).entrySet()) {
			var file = entry.getKey();
			var animationId = GeckoLibResources.stripPrefixAndSuffix(file);

			try {
				var json = read(entry.getValue());
				var definitions = ActorAnimations.GSON.fromJson(json, ActorAnimations.class);
				var baked = animations.cache().get(animationId);

				if (baked == null) {
					failures.put("animation:" + file, "GeckoLib 未烘焙本地动画");
				} else {
					bakedAnimations.put(animationId, baked);
				}

				var batch = LcAnimationClip.compileAll(definitions);
				batch.clips().forEach((name, clip) ->
						clips.put(new LocalClipKey(revision, animationId, name), clip));
				batch.failures().forEach((name, failure) ->
						failures.put("clip:" + animationId + "/" + name, failure));
				if (definitions.animations().isEmpty()) {
					failures.put("animation:" + file, "本地动画文件没有动画定义");
				}
			} catch (IOException | RuntimeException exception) {
				failures.put("animation:" + file, failure(exception));
			}
		}

		return new LcClientAnimationResourceIndex(revision, models, animations, bakedModels, bakedAnimations,
				clips, skeletons, failures);
	}

	/// 服务器内容准备按完整 version 隔离；单类失败只记录，不阻断内容发布。
	public static ContentPreparation prepareContent(LcAnimationContent content) {
		return prepareContent(content, LcClientAnimationResourceIndex::bakeAnimations);
	}

	@SuppressWarnings("UnstableApiUsage")
	static ContentPreparation prepareContent(LcAnimationContent content,
	                                         java.util.function.Function<ActorAnimations, BakedAnimations> baker) {
		Objects.requireNonNull(content);
		Objects.requireNonNull(baker);
		Map<String, String> failures = new LinkedHashMap<>();
		BakedGeoModel model = null;
		LcSkeleton skeleton = null;
		BakedAnimations animations = null;
		Map<ContentClipKey, LcAnimationClip> clips = new LinkedHashMap<>();

		try {
			model = new GeckoLibGsonLoader().bakeGeckoLibModelFile(content.modelId(),
					JsonParser.parseString(content.modelJson()).getAsJsonObject());
			model.boneLookup().get();
		} catch (RuntimeException exception) {
			failures.put("model:" + content.modelId(), failure(exception));
		}

		try {
			skeleton = LcSkeleton.parse(content.modelJson());
		} catch (RuntimeException exception) {
			failures.put("skeleton:" + content.modelId(), failure(exception));
		}

		try {
			var definitions = ActorAnimations.GSON.fromJson(content.animationJson(), ActorAnimations.class);
			try {
				animations = baker.apply(definitions);
			} catch (RuntimeException exception) {
				failures.put("animation:" + content.animationId(), failure(exception));
			}

			var batch = LcAnimationClip.compileAll(definitions);
			batch.clips().forEach((name, clip) ->
					clips.put(new ContentClipKey(content.version(), name), clip));
			batch.failures().forEach((name, failure) ->
					failures.put("clip:" + content.animationId() + "/" + name, failure));
		} catch (RuntimeException exception) {
			failures.put("animation:" + content.animationId(), failure(exception));
		}

		return new ContentPreparation(content.version(), model, skeleton, animations, clips, failures);
	}

	@SuppressWarnings("UnstableApiUsage")
	private static BakedAnimations bakeAnimations(ActorAnimations definitions) {
		return definitions.bake(Identifier.fromNamespaceAndPath(Lobecorp.NAMESPACE, "animation"),
				MathParser.createWithDeduplication());
	}

	private static boolean isModel(Identifier file) {
		return file.getNamespace().equals(Lobecorp.NAMESPACE) && file.getPath().endsWith(MODEL_SUFFIX);
	}

	private static boolean isAnimation(Identifier file) {
		return file.getNamespace().equals(Lobecorp.NAMESPACE) && file.getPath().endsWith(ANIMATION_SUFFIX);
	}

	private static String read(Resource resource) throws IOException {
		try (var reader = resource.openAsReader()) {
			var text = new StringWriter();
			reader.transferTo(text);
			return text.toString();
		}
	}

	private static String failure(Exception exception) {
		return exception.getClass().getSimpleName() + ": " + exception.getMessage();
	}

	private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	public int failureCount() {
		return failures.size();
	}

	/// 本地动画 clip 的键包含资源代次，避免同 ID 同名内容在 reload 后复用旧结果。
	public record LocalClipKey(long revision, Identifier animationId, String name) {
		public LocalClipKey {
			Objects.requireNonNull(animationId);
			Objects.requireNonNull(name);
		}
	}

	/// 本地骨架的键包含资源代次，避免同模型 ID 在 reload 后复用旧骨架。
	public record LocalSkeletonKey(long revision, Identifier modelId) {
		public LocalSkeletonKey {
			Objects.requireNonNull(modelId);
		}
	}

	/// 服务器骨架的键使用完整内容 version，避免同模型 ID 的不同连接或内容版本互相复用。
	public record ContentSkeletonKey(String contentVersion) {
		public ContentSkeletonKey {
			Objects.requireNonNull(contentVersion);
		}
	}

	/// 服务器内容的 clip 键使用完整内容 version，不按模型或动画 ID 复用。
	public record ContentClipKey(String contentVersion, String animationName) {
		public ContentClipKey {
			Objects.requireNonNull(contentVersion);
			Objects.requireNonNull(animationName);
		}
	}

	/// 服务器内容准备结果；空字段表示对应准备失败，调用方必须保留原有 lazy 行为。
	public record ContentPreparation(String contentVersion, @Nullable BakedGeoModel model,
	                                 @Nullable LcSkeleton skeleton, @Nullable BakedAnimations animations,
	                                 Map<ContentClipKey, LcAnimationClip> clips, Map<String, String> failures) {
		public ContentPreparation {
			Objects.requireNonNull(contentVersion);
			clips = immutableMap(clips);
			failures = immutableMap(failures);
		}

		public static ContentPreparation empty(String contentVersion) {
			return new ContentPreparation(contentVersion, null, null, null, Map.of(), Map.of());
		}

	}


	/// 一次本地动画文件批量解析后的完成状态；失败名与缺失名均由不可变映射显式表达。
	public record LocalClipBatch(Map<String, LcAnimationClip> clips, Map<String, String> failures,
	                             LocalClipKey requested) {
		public LocalClipBatch {
			clips = immutableMap(clips);
			failures = immutableMap(failures);
			Objects.requireNonNull(requested);
		}

		public @Nullable LcAnimationClip clip(String name) {
			return clips.get(name);
		}

		public boolean failed(String name) {
			return failures.containsKey(name) || failures.containsKey("*");
		}
	}
}
