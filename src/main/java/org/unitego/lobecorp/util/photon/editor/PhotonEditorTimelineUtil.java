package org.unitego.lobecorp.util.photon.editor;

import com.lowdragmc.photon.client.fx.timeline.Timeline;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;

import java.util.Objects;

/// 通用编辑工具只访问原生特效时间轴。
public class PhotonEditorTimelineUtil {
	public static Timeline of(TimelineContext context) {
		return Objects.requireNonNull(context.runtime()).getFxData().timeline();
	}
}
