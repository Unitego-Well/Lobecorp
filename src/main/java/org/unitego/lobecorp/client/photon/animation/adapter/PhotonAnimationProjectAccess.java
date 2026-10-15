package org.unitego.lobecorp.client.photon.animation.adapter;

import org.unitego.lobecorp.config.photon.animation.PhotonAnimationDefinition;

/// 工程专有测试时间轴桥接，不将测试定义加入可导出的动画库。
@SuppressWarnings("UnnecessaryModifier")
public interface PhotonAnimationProjectAccess {
	/// 首次使用时创建独立测试定义，工作区关闭后仍由工程持有。
	public PhotonAnimationDefinition lobecorp$previewDefinition();

	public void lobecorp$setPreviewDefinition(PhotonAnimationDefinition definition);
}
