package org.unitego.lobecorp.mixin.bench;

import com.zhongbai233.bench.api.neoforge.client.BenchCaptureOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.config.bench.LobecorpBenchConfig;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/// 仅供精确性能 profile 装载；阻止 Bench 主动和失败取证入队，不干预游戏世界图标。
@Mixin(targets = "com.zhongbai233.bench.runtime.client.ClientAutomationController")
public class ClientAutomationNoScreenshotMixin {
	@Unique
	private static final Logger lobecorp$logger = LoggerFactory.getLogger("LobecorpBenchNoScreenshot");

	@Inject(method = "captureScreenshot(Ljava/lang/String;Lcom/zhongbai233/bench/api/neoforge/client/BenchCaptureOptions;)Ljava/util/concurrent/CompletableFuture;",
			at = @At("HEAD"), cancellable = true, require = 1)
	private void lobecorp$rejectScreenshot(String name, BenchCaptureOptions options,
	                                       CallbackInfoReturnable<CompletableFuture<Path>> callback) {
		String scenario = System.getProperty("modBench.scenarios", "");
		if (!LobecorpBenchConfig.PHOTON_FIRST_CAST_PERFORMANCE_SCENARIO.equals(scenario)
				&& !"lobecorp.horde-encounter-performance".equals(scenario)) {
			return;
		}

		lobecorp$logger.info("MODBENCH event=screenshot_suppressed evidence_mode=PERFORMANCE image_validation=NOT_USED name={}", name);
		callback.setReturnValue(CompletableFuture.failedFuture(
				new UnsupportedOperationException("性能专用 Bench 不提供截图取证：" + name)));
	}
}
