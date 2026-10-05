package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.photon.gui.editor.view.timeline.ControlTrackEditor.ControlTrackUIState;
import com.lowdragmc.photon.gui.editor.view.timeline.SignalTrackEditor.SignalTrackUIState;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackGroupEditor.TrackGroupUIState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ControlTrackUIState.class, SignalTrackUIState.class, TrackGroupUIState.class})
@SuppressWarnings("UnnecessaryModifier")
public interface TrackRenameUIStateAccessor {
	@Accessor("editingName")
	public boolean lobecorp$isEditingName();

	@Accessor("editingName")
	public void lobecorp$setEditingName(boolean editing);
}

