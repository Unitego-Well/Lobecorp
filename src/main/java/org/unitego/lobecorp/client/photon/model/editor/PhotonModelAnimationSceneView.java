package org.unitego.lobecorp.client.photon.model.editor;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;
import org.unitego.lobecorp.mixin.photon.animation.client.PhotonAnimationSceneAccessor;
import org.unitego.lobecorp.util.photon.model.PhotonModelTextUtil;

/// 动画标签借用原模型场景，切换标签时保留同一世界、相机和粒子管理器。
public class PhotonModelAnimationSceneView extends View {
	private final PhotonModelProjectEditor owner;
	private final Runnable onEnter;
	private final Runnable onExit;
	private @Nullable UIElement originalParent;
	private boolean autoReleased;
	private boolean borrowed;
	private boolean closed;

	public PhotonModelAnimationSceneView(PhotonModelProjectEditor owner, Runnable onEnter, Runnable onExit) {
		super(PhotonModelTextUtil.key("animation"), owner.sceneView.getIcon());
		this.owner = owner;
		this.onEnter = onEnter;
		this.onExit = onExit;
		setId("model.animation_scene");
		setFloatable(false);
		setCanRemove(true);
		setOnRemove(onExit);
	}

	@Override
	public Tab createTab() {
		var tab = super.createTab();
		tab.setOnTabSelected(() -> {
			if (!closed) {
				onEnter.run();
			}
		});
		tab.setOnTabUnselected(() -> {
			if (!closed) {
				onExit.run();
			}
		});
		return tab;
	}

	/// 借用期间关闭自动释放，原场景回到原父节点后再恢复其释放策略。
	public void showScene(boolean value) {
		if (closed || value == borrowed) {
			return;
		}

		var scene = owner.sceneView;
		var access = (PhotonAnimationSceneAccessor) scene.sceneEditor.scene;

		if (value) {
			originalParent = scene.getParent();
			autoReleased = scene.sceneEditor.scene.isAutoReleased();
			access.lobecorp$setAutoReleased(false);
			borrowed = true;
			addChild(scene);
			scene.setDisplay(true);
			return;
		}

		if (originalParent != null && scene.getParent() != originalParent) {
			originalParent.addChild(scene);
		} else if (originalParent == null && scene.getParent() == this) {
			removeChild(scene);
		}

		access.lobecorp$setAutoReleased(autoReleased);
		originalParent = null;
		borrowed = false;
	}

	public void open() {
		if (closed) {
			return;
		}

		owner.rootWindow.restoreMaximized();
		var container = sceneContainer();

		if (getViewContainer() != container) {
			container.addView(this);
		}

		container.selectView(this);
	}

	private ViewContainer sceneContainer() {
		var container = owner.sceneView.getViewContainer();
		return container == null ? owner.centerWindow.getLeftTop() : container;
	}

	public void close() {
		showScene(false);
		closed = true;
	}
}
