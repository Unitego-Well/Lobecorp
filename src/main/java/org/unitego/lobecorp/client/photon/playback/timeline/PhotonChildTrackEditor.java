package org.unitego.lobecorp.client.photon.playback.timeline;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.photon.client.fx.timeline.Clip;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.timeline.ClipTrackEditor;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackUIState;
import net.minecraft.network.chat.Component;

/// 现有窗口的选取与拖动沿用原生UI；完整来源/形参制作界面完成前仍受工程保护。
public class PhotonChildTrackEditor extends ClipTrackEditor {
	@Override
	public ColorPattern chipColor() {
		return ColorPattern.GREEN;
	}

	@Override
	public ColorPattern clipFillColor() {
		return ColorPattern.T_GREEN;
	}

	@Override
	public UIElement buildHeaderContent(TimelineContext context, Track track, TrackUIState state) {
		var label = new Label().setText(Component.literal(track.displayName()));
		TimelineContext.styleLabel(label);
		return label;
	}

	@Override
	protected String clipLabel(TimelineContext context, Track track, Clip value) {
		return value instanceof PhotonChildClip child ? child.animation : "";
	}

	@Override
	public boolean clipsConflict(Clip first, Clip second) {
		return false;
	}
}
