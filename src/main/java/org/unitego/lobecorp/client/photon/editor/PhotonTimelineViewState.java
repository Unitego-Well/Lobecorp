package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Marker;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackUIState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/// 临时编辑状态保留轨道身份；不写入特效资源。
public record PhotonTimelineViewState(float scale, float scroll, int rowHeight,
                                      Map<Track, TrackUIState> states, Set<Track> tracks,
                                      @Nullable Track track, Set<Clip> clips,
                                      @Nullable Clip clip, @Nullable Track clipTrack,
                                      @Nullable Marker marker, boolean markersVisible,
                                      float leftScroll, float rightScroll, Object modes,
                                      @Nullable Object previewRange) {
}
