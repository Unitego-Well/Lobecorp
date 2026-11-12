package org.unitego.lobecorp.client.horde;

import org.unitego.lobecorp.config.HordePathVariant;
import org.unitego.lobecorp.config.client.HordeVisualConfig;

/// 在渲染线程锁存生效设置，同一绘制过程不重新读取可变配置。
public class HordeRenderPolicy {
    private static Options nativeOptions = new Options(true, true, false);
    private static Options irisOptions = new Options(true, false, false);
    private static Settings active = baseline(false);

    public static boolean refresh(boolean shaderPackActive) {
        boolean motion = HordePathVariant.motionKernel();
        boolean selected = HordePathVariant.selectedScatter();
        boolean depth = HordePathVariant.depthScissor();
        if (HordePathVariant.current() == HordePathVariant.A_ADAPTIVE) {
            var options = readOptions(shaderPackActive);
            motion = options.motionKernel();
            selected = options.selectedScatter();
            depth = options.depthScissor();
        }

        if (active.shaderPackActive() == shaderPackActive && active.motionKernel() == motion
                && active.selectedScatter() == selected && active.depthScissor() == depth) {
            return false;
        }

        active = HordePathVariant.current() == HordePathVariant.A_ADAPTIVE
                ? forPath(shaderPackActive, nativeOptions, irisOptions)
                : new Settings(shaderPackActive, motion, selected, depth);
        return true;
    }

    public static Settings settings() {
        return active;
    }

    public static boolean motionKernel() {
        return active.motionKernel();
    }

    public static boolean selectedScatter() {
        return active.selectedScatter();
    }

    public static boolean depthScissor() {
        return active.depthScissor();
    }

    public static Settings forPath(boolean shaderPackActive, Options nativeOptions, Options irisOptions) {
        var options = shaderPackActive ? irisOptions : nativeOptions;
        return new Settings(shaderPackActive, options.motionKernel(), options.selectedScatter(), options.depthScissor());
    }

    private static Options readOptions(boolean iris) {
        boolean motion = !iris || HordeVisualConfig.IRIS_GROUPED_MOTION.get();
        boolean selected = !iris;
        boolean depth = false;
        var cached = iris ? irisOptions : nativeOptions;
        if (cached.motionKernel() == motion && cached.selectedScatter() == selected && cached.depthScissor() == depth) {
            return cached;
        }

        var options = new Options(motion, selected, depth);
        if (iris) {
            irisOptions = options;
        } else {
            nativeOptions = options;
        }
        return options;
    }

    private static Settings baseline(boolean shaderPackActive) {
        return new Settings(shaderPackActive, HordePathVariant.motionKernel(),
                HordePathVariant.selectedScatter(), HordePathVariant.depthScissor());
    }

    public record Options(boolean motionKernel, boolean selectedScatter, boolean depthScissor) {
    }

    /// 光影状态属于缓存键，即使内核选项相同，切换光影也必须重建相关资源。
    public record Settings(boolean shaderPackActive, boolean motionKernel, boolean selectedScatter, boolean depthScissor) {
    }
}
