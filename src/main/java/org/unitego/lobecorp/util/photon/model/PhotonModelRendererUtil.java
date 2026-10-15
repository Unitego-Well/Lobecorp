package org.unitego.lobecorp.util.photon.model;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;

/// 通过原生持久化复制完整渲染配置，保留材质参数和渲染扩展且不共享可变设置。
public class PhotonModelRendererUtil {
	public static RendererSetting copy(RendererSetting source) {
		var result = new RendererSetting();
		copyTo(source, result);
		return result;
	}

	public static void copyTo(RendererSetting source, RendererSetting target) {
		var provider = Platform.getFrozenRegistry();
		PersistedParser.deserializeNBT(PersistedParser.serializeNBT(source, provider), target, provider);
	}
}
