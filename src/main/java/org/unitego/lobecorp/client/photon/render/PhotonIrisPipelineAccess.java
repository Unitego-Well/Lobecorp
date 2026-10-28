package org.unitego.lobecorp.client.photon.render;

import net.irisshaders.iris.vertices.ImmediateState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// Photon 自有管线在光影下的绘制边界：不让 Iris 把它们当作原版管线查找 override。
///
/// Iris 的 `MixinShaderManager_Overrides` 在 `GlDevice#getOrCompilePipeline` 头部拦截所有管线，
/// 找不到映射时首次输出带栈的缺失日志。Photon 的着色图、Bloom 与遮挡变体由 Photon 自己编译并绑定，
/// 不参与光影包的 override 表；只在本次 Photon 绘制期间切换 Iris 的通用 bypass，结束后恢复原值。
public final class PhotonIrisPipelineAccess {
	private PhotonIrisPipelineAccess() {
	}

	public static void useNativePipeline(Runnable draw) {
		withBypass(() -> ImmediateState.bypass, value -> ImmediateState.bypass = value, draw);
	}

	/// 正常或异常结束都恢复调用前状态。
	static void withBypass(BooleanSupplier current, Consumer<Boolean> setter, Runnable draw) {
		boolean previous = current.getAsBoolean();
		setter.accept(true);
		try {
			draw.run();
		} finally {
			setter.accept(previous);
		}
	}

	/// 仅包住一次 `setPipeline`，不覆盖后续 uniform、纹理和 draw 调用。
	public static void setPipeline(RenderPass pass, RenderPipeline pipeline) {
		useNativePipeline(() -> pass.setPipeline(pipeline));
	}
}
