package org.unitego.lobecorp.client.photon.animation.adapter;

import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;

@SuppressWarnings("UnnecessaryModifier")
public interface PhotonAnimationRuntimeAccess {
	public @Nullable PhotonAnimationRuntime lobecorp$animations();

	public void lobecorp$reloadAnimations();

	/// 仅标记编辑器主场景实例；场景模式不挂接命名动画运行态。
	public void lobecorp$setScenePreview(boolean sceneOnly);
}
