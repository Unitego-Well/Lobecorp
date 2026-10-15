package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.gui.editor.view.scene.FXObjectInfoView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationTab;

/// 命名模式的制作预览由共享 TL 控制，保留场景信息中的种子和性能信息。
@Mixin(FXObjectInfoView.class)
public abstract class PhotonAnimationSceneInfoMixin {
	@Unique
	private boolean lobecorp$hiddenControls;
	@Unique
	private boolean lobecorp$controlsDisplayed;
	@Unique
	private boolean lobecorp$timeDisplayed;

	@Inject(method = "initBasicInfo", at = @At("RETURN"))
	private void lobecorp$bindSharedTransport(CallbackInfo ci) {
		var view = (FXObjectInfoView) (Object) this;
		var controls = view.contentContainer.getChildren().get(0);
		var time = view.contentContainer.getChildren().get(1);
		view.addEventListener(UIEvents.TICK, _ -> lobecorp$syncTransport(view, controls, time));
	}

	@Unique
	private void lobecorp$syncTransport(FXObjectInfoView view, UIElement controls, UIElement time) {
		var named = view.sceneView.fxEditor != null && PhotonAnimationTab.editing(view.sceneView.fxEditor) != null;
		if (named == lobecorp$hiddenControls) {
			return;
		}

		lobecorp$hiddenControls = named;
		if (named) {
			lobecorp$controlsDisplayed = controls.isDisplayed();
			lobecorp$timeDisplayed = time.isDisplayed();
			controls.setDisplay(false);
			time.setDisplay(false);
			return;
		}

		controls.setDisplay(lobecorp$controlsDisplayed);
		time.setDisplay(lobecorp$timeDisplayed);
	}
}
