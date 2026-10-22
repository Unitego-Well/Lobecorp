package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// 借用同一编辑场景时保留其资源，归还后恢复原有自动释放策略。
@Mixin(Scene.class)
@SuppressWarnings("UnnecessaryModifier") // 项目接口成员保持显式 public。
public interface PhotonSceneAutoReleaseAccessor {
	@Accessor("autoReleased")
	public void lobecorp$setAutoReleased(boolean value);
}
