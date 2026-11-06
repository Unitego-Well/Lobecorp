package org.unitego.lobecorp.client.animation;

import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.BakedAnimationCache;
import com.geckolib.cache.BakedModelCache;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.jspecify.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/// 在 GeckoLib 发布新烘焙缓存后建立本地动画只读索引。
///
/// 只处理 CPU 目录和 LC 自有资源语义；不创建 GPU 资源，不预播放实例动画。
public class LcClientAnimationResourceReloadListener implements PreparableReloadListener {
	@Override
	public CompletableFuture<Void> reload(SharedState currentReload, Executor taskExecutor,
	                                      PreparationBarrier preparationBarrier, Executor reloadExecutor) {
		var manager = currentReload.resourceManager();
		var revision = LcClientAnimationResources.beginGeneration();

		// GeckoLib 在前序 listener 的 apply 阶段才替换全局缓存，准备必须等该应用完成。
		return preparationBarrier.wait(CompletableFuture.<Void>completedFuture(null))
				.thenCompose(_ -> CompletableFuture.supplyAsync(() -> {
					var models = GeckoLibResources.getBakedModels();
					var animations = GeckoLibResources.getBakedAnimations();
					try {
						return new Prepared(revision, models, animations,
								LcClientAnimationResourceIndex.prepare(manager, revision, models, animations));
					} catch (RuntimeException exception) {
						Lobecorp.LOGGER.error("客户端动画资源准备失败，保留上一代只读索引", exception);
						return new Prepared(revision, models, animations, null);
					}
				}, taskExecutor))
				.thenAcceptAsync(this::publish, reloadExecutor);
	}

	private void publish(Prepared prepared) {
		if (prepared.revision() != LcClientAnimationResources.latestRequestedRevision()) {
			return;
		}

		if (prepared.index() == null) {
			return;
		}

		LcClientAnimationResources.install(prepared.index());
		if (prepared.index().failureCount() > 0) {
			Lobecorp.LOGGER.warn("客户端动画资源准备完成，{} 项失败：{}",
					prepared.index().failureCount(), prepared.index().failures());
		}
	}

	private record Prepared(long revision, BakedModelCache models, BakedAnimationCache animations,
	                        @Nullable LcClientAnimationResourceIndex index) {
	}
}
