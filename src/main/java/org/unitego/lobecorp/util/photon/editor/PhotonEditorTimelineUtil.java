package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import org.unitego.lobecorp.client.photon.editor.PhotonNamedTimelineContext;

import java.util.Objects;

/// 编辑工具访问当前宿主的轨道树，命名制作不得落回主场景时间轴。
public class PhotonEditorTimelineUtil {
	public static Timeline of(TimelineContext context) {
		if (context instanceof PhotonNamedTimelineContext named) {
			return named.draft().timeline();
		}
		return Objects.requireNonNull(context.runtime()).getFxData().timeline();
	}
}
