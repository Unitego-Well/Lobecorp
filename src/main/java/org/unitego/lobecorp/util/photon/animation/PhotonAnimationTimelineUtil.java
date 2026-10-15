package org.unitego.lobecorp.util.photon.animation;

import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationTab;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationWorkspace;

import java.util.Objects;

public class PhotonAnimationTimelineUtil {
	public static Timeline of(TimelineContext context) {
		var workspace = workspace(context);

		if (workspace != null) {
			return workspace.timelineContext().animationTimeline();
		}

		return Objects.requireNonNull(context.runtime()).fxData.timeline();
	}

	public static @Nullable PhotonAnimationWorkspace workspace(TimelineContext context) {
		var owner = context.editor();
		return context == owner.timelineView ? PhotonAnimationTab.editing(owner) : null;
	}
}
