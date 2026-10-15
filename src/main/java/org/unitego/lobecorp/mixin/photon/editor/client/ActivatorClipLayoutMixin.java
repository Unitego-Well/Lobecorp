package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.ActivatorTrack;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.FXTimelineView;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.editor.PhotonActivatorClipUtil;

/// 仅调整激活片段视图几何，原生登记、框选和组拖动保持统一入口。
@Mixin(FXTimelineView.class)
public abstract class ActivatorClipLayoutMixin {
	@Shadow
	private Clip dragGuideClip;

	@Shadow
	private Track trackOf(Clip clip) {
		throw new AssertionError();
	}

	@WrapOperation(method = "registerClipView", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/gui/editor/view/FXTimelineView;registerLaneItem(Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;Ljava/lang/Runnable;)V"))
	private void lobecorp$activationReposition(FXTimelineView view, UIElement element, Runnable reposition,
	                                           Operation<Void> original, @Local(argsOnly = true, name = "track") Track track, @Local(argsOnly = true, name = "clip") Clip clip) {
		if (!(track instanceof ActivatorTrack)) {
			original.call(view, element, reposition);
			return;
		}

		original.call(view, element, (Runnable) () -> {
			reposition.run();
			PhotonActivatorClipUtil.reposition(view, track, clip, element);
		});
	}

	@Inject(method = "repositionSelectedClips", at = @At("RETURN"))
	private void lobecorp$activationGroupLayout(CallbackInfo ci) {
		var ctx = (TimelineContext) this;
		if (!PhotonEditorSettings.of(ctx.editor()).enabled("control")) {
			return;
		}

		ctx.refreshLaneLayout();
	}

	@WrapOperation(method = "lambda$createRightColumn$7", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/gui/editor/view/FXTimelineView;drawGuideLine(Lcom/lowdragmc/lowdraglib2/gui/ui/rendering/GUIContext;DFFFF)V", ordinal = 0))
	private void lobecorp$activationStartGuide(FXTimelineView view, GUIContext graphics, double tick,
	                                           float x, float y, float width, float height, Operation<Void> original) {
		if (PhotonActivatorClipUtil.showsBoundary(view, trackOf(dragGuideClip), true)) {
			original.call(view, graphics, tick, x, y, width, height);
		}
	}

	@WrapOperation(method = "lambda$createRightColumn$7", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/photon/gui/editor/view/FXTimelineView;drawGuideLine(Lcom/lowdragmc/lowdraglib2/gui/ui/rendering/GUIContext;DFFFF)V", ordinal = 1))
	private void lobecorp$activationEndGuide(FXTimelineView view, GUIContext graphics, double tick,
	                                         float x, float y, float width, float height, Operation<Void> original) {
		if (PhotonActivatorClipUtil.showsBoundary(view, trackOf(dragGuideClip), false)) {
			original.call(view, graphics, tick, x, y, width, height);
		}
	}
}
