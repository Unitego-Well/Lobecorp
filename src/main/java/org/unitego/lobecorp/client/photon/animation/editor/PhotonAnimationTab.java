package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventListener;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationEditingUtil;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.config.photon.PhotonEditorSettings;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationTextUtil;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUiUtil;

public class PhotonAnimationTab extends View {
	private final FXEditor owner;
	private final UIEventListener commandGuard;
	private @Nullable PhotonAnimationWorkspace workspace;

	public PhotonAnimationTab(FXEditor owner) {
		super(PhotonAnimationTextUtil.key("title"), owner.timelineView.getIcon());
		this.owner = owner;
		commandGuard = event -> PhotonAnimationEditingUtil.guardObjectCommand(owner, event);
		owner.addEventListener(UIEvents.EXECUTE_COMMAND, commandGuard, true);
		owner.addEventListener(UIEvents.KEY_DOWN, commandGuard, true);
		setCanRemove(true);
		setOnRemove(this::closeWorkspace);
	}

	public static void install(FXEditor owner) {
		if (!(owner.getCurrentProject() instanceof FXProject) || owner.runtime == null
				|| !PhotonEditorSettings.of(owner).enabled(PhotonAnimationTextUtil.FEATURE)) {
			return;
		}

		if (owner.getAllViews().stream().anyMatch(PhotonAnimationTab.class::isInstance)) {
			return;
		}

		var container = owner.sceneView.getViewContainer();

		if (container == null) {
			return;
		}

		var tab = new PhotonAnimationTab(owner);
		owner.getViewFallbacks().put(tab, tab::sceneContainer);
		container.addViewAt(tab, container.getAllViews().indexOf(owner.sceneView) + 1);
	}

	public static void open(FXEditor owner) {
		if (!(owner.getCurrentProject() instanceof FXProject) || owner.runtime == null
				|| !PhotonEditorSettings.of(owner).enabled(PhotonAnimationTextUtil.FEATURE)) {
			return;
		}

		install(owner);

		for (var view : owner.getAllViews()) {
			if (!(view instanceof PhotonAnimationTab tab)) {
				continue;
			}

			if (owner.getFloatingViews().isFloating(tab)) {
				owner.getFloatingViews().dockBack(tab);
			}

			var container = tab.getViewContainer();

			if (container != null) {
				container.selectView(tab);
			}

			tab.initialize();
			return;
		}
	}

	@Override
	public Tab createTab() {
		var tab = super.createTab();
		PhotonAnimationUiUtil.tooltip(tab, "title");
		tab.setOnTabSelected(this::initialize);
		tab.setOnTabUnselected(this::hidePanels);
		return tab;
	}

	private ViewContainer sceneContainer() {
		var container = owner.sceneView.getViewContainer();
		return container == null ? owner.centerWindow.getLeftTop() : container;
	}

	private void initialize() {
		if (!(owner.getCurrentProject() instanceof FXProject) || owner.runtime == null
				|| !PhotonEditorSettings.of(owner).enabled(PhotonAnimationTextUtil.FEATURE)) {
			return;
		}

		if (workspace == null) {
			workspace = PhotonAnimationWorkspace.create(owner);

			if (workspace != null) {
				addChild(workspace);
			}
		}

		if (workspace != null) {
			workspace.showPanels(true);
		}
	}

	private void hidePanels() {
		if (workspace != null) {
			workspace.showPanels(false);
		}
	}

	/// 原项目的编辑、撤销和重播通知；没有打开动画窗口时不创建任何预览。
	@SuppressWarnings("resource") // 预览由工作区管理，此处仅通知同步，不转移所有权。
	public static void sceneChanged(FXEditor owner) {
		for (var view : owner.getAllViews()) {
			if (view instanceof PhotonAnimationTab tab && tab.workspace != null) {
				tab.workspace.preview().requestSync();
			}
		}
	}

	@SuppressWarnings("resource") // 只查询标签持有的工作区，不转移预览所有权。
	public static @Nullable PhotonAnimationWorkspace editing(FXEditor owner) {
		if (!(owner.getCurrentProject() instanceof FXProject)) {
			return null;
		}

		for (var view : owner.getAllViews()) {
			if (view instanceof PhotonAnimationTab tab && tab.workspace != null && tab.workspace.preview().editing()) {
				return tab.workspace;
			}
		}

		return null;
	}

	public static boolean replayingScene(FXEditor owner) {
		return owner.getAllViews().stream().filter(PhotonAnimationTab.class::isInstance)
				.map(PhotonAnimationTab.class::cast).anyMatch(tab -> tab.workspace != null
						&& tab.workspace.timelineContext().replayingScene());
	}

	public static void finishSceneRestore(FXEditor owner) {
		for (var view : owner.getAllViews()) {
			if (view instanceof PhotonAnimationTab tab && tab.workspace != null) {
				tab.workspace.timelineContext().finishSceneRestore();
			}
		}
	}

	public void closeWorkspace() {
		owner.removeEventListener(UIEvents.EXECUTE_COMMAND, commandGuard, true);
		owner.removeEventListener(UIEvents.KEY_DOWN, commandGuard, true);

		if (workspace != null) {
			workspace.closeWorkspace();
			workspace = null;
		}

		owner.getViewFallbacks().remove(this);
	}
}
