package org.unitego.lobecorp.client.photon.model.project;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.keymap.EditorAction;
import com.lowdragmc.lowdraglib2.editor.keymap.EditorActions;
import com.lowdragmc.lowdraglib2.editor.settings.BehaviorSettings;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorLayoutStore;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.editor.ui.menu.MenuTab;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.PhotonEditorActions;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.logging.annotations.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.Lobecorp;
import org.unitego.lobecorp.client.photon.model.editor.PhotonModelEditor;
import org.unitego.lobecorp.client.photon.model.editor.PhotonModelSceneController;
import org.unitego.lobecorp.client.photon.model.editor.PhotonModelTimelineView;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorTextUtil;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.ParametersAreNonnullByDefault;

/// 使用原生项目标签、文件菜单和历史面板承载模型、UV 与动画工作区。
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PhotonModelProjectEditor extends FXEditor {
	private @Nullable PhotonModelEditor workspace;
	private boolean workspaceInstalled;

	public PhotonModelProjectEditor() {
		removeWorkspaceView(hierarchyView);
		removeWorkspaceView(timelineView);
		sceneView.setFloatable(false);
		placeView(sceneView, () -> centerWindow.getLeftTop());
	}

	@Override
	protected void initMenus() {
		super.initMenus();
		PhotonModelProject.installFileTypes(fileMenu);
		fileMenu.registerMenuCreator((_, menu) -> {
			if (getCurrentProject() instanceof PhotonModelProject && getCurrentProjectFile() == null) {
				menu.leaf(Icons.SAVE, getKeymap().menuLabel(EditorActions.SAVE, "ldlib.gui.editor.tips.save"),
						() -> saveProject(null));
			}

			if (workspace != null) {
				workspace.appendFileMenu(menu);
			}
		});
		menuContainer.addChildren(
				directMenu(PhotonEditorTextUtil.key("documents"), () -> {
					if (workspace != null) {
						workspace.showDocuments();
					}
				}),
				directMenu(PhotonEditorTextUtil.key("settings"), this::openSettingsPanel));
		viewMenu.registerMenuCreator((_, menu) -> {
			var currentWorkspace = workspace;

			if (currentWorkspace == null) {
				return;
			}

			menu.branch(PhotonModelTextUtil.key("editor.panels"), panels -> {
				for (var view : List.of(currentWorkspace.hierarchyView(), currentWorkspace.uvView(),
						currentWorkspace.modelResourcesView(), inspectorView, historyView, sceneView,
						currentWorkspace.animationSceneView(), currentWorkspace.animationListView(), currentWorkspace.animationView(), resourceView)) {
					panels.leaf(view.getName(), () -> showPanel(view));
				}
			});
		});
	}

	private UIElement directMenu(String name, Runnable action) {
		return new MenuTab(this) {
			@Override
			protected Component getComponent() {
				return Component.translatable(name);
			}

			@Override
			protected TreeBuilder.Menu createDefaultMenu() {
				return TreeBuilder.Menu.start();
			}

			@Override
			public UIElement createMenuTab() {
				return super.createMenuTab().addEventListener(UIEvents.MOUSE_DOWN, event -> {
					event.stopImmediatePropagation();

					if (event.button == 0) {
						action.run();
					}
				}, true);
			}
		}.createMenuTab();
	}

	/// 已浮动的面板先返回停靠树，关闭的面板按原生登记位置重新打开。
	private void showPanel(View view) {
		rootWindow.restoreMaximized();
		var floating = getFloatingViews();

		if (floating.isFloating(view)) {
			floating.dockBack(view);
		} else if (view.getFirstAncestorOfType(ViewContainer.class) == null) {
			redockView(view);
		}

		var container = view.getFirstAncestorOfType(ViewContainer.class);

		if (container != null) {
			container.selectView(view);
		}
	}

	@Override
	protected Editor createNewEditorInstance() {
		return new PhotonModelProjectEditor();
	}

	@Override
	protected void initKeymap() {
		super.initKeymap();
		redirectTimelineAction(PhotonEditorActions.PLAY_PAUSE, PhotonModelTimelineView::togglePlay);
		redirectTimelineAction(PhotonEditorActions.STOP, PhotonModelTimelineView::stop);
		redirectTimelineAction(PhotonEditorActions.STEP_BACK, timeline -> timeline.stepFrames(-1));
		redirectTimelineAction(PhotonEditorActions.STEP_FORWARD, timeline -> timeline.stepFrames(1));
	}

	/// 沿用原生动作标识、默认键位和用户绑定，仅替换已移除的 FX 时间轴实例。
	private void redirectTimelineAction(Identifier id, Consumer<PhotonModelTimelineView> handler) {
		keymap.getAction(id).ifPresent(action -> keymap.register(new EditorAction(action.id(), action.category(),
				action.displayName(), action.defaultPrimary(), action.defaultSecondary(), action.when(), action.priority(), _ -> {
			if (workspace == null || !(workspace.animationView() instanceof PhotonModelTimelineView modelTimeline)) {
				return false;
			}

			handler.accept(modelTimeline);
			return true;
		})));
	}

	/// 在原窗口创建独立标签，原特效项目和未保存内容保持存活。
	public static void open(Editor origin, PhotonModelProject project, @Nullable File file) {
		var window = origin.getWindow();

		if (window == null) {
			throw new IllegalStateException(PhotonModelTextUtil.key("project.editor_required"));
		}

		window.createNewEditor(PhotonModelProjectEditor::new).loadProject(project, file);
	}

	@SuppressWarnings("unused") // 保留已有模型编辑器公共入口，场景改由原生 SceneView 持有。
	public TrackedDummyWorld previewLevel() {
		return sceneView.level;
	}

	/// 原生场景钩子只借用当前模型控制器；工程关闭后不保留旧预览。
	public @Nullable PhotonModelSceneController sceneController() {
		return workspace == null ? null : workspace.sceneController();
	}

	/// 由工程加载回调在原生布局恢复前安装视图和历史栈。
	public void installWorkspace(PhotonModelProject project) {
		project.session().setHistory(historyView);
		workspace = new PhotonModelEditor(project, this);
		placeView(workspace.hierarchyView(), () -> leftWindow.getLeftTop());
		placeView(workspace.uvView(), () -> leftWindow.getLeftTop());
		placeView(workspace.modelResourcesView(), () -> leftWindow.getLeftTop());
		placeView(workspace.animationListView(), () -> leftWindow.getLeftTop());
		placeView(sceneView, () -> centerWindow.getLeftTop());
		placeView(workspace.animationSceneView(), () -> centerWindow.getLeftTop());
		placeView(workspace.animationView(), () -> bottomWindow.getLeftTop());
		leftWindow.getLeftTop().selectView(workspace.hierarchyView());
		rightWindow.getRightTop().selectView(inspectorView);
		workspace.sceneController().initialize();
	}

	/// 原生 S 重启与场景模式切换通过模型控制器统一清理浮点时间残量。
	@Override
	public void reloadEffect() {
		var controller = sceneController();

		if (controller != null) {
			controller.restart();
		}
	}

	@Override
	protected void loadNewProject(IProject project, @Nullable File projectFile) {
		if (!(project instanceof PhotonModelProject model)) {
			throw new IllegalArgumentException(PhotonModelTextUtil.key("project.editor_required"));
		}

		if (projectFile != null) {
			model.updateModelName(projectFile);
		}

		workspaceInstalled = false;

		try {
			var behavior = BehaviorSettings.of(this);
			boolean restoreLayout = behavior.isRestoreLayoutOnProjectOpen();
			var savedLayout = restoreLayout ? EditorLayoutStore.load(project.getProjectType().getName()).orElse(null) : null;
			boolean legacyLayout = savedLayout != null && savedLayout.findSlotForView(sceneView.getName()) == null;

			// 旧模型或创建失败的布局不含原生场景；只在本次加载中跳过恢复。
			if (legacyLayout) {
				behavior.setRestoreLayoutOnProjectOpen(false);
			}

			try {
				((IPhotonModelEditorBridge) this).lobecorp$loadProjectBase(project, projectFile);
			} finally {
				if (legacyLayout) {
					behavior.setRestoreLayoutOnProjectOpen(true);
				}
			}

			if (workspace != null && workspace.animationListView().getViewContainer() == null
					&& !getFloatingViews().isFloating(workspace.animationListView())) {
				placeView(workspace.animationListView(), () -> leftWindow.getLeftTop());
			}

			if (workspace != null && workspace.animationSceneView().getViewContainer() == null) {
				placeView(workspace.animationSceneView(), () -> centerWindow.getLeftTop());
			}

			workspaceInstalled = true;
		} catch (RuntimeException | Error failure) {
			try {
				closeCurrentProject();
			} catch (RuntimeException cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			} catch (Error cleanupFailure) {
				cleanupFailure.addSuppressed(failure);
				throw cleanupFailure;
			}

			var window = getWindow();

			if (window != null && window.hasMultipleEditors()) {
				window.removeEditor(this);
			}

			if (failure instanceof Error fatal) {
				throw fatal;
			}

			// 普通加载失败返回回调，让原生确认弹窗继续完成关闭。
			Lobecorp.LOGGER.error(failure.getMessage(), failure);
			Dialog.showNotification("editor.error", "editor.loading_failed", null).show(window == null ? this : window);
		}
	}

	@Override
	public Component getTitle() {
		if (!(getCurrentProject() instanceof PhotonModelProject model)) {
			return super.getTitle();
		}

		var name = model.modelName().isBlank() ? Component.translatable(PhotonModelTextUtil.key("project.unnamed"))
				: Component.literal(model.modelName());
		return Component.translatable(PhotonModelTextUtil.key("project.title"), name);
	}

	/// 保存确认成功后释放模型工作区；取消关闭保留当前编辑会话。
	@Override
	public void exit(@Nullable Runnable onFinish) {
		super.exit(() -> {
			closeCurrentProject();

			if (onFinish != null) {
				onFinish.run();
			}
		});
	}

	/// 窗口实际移除时兜底释放；锁定版本的切换和最小化不会调用此回调。
	@Override
	protected void onRemoved() {
		super.onRemoved();
		closeCurrentProject();
	}

	@Override
	protected void closeCurrentProject() {
		try {
			super.closeCurrentProject();
		} finally {
			clearWorkspace();
			workspaceInstalled = false;
		}
	}

	/// 初始化失败不覆盖上次有效布局，但仍关闭已经恢复的浮动面板。
	@Override
	protected void saveLayoutAndCloseFloating() {
		if (workspaceInstalled) {
			super.saveLayoutAndCloseFloating();
			return;
		}

		getFloatingViews().closeAll();
	}

	private void clearWorkspace() {
		if (workspace != null) {
			workspace.close();
			removeWorkspaceView(workspace.hierarchyView());
			removeWorkspaceView(workspace.uvView());
			removeWorkspaceView(workspace.animationSceneView());
			removeWorkspaceView(workspace.animationView());
			removeWorkspaceView(workspace.animationListView());
			removeWorkspaceView(workspace.modelResourcesView());
			workspace = null;
		}
	}

	private void removeWorkspaceView(View view) {
		viewFallbacks.remove(view);
		view.removeSelf();
	}
}
