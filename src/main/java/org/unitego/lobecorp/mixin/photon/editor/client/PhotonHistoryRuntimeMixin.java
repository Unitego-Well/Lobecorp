package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.view.HistoryView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorRuntimeUtil;

@Mixin(HistoryView.class)
public abstract class PhotonHistoryRuntimeMixin {
	@Shadow
	@Final
	public Editor editor;

	@Inject(method = "jumpToHistory", at = @At("RETURN"))
	private void lobecorp$refreshRestoredPreview(CallbackInfo ci) {
		if (editor instanceof PhotonEffectEditor effectEditor) {
			PhotonEditorRuntimeUtil.refreshRestoredProject(effectEditor);
		}
	}
}
