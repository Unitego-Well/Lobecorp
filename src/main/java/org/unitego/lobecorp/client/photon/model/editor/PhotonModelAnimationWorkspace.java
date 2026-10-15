package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.configurator.ui.ArrayConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.view.timeline.TimelineContext;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.model.PhotonModelAsset.Clip;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// 左侧沿用原生动画列表样式，模型片段和历史仍由独立模型会话管理。
public class PhotonModelAnimationWorkspace {
	private final PhotonModelEditSession session;
	private final PhotonModelSceneController scene;
	private final Consumer<String> onSelected;
	private final Runnable activateAnimation;
	private final View animations = new View(PhotonModelTextUtil.key("timeline.animations"), new TextTexture("A"));
	private final ScrollerView scroller = new ScrollerView();
	private final PhotonModelTimelineView timeline;
	private @Nullable ArrayConfiguratorGroup<Clip> list;
	private List<String> displayedIds = List.of();
	private String displayedSelection = "";
	private final Map<String, SceneState> animationStates = new HashMap<>();
	private final Object initialViewState;
	private @Nullable SceneState normalState;
	private String contextClip = "";
	private boolean switchingContext;

	private record SceneState(float time, boolean playing, Object viewState) {
	}

	public PhotonModelAnimationWorkspace(PhotonModelEditSession session, PhotonModelSceneController scene,
	                                     Consumer<String> onSelected, Runnable activateAnimation) {
		this.session = session;
		this.scene = scene;
		this.onSelected = onSelected;
		this.activateAnimation = activateAnimation;
		timeline = new PhotonModelTimelineView(session, scene, activateAnimation);
		animations.setId("model.animations");
		animations.getLayout().widthPercent(100).heightPercent(100).flex(1).minHeight(0);
		scroller.getLayout().widthPercent(100).flex(1).minHeight(0);
		animations.addEventListener(UIEvents.DRAG_END, _ -> commitOrder(), true);
		animations.addEventListener(UIEvents.MOUSE_DOWN, this::backgroundMenu);
		rebuild();
		initialViewState = timeline.captureViewState();
	}

	public View animationsView() {
		return animations;
	}

	public View timelineView() {
		return timeline;
	}

	public void refresh() {
		if (switchingContext) {
			return;
		}

		if (scene.isAnimationMode() && !contextClip.equals(session.selectedClip)) {
			switchAnimation(session.selectedClip);
		}

		var ids = session.asset().clips.stream().map(clip -> clip.id).toList();
		animationStates.keySet().removeIf(id -> !id.isEmpty() && !ids.contains(id));

		if (!ids.equals(displayedIds) || !session.selectedClip.equals(displayedSelection)) {
			rebuild();
		}

		timeline.refresh();
	}

	/// 两种场景借用同一时钟；退出动画后恢复进入前的常规场景播放状态。
	public void setAnimationMode(boolean animationMode) {
		if (scene.isAnimationMode() == animationMode) {
			return;
		}

		switchingContext = true;

		try {
			saveSceneState();
			scene.setAnimationMode(animationMode);
			contextClip = session.selectedClip;
			restoreSceneState();
		} finally {
			switchingContext = false;
		}

		refresh();
	}

	private void saveSceneState() {
		timeline.finishEditing();
		scene.time();
		boolean playing = session.playing;
		scene.pause();
		var state = new SceneState(scene.time(), playing, timeline.captureViewState());

		if (scene.isAnimationMode()) {
			animationStates.put(contextClip, state);
		} else {
			normalState = state;
		}
	}

	private void restoreSceneState() {
		var state = scene.isAnimationMode() ? animationStates.get(contextClip) : normalState;

		if (state == null) {
			state = new SceneState(0, false, initialViewState);
		}

		session.clearPose();
		scene.preview().previewMorph("");
		scene.seek(state.time());
		timeline.restoreViewState(state.viewState());

		if (!scene.isAnimationMode() && state.playing()) {
			scene.play();
		}
	}

	private void switchAnimation(String id) {
		switchingContext = true;

		try {
			saveSceneState();
			session.selectedClip = id;
			contextClip = id;
			scene.setAnimationClip(id);
			restoreSceneState();
		} finally {
			switchingContext = false;
		}
	}

	private void rebuild() {
		animations.clearAllChildren();
		var group = new ArrayConfiguratorGroup<Clip>("", false, List::of,
				(getter, _) -> animationName(getter.get()), false) {
			@Override
			protected void onAdd(@Nonnull UIEvent event) {
				create();
			}

			@Override
			protected void onRemove(@Nonnull UIEvent event) {
				deleteSelected();
			}
		};
		list = group;
		group.hideTitle();
		group.getLayout().widthPercent(100).heightPercent(100).flex(1).minHeight(0);
		group.configuratorContainer.getLayout().widthPercent(100).setFlexShrink(0);
		scroller.clearAllScrollViewChildren();
		scroller.addScrollViewChild(group.configuratorContainer);
		group.addChildAt(scroller, 1);
		group.buttonGroup.getLayout().setFlexShrink(0);
		group.addButton.setDisplay(true);
		group.removeButton.setDisplay(true);
		group.addButton.style(style -> style.tooltips(PhotonModelTextUtil.key("editor.add_clip")));
		group.removeButton.style(style -> style.tooltips(PhotonModelTextUtil.key("editor.delete_clip")));

		for (var clip : session.asset().clips) {
			var item = group.new ItemConfigurator(clip, group.configuratorProvider);
			group.addConfigurator(item);
			item.getLayout().setFlexShrink(0);
			item.addEventListener(UIEvents.MOUSE_DOWN, event -> {
				if (event.button == 1) {
					group.setSelected(item);
					choose(clip.id);
					openMenu(event, clip.id);
				}
			});

			if (clip.id.equals(session.selectedClip)) {
				group.setSelected(item);
			}
		}

		group.setOnSelectedChanged(clip -> choose(clip == null ? "" : clip.id));
		animations.addChild(group);
		displayedIds = session.asset().clips.stream().map(clip -> clip.id).toList();
		displayedSelection = session.selectedClip;
	}

	private Configurator animationName(Clip clip) {
		var row = new Configurator();
		row.getLayout().widthPercent(100);
		var name = new Label().setText(clip.id, false);
		TimelineContext.styleLabel(name);
		name.getLayout().widthPercent(100);
		row.addInlineChild(name);
		return row;
	}

	private void choose(String id) {
		activateAnimation.run();

		if (session.selectedClip.equals(id)) {
			onSelected.accept(id);
			return;
		}

		switchAnimation(id);
		onSelected.accept(id);
		refresh();
	}

	private void create() {
		timeline.finishEditing();
		var ui = animations.getModularUI();

		if (ui == null) {
			return;
		}

		int index = session.asset().clips.size() + 1;
		String name = Component.translatable(PhotonModelTextUtil.key("timeline.new_name"), index).getString();

		while (!availableName(name, "")) {
			name = Component.translatable(PhotonModelTextUtil.key("timeline.new_name"), ++index).getString();
		}

		Dialog.stringEditorDialog(PhotonModelTextUtil.key("editor.add_clip"), name,
				value -> availableName(value, ""), value -> safe(() -> {
					session.change("editor.add_clip", asset -> {
						var clip = new Clip();
						clip.id = value;
						asset.clips.add(clip);
					});
					choose(value);
				})).show(ui);
	}

	private boolean availableName(String candidate, String previous) {
		return candidate != null && !candidate.isBlank() && candidate.chars().noneMatch(Character::isISOControl)
				&& session.asset().clips.stream().noneMatch(clip -> clip.id.equals(candidate) && !clip.id.equals(previous));
	}

	private void rename(String id) {
		timeline.finishEditing();
		var ui = animations.getModularUI();

		if (ui == null) {
			return;
		}

		Dialog.stringEditorDialog(PhotonModelTextUtil.key("timeline.rename"), id,
				value -> availableName(value, id), value -> safe(() -> renameClip(id, value))).show(ui);
	}

	public void renameSelected(String name) {
		renameClip(session.selectedClip, name);
	}

	private void renameClip(String id, String name) {
		activateAnimation.run();
		saveSceneState();
		switchingContext = true;

		try {
			session.change("timeline.rename", asset -> asset.clips.stream().filter(clip -> clip.id.equals(id))
					.findFirst().orElseThrow().id = name);
			session.selectedClip = name;
			var state = animationStates.remove(id);

			if (state != null) {
				animationStates.put(name, state);
			}

			contextClip = name;
			scene.setAnimationClip(name);
			restoreSceneState();
		} finally {
			switchingContext = false;
		}

		onSelected.accept(name);
		refresh();
	}

	private void deleteSelected() {
		timeline.finishEditing();
		String id = session.selectedClip;

		if (session.clip() == null) {
			return;
		}

		safe(() -> {
			activateAnimation.run();
			scene.pause();
			switchingContext = true;

			try {
				session.change("editor.delete_clip", asset -> asset.clips.removeIf(clip -> clip.id.equals(id)));
				animationStates.remove(id);
				contextClip = session.selectedClip;
				scene.setAnimationClip(contextClip);
				restoreSceneState();
			} finally {
				switchingContext = false;
			}

			onSelected.accept(session.selectedClip);
			refresh();
		});
	}

	private void openMenu(UIEvent event, String id) {
		var editor = animations.getEditor();

		if (editor == null) {
			return;
		}

		var menu = TreeBuilder.Menu.start();
		menu.leaf(PhotonModelTextUtil.key("timeline.rename"), () -> rename(id));
		menu.leaf(PhotonModelTextUtil.key("editor.delete_clip"), this::deleteSelected);
		editor.openMenu(animations, event.x, event.y, menu);
		event.stopPropagation();
	}

	private void backgroundMenu(UIEvent event) {
		if (event.button != 1 || event.target != animations && event.target != scroller
				&& event.target != scroller.viewPort && event.target != scroller.viewContainer) {
			return;
		}

		var editor = animations.getEditor();

		if (editor != null) {
			editor.openMenu(animations, event.x, event.y,
					TreeBuilder.Menu.start().leaf(PhotonModelTextUtil.key("editor.add_clip"), this::create));
			event.stopPropagation();
		}
	}

	private void commitOrder() {
		if (list == null) {
			return;
		}

		var order = new ArrayList<String>();

		for (var child : list.getConfigurators()) {
			if (child instanceof ArrayConfiguratorGroup<?>.ItemConfigurator item && item.object instanceof Clip clip) {
				order.add(clip.id);
			}
		}

		if (order.size() != session.asset().clips.size() || order.equals(displayedIds)) {
			return;
		}

		safe(() -> session.change("timeline.reorder", asset -> {
			var clips = new ArrayList<>(asset.clips);
			asset.clips.clear();
			order.forEach(id -> clips.stream().filter(clip -> clip.id.equals(id)).findFirst().ifPresent(asset.clips::add));
		}));
	}

	private void safe(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException failure) {
			var ui = animations.getModularUI();

			if (ui != null) {
				Dialog.showNotification(PhotonModelTextUtil.key("editor"), Component.translatable(
						PhotonModelTextUtil.key("error"), String.valueOf(failure.getMessage())).getString(), () -> {
				}).show(ui);
			}
		}
	}

	public void close() {
		timeline.close();
		animationStates.clear();
		normalState = null;
		list = null;
		animations.clearAllChildren();
	}
}
