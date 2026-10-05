package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.timeline.ControlTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.SignalTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackGroupEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackUIState;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorRenameUtil;

@Mixin({ControlTrackEditor.class, SignalTrackEditor.class, TrackGroupEditor.class})
public abstract class TrackEditorMixin {
	@Inject(method = "buildHeaderContent", at = @At("HEAD"))
	private void lobecorp$renameDialog(TimelineContext ctx, Track track, TrackUIState state, CallbackInfoReturnable<UIElement> cir) {
		var access = (TrackRenameUIStateAccessor) state;
		if (!access.lobecorp$isEditingName() || !PhotonEditorSettings.of(ctx.editor()).enabled("rename_dialog"))
			return;
		access.lobecorp$setEditingName(false);
		if (!track.lock())
			PhotonEditorRenameUtil.track(ctx, track);
	}
}

