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
		if (mixinClassName.equals("org.unitego.lobecorp.mixin.minecraft.client.BufferBuilderMixin")) {
			return Boolean.getBoolean(PhotonPackedVertexUtil.ENABLE_PROPERTY);
		}

		return !BENCH_MIXINS.contains(mixinClassName)
				|| System.getProperty("modBench.scenarios", "").contains("lobecorp.photon-");
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
