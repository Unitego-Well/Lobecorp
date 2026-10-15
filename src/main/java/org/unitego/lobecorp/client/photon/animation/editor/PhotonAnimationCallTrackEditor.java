package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.photon.client.fx.timeline.Track;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import com.lowdragmc.photon.gui.editor.view.timeline.TrackUIState;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.timeline.PhotonChildAnimationClip;
import org.unitego.lobecorp.config.photon.animation.PhotonAnimationLibrary;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTimelineUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;

/// 本特效动画调用仅替换候选库和片段默认模式，交互与实参表复用子动画编辑器。
public class PhotonAnimationCallTrackEditor extends PhotonChildAnimationTrackEditor {
	@Override
	public UIElement buildHeaderContent(TimelineContext context, Track track, TrackUIState state) {
		var label = new Label().setText(Component.translatable(PhotonAnimationTextUtil.key("call_target")));
		TimelineContext.styleLabel(label);
		label.setId("timeline.trackHeader.name").layout(layout -> layout.flex(1).heightPercent(100));
		PhotonAnimationUiUtil.tooltip(label, "call_target");
		return label;
	}

	@Override
	protected void buildHeaderConfigurator(ConfiguratorGroup group, TimelineContext context, Track track) {
	}

	@Override
	protected PhotonChildAnimationClip createClip(double start, double duration) {
		var clip = super.createClip(start, duration);
		clip.mode = PhotonChildAnimationClip.Mode.INDEPENDENT;
		return clip;
	}

	@Override
	protected String animationSelectorKey() {
		return "call_animation";
	}

	@Override
	protected String modeSelectorKey() {
		return "call_mode";
	}

	@Override
	protected @Nullable PhotonAnimationLibrary childLibrary(TimelineContext context, Track track) {
		var workspace = PhotonAnimationTimelineUtil.workspace(context);
		return workspace == null ? null : workspace.library();
	}
}
