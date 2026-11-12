package org.unitego.lobecorp.bench.plugin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/// 仅在 Bench source set 装载，维护动态场景选择和窗口保留门禁。
public class BenchMixinPlugin implements IMixinConfigPlugin {
	public static final String PRESERVE_WINDOW_PROPERTY = "lobecorp.photon.bench.preserveWindow";
	private static final String WINDOW_MIXIN = "org.unitego.lobecorp.mixin.bench.BenchWindowPreservationMixin";
	private static boolean preservedWindowApplied;
	private static boolean controlledDebugOverlayApplied;
	private static final Set<String> FAST_RUN_SCENARIOS = Set.of(
			"lobecorp.photon-12-run-cow25", "lobecorp.photon-12-run-queen25",
			"lobecorp.photon-12-run-laser3", "lobecorp.photon-12-run-blank45");
	private static final Set<String> BENCH_MIXINS = Set.of(
			"org.unitego.lobecorp.mixin.photon.performance.client.FXObjectMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.EmitterMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.ByteBufferBuilderMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.GlCommandEncoderMixin");

	@Override
	public @Nullable List<String> getMixins() {
		var scenario = System.getProperty("modBench.scenarios", "");
		if (FAST_RUN_SCENARIOS.contains(scenario)) {
			return List.of("bench.BenchWindowPreservationMixin", "bench.PhotonRunMinecraftMixin",
					"bench.PhotonRunSkillObservationMixin", "bench.PhotonQuietEntityRenderObservationMixin");
		}
		if (scenario.equals("lobecorp.photon-10-quiet-entity-render-diagnostic")) {
			if (!Boolean.getBoolean(PRESERVE_WINDOW_PROPERTY)) {
				throw new IllegalStateException("安静诊断要求显式保留游戏窗口。");
			}
			return List.of("bench.BenchWindowPreservationMixin", "bench.PhotonQuietMinecraftMixin",
					"bench.PhotonQuietSkillObservationMixin",
					"bench.PhotonQuietEntityRenderObservationMixin");
		}
		if (scenario.equals("lobecorp.photon-10-quiet-cow25")
				|| scenario.equals("lobecorp.photon-10-quiet-queen25")
				|| scenario.equals("lobecorp.photon-10-quiet-laser3")
				|| scenario.equals("lobecorp.photon-10-quiet-laser3-visual")
				|| scenario.equals("lobecorp.photon-10-quiet-blank45")) {
			if (!Boolean.getBoolean(PRESERVE_WINDOW_PROPERTY)) {
				throw new IllegalStateException("安静场景要求显式保留游戏窗口。");
			}
			return List.of("bench.BenchWindowPreservationMixin", "bench.PhotonQuietMinecraftMixin",
					"bench.PhotonQuietSkillObservationMixin",
					"bench.PhotonQuietEntityRenderObservationMixin");
		}
		if (scenario.equals("lobecorp.photon-06-migration-laser3")) {
			if (!Boolean.getBoolean(PRESERVE_WINDOW_PROPERTY)) {
				throw new IllegalStateException("迁移场景要求保留游戏窗口。");
			}
			return List.of("bench.BenchWindowPreservationMixin", "bench.PhotonMigrationMinecraftMixin",
					"bench.PhotonMigrationSkillObservationMixin");
		}
		if (scenario.equals("lobecorp.photon-05-hybrid-queen25")
				|| scenario.equals("lobecorp.photon-05-hybrid-laser3")
				|| scenario.equals("lobecorp.photon-05-hybrid-blank25")) {
			if (!Boolean.getBoolean(PRESERVE_WINDOW_PROPERTY)) {
				throw new IllegalStateException("混合场景要求保留游戏窗口。");
			}
			return List.of("bench.BenchWindowPreservationMixin", "bench.PhotonHybridMinecraftMixin",
					"bench.PhotonHybridSkillObservationMixin");
		}
		if (scenario.equals("lobecorp.photon-04-queen-entry-25s")
				|| scenario.equals("lobecorp.photon-04-entry-blank-25s")) {
			if (!Boolean.getBoolean(PRESERVE_WINDOW_PROPERTY)) {
				throw new IllegalStateException("进入场景要求显式保留游戏窗口。");
			}
			return List.of("bench.BenchWindowPreservationMixin", "bench.PhotonEntry25MinecraftMixin");
		}
		// 与 Bench 注册项对应；Mixin 插件不能提前加载 Bench 类。
		boolean performanceOnly = scenario.equals("lobecorp.photon-02-first-cast-performance")
				|| scenario.equals("lobecorp.photon-03-repeat-performance")
				|| scenario.equals("lobecorp.horde-encounter-performance");

		if (!scenario.equals("lobecorp.photon-00-first-cast")
				&& !scenario.equals("lobecorp.photon-01-resource-reload") && !performanceOnly) {
			return null;
		}

		if (!Boolean.getBoolean(PRESERVE_WINDOW_PROPERTY)) {
			throw new IllegalStateException("首次释放基准要求显式开启 " + PRESERVE_WINDOW_PROPERTY + "，禁止启动时修改窗口尺寸或全屏状态。");
		}

		if (scenario.equals("lobecorp.horde-encounter-performance")) {
			return List.of("bench.BenchWindowPreservationMixin", "bench.ClientAutomationNoScreenshotMixin",
					"bench.BenchControlledDebugOverlayMixin");
		}
		return performanceOnly
				? List.of("bench.BenchWindowPreservationMixin", "bench.ClientAutomationNoScreenshotMixin")
				: List.of("bench.BenchWindowPreservationMixin");
	}

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public @Nullable String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (BENCH_MIXINS.contains(mixinClassName)) {
			var scenario = System.getProperty("modBench.scenarios", "");
			// 插件阶段不加载客户端诊断类，保留原探针开关的默认值。
			return !FAST_RUN_SCENARIOS.contains(scenario)
					&& Boolean.parseBoolean(System.getProperty("lobecorp.photon.bench.probes", "true"))
					&& scenario.contains("lobecorp.photon-");
		}

		if (mixinClassName.startsWith("org.unitego.lobecorp.mixin.bench.PhotonQuiet")
				&& !mixinClassName.equals("org.unitego.lobecorp.mixin.bench.PhotonQuietEntityRenderObservationMixin")) {
			var scenario = System.getProperty("modBench.scenarios", "");
			return Boolean.parseBoolean(System.getProperty(PRESERVE_WINDOW_PROPERTY, "false"))
					&& (scenario.equals("lobecorp.photon-10-quiet-entity-render-diagnostic")
					|| scenario.equals("lobecorp.photon-10-quiet-cow25")
					|| scenario.equals("lobecorp.photon-10-quiet-queen25")
					|| scenario.equals("lobecorp.photon-10-quiet-laser3")
					|| scenario.equals("lobecorp.photon-10-quiet-laser3-visual")
					|| scenario.equals("lobecorp.photon-10-quiet-blank45"));
		}

		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	public static boolean controlledDebugOverlayApplied() {
		return controlledDebugOverlayApplied;
	}

	public static boolean preservedWindowApplied() {
		return preservedWindowApplied;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (WINDOW_MIXIN.equals(mixinClassName)) {
			preservedWindowApplied = true;
		}
		if ("org.unitego.lobecorp.mixin.bench.BenchControlledDebugOverlayMixin".equals(mixinClassName)) {
			controlledDebugOverlayApplied = true;
		}
	}

}
