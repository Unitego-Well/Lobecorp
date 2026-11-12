package org.unitego.lobecorp.client.photon.render;

import com.lowdragmc.photon.client.compat.iris.IrisCompat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;

import javax.annotation.Nullable;

/// Photon 自有管线在光影下的绘制边界：不让 Iris 把它们当作原版管线查找 override。
///
/// Iris 的 `MixinShaderManager_Overrides` 在 `GlDevice#getOrCompilePipeline` 头部拦截所有管线，
/// 找不到映射时首次输出带栈的缺失日志。Photon 的着色图、Bloom 与遮挡变体由 Photon 自己编译并绑定，
/// 不参与光影包的 override 表；只在本次 Photon 绘制期间切换 Iris 的通用 bypass，结束后恢复原值。
public final class PhotonIrisPipelineAccess {
	private PhotonIrisPipelineAccess() {
	}

	public static void useNativePipeline(Runnable draw) {
		if (!IrisCompat.isModInstalled()) {
			draw.run();
			return;
		}

		var bridge = IrisPipelineBridge.current();
		withBypass(bridge, draw);
	}

	/// 只有 Photon 自己注册的管线才不参与 Iris 的原版管线覆盖表。
	public static boolean isPhotonPipeline(RenderPipeline pipeline) {
		var location = pipeline.getLocation();
		return location.getNamespace().equals("photon")
				|| location.getNamespace().equals("lobecorp");
	}

	/// 只对 Photon 自有管线切换边界；原版或光影包管线保持 Iris 原有路由。
	public static void setPhotonPipeline(RenderPass pass, RenderPipeline pipeline) {
		if (isPhotonPipeline(pipeline)) {
			useNativePipeline(() -> pass.setPipeline(pipeline));
		} else {
			pass.setPipeline(pipeline);
		}
	}

	/// 正常或异常结束都恢复调用前状态。
	static void withBypass(Bypass bridge, Runnable draw) {
		boolean previous = bridge.bypass();
		bridge.bypass(true);
		try {
			draw.run();
		} finally {
			bridge.bypass(previous);
		}
	}

	/// 全屏后期通道由 Photon 自己建立并绑定，同样不进入原版管线覆盖表。
	public static void drawFullscreen(Runnable draw) {
		useNativePipeline(draw);
	}

	interface Bypass {
		boolean bypass();

		void bypass(boolean value);
	}

	/// Iris 引用隔离在独立类中，未安装 Iris 时不会加载该类。
	private static final class IrisPipelineBridge {
		private static @Nullable Bypass instance;

		private static Bypass current() {
			if (instance == null) {
				instance = Impl.create();
			}
			return instance;
		}
	}

	private static final class Impl implements Bypass {
		private static Bypass create() {
			if (!com.lowdragmc.photon.client.compat.iris.IrisCompat.isModInstalled()) {
				return new Bypass() {
					@Override
					public boolean bypass() {
						return false;
					}

					@Override
					public void bypass(boolean value) {
					}
				};
			}
			return new Impl();
		}

		@Override
		public boolean bypass() {
			return net.irisshaders.iris.vertices.ImmediateState.bypass;
		}

		@Override
		public void bypass(boolean value) {
			net.irisshaders.iris.vertices.ImmediateState.bypass = value;
		}
	}
}
