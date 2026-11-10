package org.unitego.lobecorp.launch.plugin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.unitego.lobecorp.util.photon.model.PhotonPackedVertexUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/// 生产 Mixin 的诊断与实验开关选择；不持有 Bench 场景装载或窗口状态。
public class PhotonMixinPlugin implements IMixinConfigPlugin {
	private static final Set<String> MATERIAL_MIXINS = Set.of(
			"org.unitego.lobecorp.mixin.photon.performance.client.MaterialSettingMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.MaterialRenderTypesMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.ShaderGraphMaterialMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.ShaderGraphRuntimeMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.RenderTypeFactoryMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.PhotonPipelinesMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.GlDeviceMixin",
			"org.unitego.lobecorp.mixin.photon.performance.client.GlProgramMixin");

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public @Nullable String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (MATERIAL_MIXINS.contains(mixinClassName)) {
			// 插件阶段不加载客户端材质或诊断类。
			return Boolean.getBoolean("lobecorp.photon.diag.material");
		}

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

		return true;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public @Nullable List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
