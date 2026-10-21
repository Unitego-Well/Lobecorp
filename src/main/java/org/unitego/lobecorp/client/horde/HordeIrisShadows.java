package org.unitego.lobecorp.client.horde;

import net.irisshaders.iris.api.v0.IrisApi;
import net.neoforged.fml.ModList;

/// 隔离可选 Iris API；只查询阴影阶段和深度方向，不提交虚拟阴影。
public class HordeIrisShadows {
	public static boolean shadowPass() {
		return ModList.get().isLoaded("iris") && IrisApi.getInstance().isRenderingShadowPass();
	}

	public static boolean reverseDepth() {
		return ModList.get().isLoaded("iris") && IrisApi.getInstance().isShaderPackInUse()
				&& IrisApi.getInstance().isReverseZDuringShaders();
	}
}
