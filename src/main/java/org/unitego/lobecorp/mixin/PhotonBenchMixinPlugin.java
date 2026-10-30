package org.unitego.lobecorp.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.unitego.lobecorp.util.photon.model.PhotonPackedVertexUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/// 高频探针限定于显式 Photon Bench，整块提交由独立实验开关控制；其余 Mixin 选择不变。
public class PhotonBenchMixinPlugin implements IMixinConfigPlugin {
	public static final String PRESERVE_WINDOW_PROPERTY = "lobecorp.photon.bench.preserveWindow";
	private static final String WINDOW_MIXIN = "org.unitego.lobecorp.mixin.bench.ClientGraphicsControllerMixin";
	private static boolean preservedWindowApplied;
	private static final Set<String> BENCH_MIXINS = Set.of(
			"org.unitego.lobecorp.mixin.photon.performance.client.FXObjectMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.EmitterMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.ByteBufferBuilderMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.GlCommandEncoderMixin");

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public @Nullable String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (mixinClassName.equals("org.unitego.lobecorp.mixin.photon.performance.client.PhotonFXLoadDiagnosticsMixin")
				|| mixinClassName.equals("org.unitego.lobecorp.mixin.photon.performance.client.PhotonObjectDeserializeDiagnosticsMixin")
				|| mixinClassName.equals("org.unitego.lobecorp.mixin.photon.performance.client.PhotonFactoryDeserializeDiagnosticsMixin")
				|| mixinClassName.equals("org.unitego.lobecorp.mixin.photon.performance.client.PhotonVersionDeserializeDiagnosticsMixin")
				|| mixinClassName.equals("org.unitego.lobecorp.mixin.photon.performance.client.PhotonPersistedDeserializeDiagnosticsMixin")) {
			// 插件阶段只读属性，不提前加载客户端诊断类。
			return Boolean.getBoolean("lobecorp.photon.diag.cast");
		}

		if (mixinClassName.equals("org.unitego.lobecorp.mixin.minecraft.client.BufferBuilderMixin")) {
			return Boolean.getBoolean(PhotonPackedVertexUtil.ENABLE_PROPERTY);
		}

		if (BENCH_MIXINS.contains(mixinClassName)) {
			// 字面量与 PhotonSubmissionDiagnostics.PROBES_PROPERTY 一致；这里不加载 Minecraft 类。
			return Boolean.parseBoolean(System.getProperty("lobecorp.photon.bench.probes", "true"))
					&& System.getProperty("modBench.scenarios", "").contains("lobecorp.photon-");
		}

		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public @Nullable List<String> getMixins() {
		var scenario = System.getProperty("modBench.scenarios", "");
		// 与 Bench 注册项对应；Mixin 插件不能提前加载 Bench 类。
		boolean performanceOnly = scenario.equals("lobecorp.photon-02-first-cast-performance")
				|| scenario.equals("lobecorp.photon-03-repeat-performance");

		if (!scenario.equals("lobecorp.photon-00-first-cast")
				&& !scenario.equals("lobecorp.photon-01-resource-reload") && !performanceOnly) {
			return null;
		}

		if (!Boolean.getBoolean(PRESERVE_WINDOW_PROPERTY)) {
			throw new IllegalStateException("首次释放基准要求显式开启 " + PRESERVE_WINDOW_PROPERTY + "，禁止启动时修改窗口尺寸或全屏状态。");
		}

		return performanceOnly
				? List.of("bench.ClientGraphicsControllerMixin", "bench.ClientAutomationNoScreenshotMixin")
				: List.of("bench.ClientGraphicsControllerMixin");
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
	}
}
