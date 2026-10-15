package org.unitego.lobecorp.mixin.photon.animation.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.gui.editor.view.timeline.AnimationTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTimelineUtil;

import java.util.UUID;

/// 删除、撤销和重新绑定仅释放动画贡献，不把记录姿态写回原对象。
@Mixin(AnimationTrackEditor.class)
public abstract class PhotonAnimationTrackEditorMixin {
	@Inject(method = "restoreBaseOf", at = @At("HEAD"), cancellable = true)
	private void lobecorp$keepAuthoredTarget(TimelineContext ctx, Track track, UUID targetId, CallbackInfo ci) {
		if (PhotonAnimationTimelineUtil.workspace(ctx) != null) {
			ci.cancel();
		}
	}

	@WrapOperation(method = {"lambda$removeProperty$0", "lambda$addProperty$1"},
			at = @At(value = "INVOKE",
					target = "Lcom/lowdragmc/photon/client/fx/timeline/AnimatedProperty;restoreBase(Lcom/lowdragmc/photon/client/gameobject/FXObject;)V"))
	private void lobecorp$keepAuthoredProperty(AnimatedProperty property, FXObject target,
	                                           Operation<Void> original, @Local(argsOnly = true, name = "ctx") TimelineContext ctx) {
		if (PhotonAnimationTimelineUtil.workspace(ctx) == null) {
			original.call(property, target);
		}
	}
}
