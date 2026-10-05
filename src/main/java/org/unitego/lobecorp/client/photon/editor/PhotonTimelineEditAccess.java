package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackUIState;

import java.util.Map;
import java.util.Set;

@SuppressWarnings("UnnecessaryModifier")
public interface PhotonTimelineEditAccess {
	public Map<Track, TrackUIState> lobecorp$states();

	public Set<Track> lobecorp$selectedTracks();

	public TrackEditor lobecorp$trackEditor(Track track);

	public void lobecorp$fitTime(double start, double end);

	public void lobecorp$duplicateNative();

	public void lobecorp$cancelClipDrag();
}
