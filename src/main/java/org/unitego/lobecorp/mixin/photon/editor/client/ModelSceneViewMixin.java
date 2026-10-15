package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;

/// 原生场景的整数跳转与重置同步模型时钟，不影响普通特效工程。
@Mixin(SceneView.class)
public abstract class ModelSceneViewMixin {
	@Shadow
	@Final
	public FXEditor fxEditor;

	@Inject(method = "simulateTo", at = @At("TAIL"))
	private void lobecorp$seekModel(long time, CallbackInfo ci) {
		if (fxEditor instanceof PhotonModelProjectEditor editor) {
			var controller = editor.sceneController();

			if (controller != null) {
				controller.onNativeSeek(time);
			}
		}
	}

	@Inject(method = "reset", at = @At("HEAD"))
	private void lobecorp$resetModel(CallbackInfo ci) {
		if (fxEditor instanceof PhotonModelProjectEditor editor) {
			var controller = editor.sceneController();

			if (controller != null) {
				controller.onNativeReset();
			}
		}
	}
}
