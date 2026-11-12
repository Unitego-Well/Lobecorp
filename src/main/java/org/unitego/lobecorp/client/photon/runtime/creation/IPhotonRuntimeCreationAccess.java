package org.unitego.lobecorp.client.photon.runtime.creation;

import com.lowdragmc.photon.client.fx.FXData;

/// 原生构造器回调快照仅由受管租约复位，避免旧播放闭包留在池中。
public interface IPhotonRuntimeCreationAccess {
	void lobecorp$restoreCreationCallbacks();

	void lobecorp$resetCreationState(FXData data);
}
