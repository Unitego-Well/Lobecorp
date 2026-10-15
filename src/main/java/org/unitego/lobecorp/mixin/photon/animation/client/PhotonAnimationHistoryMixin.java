package org.unitego.lobecorp.mixin.photon.animation.client;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.view.HistoryView;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationTab;
import com.lowdragmc.lowdraglib2.gui.ui.utils.IHistoryStack.HistoryItem;

@Mixin(HistoryView.class)
public abstract class PhotonAnimationHistoryMixin {
	@Shadow
	@Final
	public Editor editor;

	@Inject(method = {"undo", "redo"}, at = @At("HEAD"))
	private void lobecorp$finishRecording(CallbackInfo ci) {
		if (editor instanceof FXEditor owner) {
			var workspace = PhotonAnimationTab.editing(owner);
			if (workspace != null) {
				workspace.timelineView().setRecordingTrack(null);
			}
		}
	}

	@Inject(method = "jumpToHistory", at = @At("HEAD"), cancellable = true)
	private void lobecorp$animationHistoryOnly(HistoryItem historyItem, CallbackInfo ci) {
		if (!(editor instanceof FXEditor owner)) {
			return;
		}

		var workspace = PhotonAnimationTab.editing(owner);
		if (workspace == null) {
			return;
		}

		workspace.timelineView().setRecordingTrack(null);
		if (!workspace.canRestoreHistory(historyItem)) {
			ci.cancel();
		}
	}

	@Inject(method = "pushHistory(Lnet/minecraft/network/chat/Component;Lcom/lowdragmc/lowdraglib2/configurator/EditAction;Ljava/lang/Object;Z)V", at = @At("RETURN"))
	private void lobecorp$afterEdit(CallbackInfo ci) {
		if (editor instanceof FXEditor owner) {
			PhotonAnimationTab.sceneChanged(owner);
		}
	}

	@Inject(method = "jumpToHistory", at = @At("RETURN"))
	private void lobecorp$afterRestore(CallbackInfo ci) {
		if (editor instanceof FXEditor owner) {
			PhotonAnimationTab.sceneChanged(owner);
		}
	}
}
