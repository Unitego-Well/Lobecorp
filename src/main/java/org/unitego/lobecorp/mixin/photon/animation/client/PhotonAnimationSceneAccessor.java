package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Scene.class)
@SuppressWarnings("UnnecessaryModifier") // 项目规范要求接口成员显式 public。
public interface PhotonAnimationSceneAccessor {
	@Accessor("autoReleased")
	public void lobecorp$setAutoReleased(boolean value);
}
