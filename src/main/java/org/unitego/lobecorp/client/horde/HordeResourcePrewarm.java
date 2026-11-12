package org.unitego.lobecorp.client.horde;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.world.horde.HordeVisualDefinition;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/// 只推进公共模型与基础图集；真实世界的 Iris 布局由世界渲染阶段准备。
public class HordeResourcePrewarm implements PreparableReloadListener {
	/// 总等待上限为30秒；单次驱动上传不可抢占，步骤预算由 Renderer 自身控制。
	private static final long TIMEOUT_NS = TimeUnit.SECONDS.toNanos(30);
	/// 防止极快失败重试在加载期间持续占用执行器。
	private static final int MAX_STEPS = 4096;
	private final Supplier<List<HordeVisualDefinition>> visuals;

	public HordeResourcePrewarm(Supplier<List<HordeVisualDefinition>> visuals) {
		this.visuals = visuals;
	}

	@Override
	public CompletableFuture<Void> reload(SharedState currentReload, Executor taskExecutor,
	                                      PreparationBarrier preparationBarrier, Executor reloadExecutor) {
		return preparationBarrier.wait((Void) null).thenComposeAsync(_ -> {
			var completion = new CompletableFuture<Void>();
			try {
				HordeClient.ensureResourceGeneration();
				var definitions = List.copyOf(visuals.get());
				if (definitions.isEmpty()) {
					Lobecorp.LOGGER.warn("Horde 菜单预热未取得深夜清扫视觉定义，继续加载菜单");
					completion.complete(null);
					return completion;
				}

				definitions.forEach(HordeGpuRenderer::requestResources);
				advance(definitions, reloadExecutor, completion, System.nanoTime(), 0);
			} catch (Exception exception) {
				fail(completion, exception);
			}
			return completion;
		}, reloadExecutor);
	}

	private static void advance(List<HordeVisualDefinition> definitions, Executor executor,
	                            CompletableFuture<Void> completion, long started, int step) {
		try {
			HordeGpuRenderer.prepareResources();
			if (definitions.stream().allMatch(HordeGpuRenderer::resourcesReady)) {
				Lobecorp.LOGGER.info("Horde 菜单公共资源预热完成：{} 项，{} ms", definitions.size(),
						TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
				completion.complete(null);
				return;
			}

			if (step + 1 >= MAX_STEPS || System.nanoTime() - started >= TIMEOUT_NS) {
				Lobecorp.LOGGER.warn("Horde 菜单预热达到等待上限，公共几何尚未就绪，继续加载菜单");
				completion.complete(null);
				return;
			}

			// 延迟投递避免执行器内联时递归或连续占用渲染线程；不阻塞等待 CPU 采样。
			CompletableFuture.runAsync(() -> advance(definitions, executor, completion, started, step + 1),
					CompletableFuture.delayedExecutor(1, TimeUnit.MILLISECONDS, executor))
					.exceptionally(exception -> {
						fail(completion, exception);
						return null;
					});
		} catch (Exception exception) {
			fail(completion, exception);
		}
	}

	private static void fail(CompletableFuture<Void> completion, Throwable exception) {
		Lobecorp.LOGGER.error("Horde 菜单公共资源预热失败，继续加载菜单", exception);
		completion.complete(null);
	}
}
