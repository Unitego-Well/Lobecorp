package org.unitego.lobecorp.client.animation;

import com.geckolib.cache.BakedAnimationCache;
import com.geckolib.cache.BakedModelCache;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.model.BakedGeoModel;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.animation.pose.LcAnimationClip;
import org.unitego.lobecorp.animation.pose.LcSkeleton;
import org.unitego.lobecorp.animation.resource.LcAnimationContent;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// 本地资源包仅覆盖视觉；服务器基础内容与 GeckoLib 本地缓存分别保存。
public class LcClientAnimationResources {
	private static final String MODEL_PREFIX = "geckolib/models/";
	private static final String ANIMATION_PREFIX = "geckolib/animations/";
	private static final Map<Identifier, Optional<String>> LOCAL_MODELS = new HashMap<>();
	private static final Map<Identifier, Optional<String>> LOCAL_ANIMATIONS = new HashMap<>();
	private static final Map<LcClientAnimationResourceIndex.LocalClipKey,
			LcClientAnimationResourceIndex.LocalClipBatch> LOCAL_CLIP_BATCHES = new HashMap<>();
	private static final Map<LcClientAnimationResourceIndex.LocalSkeletonKey, LcSkeleton> SKELETONS = new HashMap<>();
	private static final Map<String, LcClientAnimationResourceIndex.ContentPreparation> CONTENT_PREPARATIONS =
			new HashMap<>();
	private static volatile @Nullable LcClientAnimationResourceIndex index;
	private static @Nullable BakedModelCache observedModels;
	private static @Nullable BakedAnimationCache observedAnimations;
	private static long latestRequestedRevision;
	private static long lastPublishedRevision;

	public static BakedGeoModel model(LcAnimationContent content) {
		refresh();
		var current = index;
		if (current != null && current.localModels().containsKey(content.modelId())) {
			return current.localModels().get(content.modelId());
		}

		if (localModel(content) != null) {
			return GeckoLibResources.getBakedModels().getModel(content.modelId());
		}

		return Objects.requireNonNull(prepareContent(content).model());
	}

	public static @Nullable Animation animation(LcAnimationContent content, String name) {
		refresh();
		var current = index;
		if (current != null) {
			var baked = current.localAnimations().get(content.animationId());
			if (baked != null) {
				return baked.getAnimation(name);
			}
		}

		if (localAnimation(content) != null) {
			return GeckoLibResources.getBakedAnimations().getAnimation(content.animationId(), new Identifier[0], name);
		}

		var prepared = prepareContent(content).animations();
		return prepared == null ? null : prepared.getAnimation(name);
	}

	public static @Nullable LcAnimationClip visualClip(LcAnimationContent content, String name) {
		refresh();
		var current = index;
		var revision = current == null ? 0 : current.revision();
		var key = new LcClientAnimationResourceIndex.LocalClipKey(revision, content.animationId(), name);
		var prepared = current == null ? null : current.localClips().get(key);
		if (prepared != null) {
			return prepared;
		}

		return localClipBatch(revision, content).clip(name);
	}

	public static LcAnimationClip contentClip(LcAnimationContent content, String name) {
		refresh();
		var key = new LcClientAnimationResourceIndex.ContentClipKey(content.version(), name);
		var preparation = prepareContent(content);
		var preparedClip = preparation.clips().get(key);
		if (preparedClip != null) {
			return preparedClip;
		}

		var failure = preparation.failures().get(clipFailureKey(content, name));
		if (failure != null) {
			throw new IllegalStateException(failure);
		}

		throw new IllegalArgumentException("服务器动画中不存在 clip：" + content.animationId() + "/" + name);
	}

	/// 服务器内容准备按完整 version 隔离；同版本目录与 clip 只建立一次。
	///
	/// 准备失败也缓存空结果，避免每个 getter 重复解析同一份失败内容。
	public static LcClientAnimationResourceIndex.ContentPreparation prepareContent(LcAnimationContent content) {
		Objects.requireNonNull(content);
		return CONTENT_PREPARATIONS.computeIfAbsent(content.version(), _ -> {
			try {
				return LcClientAnimationResourceIndex.prepareContent(content);
			} catch (RuntimeException exception) {
				Lobecorp.LOGGER.error("服务器动画内容准备入口失败，保留 lazy 语义：{}", content.version(), exception);
				return LcClientAnimationResourceIndex.ContentPreparation.empty(content.version());
			}
		});
	}

	public static LcSkeleton skeleton(LcAnimationContent content) {
		refresh();
		var current = index;
		if (current != null) {
			var prepared = current.localSkeletons().get(new LcClientAnimationResourceIndex.LocalSkeletonKey(
					current.revision(), content.modelId()));
			if (prepared != null) {
				return prepared;
			}
		}

		var local = localModel(content);
		if (local == null) {
			return Objects.requireNonNull(prepareContent(content).skeleton());
		}

		var revision = current == null ? 0 : current.revision();
		return SKELETONS.computeIfAbsent(new LcClientAnimationResourceIndex.LocalSkeletonKey(revision, content.modelId()),
				_ -> LcSkeleton.parse(local));
	}

	private static @Nullable String localModel(LcAnimationContent content) {
		var file = localModelFile(content);
		return LOCAL_MODELS.computeIfAbsent(file, LcClientAnimationResources::read).orElse(null);
	}

	private static @Nullable String localAnimation(LcAnimationContent content) {
		var file = localAnimationFile(content);
		return LOCAL_ANIMATIONS.computeIfAbsent(file, LcClientAnimationResources::read).orElse(null);
	}

	private static Identifier localModelFile(LcAnimationContent content) {
		return content.modelId().withPath(MODEL_PREFIX + content.modelId().getPath() + ".geo.json");
	}

	private static Identifier localAnimationFile(LcAnimationContent content) {
		return content.animationId().withPath(ANIMATION_PREFIX + content.animationId().getPath() + ".animation.json");
	}

	private static LcClientAnimationResourceIndex.LocalClipBatch localClipBatch(long revision,
	                                                                             LcAnimationContent content) {
		var key = new LcClientAnimationResourceIndex.LocalClipKey(revision, content.animationId(), "");
		var existing = LOCAL_CLIP_BATCHES.get(key);
		if (existing != null) {
			return existing;
		}

		var json = localAnimation(content);
		if (json == null) {
			var batch = new LcClientAnimationResourceIndex.LocalClipBatch(Map.of(), Map.of("*", "本地动画文件不存在"), key);
			LOCAL_CLIP_BATCHES.put(key, batch);
			return batch;
		}

		try {
			var parsed = LcAnimationClip.parseAll(json);
			var batch = new LcClientAnimationResourceIndex.LocalClipBatch(parsed.clips(), parsed.failures(), key);
			LOCAL_CLIP_BATCHES.put(key, batch);
			if (!parsed.failures().isEmpty()) {
				Lobecorp.LOGGER.warn("本地动画 clip 解析失败，回退快照姿态：{}", parsed.failures());
			}

			return batch;
		} catch (RuntimeException exception) {
			var batch = new LcClientAnimationResourceIndex.LocalClipBatch(Map.of(),
					Map.of("*", exception.getClass().getSimpleName() + ": " + exception.getMessage()), key);
			LOCAL_CLIP_BATCHES.put(key, batch);
			Lobecorp.LOGGER.warn("本地动画文件解析失败，回退快照姿态：{}", content.animationId(), exception);
			return batch;
		}
	}

	private static String clipFailureKey(LcAnimationContent content, String name) {
		return "clip:" + content.animationId() + "/" + name;
	}

	private static Optional<String> read(Identifier file) {
		var resource = Minecraft.getInstance().getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return Optional.empty();
		}

		try (var reader = resource.get().openAsReader()) {
			var text = new StringWriter();
			reader.transferTo(text);
			return Optional.of(text.toString());
		} catch (IOException exception) {
			throw new IllegalStateException(file.toString(), exception);
		}
	}

	private static void refresh() {
		var models = GeckoLibResources.getBakedModels();
		var animations = GeckoLibResources.getBakedAnimations();
		if (observedModels != models || observedAnimations != animations) {
			observedModels = models;
			observedAnimations = animations;
			index = null;
			LOCAL_MODELS.clear();
			LOCAL_ANIMATIONS.clear();
			LOCAL_CLIP_BATCHES.clear();
			SKELETONS.clear();
		}
	}

	/// 发布一个已经完整建立的本地资源代次；旧代次迟到不会覆盖新代次。
	public static void install(LcClientAnimationResourceIndex prepared) {
		Objects.requireNonNull(prepared);
		if (prepared.revision() <= lastPublishedRevision
				|| prepared.revision() != latestRequestedRevision
				|| prepared.modelGeneration() != GeckoLibResources.getBakedModels()
				|| prepared.animationGeneration() != GeckoLibResources.getBakedAnimations()) {
			return;
		}

		lastPublishedRevision = prepared.revision();
		index = prepared;
		observedModels = prepared.modelGeneration();
		observedAnimations = prepared.animationGeneration();
		LOCAL_MODELS.clear();
		LOCAL_ANIMATIONS.clear();
		LOCAL_CLIP_BATCHES.clear();
		SKELETONS.clear();
	}

	/// 当前已发布的本地资源代次，供只读诊断和测试观察。
	public static @Nullable LcClientAnimationResourceIndex currentIndex() {
		return index;
	}

	/// 连接内容只清服务器内容缓存，不丢弃本地资源代次。
	public static void clearConnectionContent() {
		CONTENT_PREPARATIONS.clear();
	}

	public static void clear() {
		latestRequestedRevision++;
		LOCAL_MODELS.clear();
		LOCAL_ANIMATIONS.clear();
		LOCAL_CLIP_BATCHES.clear();
		SKELETONS.clear();
		CONTENT_PREPARATIONS.clear();
		index = null;
		observedModels = null;
		observedAnimations = null;
	}

	/// 登记一次新的本地资源请求；更早请求的结果随后不能发布。
	public static long beginGeneration() {
		latestRequestedRevision++;
		return latestRequestedRevision;
	}

	/// 当前最新请求代次，供准备服务核对晚到结果。
	public static long latestRequestedRevision() {
		return latestRequestedRevision;
	}
}
