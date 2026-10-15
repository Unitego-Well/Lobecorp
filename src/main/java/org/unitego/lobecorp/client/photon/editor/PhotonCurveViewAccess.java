package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.photon.client.fx.timeline.AnimatedProperty;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.timeline.AnimationTrackEditor.AnimationTrackUIState;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;

@SuppressWarnings({"UnnecessaryModifier", "UnusedReturnValue"})
public interface PhotonCurveViewAccess {
	public void lobecorp$resetView(AnimatedProperty property);

	public boolean lobecorp$cancelCurve(TimelineContext ctx, AnimationTrackUIState state);

	public void lobecorp$noteCurveEdit(TimelineContext ctx, Track track, AnimationTrackUIState state);
}
