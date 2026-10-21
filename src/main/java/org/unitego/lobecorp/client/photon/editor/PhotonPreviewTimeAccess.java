package org.unitego.lobecorp.client.photon.editor;

/// 暂停预览的帧内时间，不写入工程或替换原生整数模拟时钟。
public interface PhotonPreviewTimeAccess {
	void lobecorp$seekFraction(float fraction);
}
