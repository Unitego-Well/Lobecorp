package org.unitego.lobecorp.client.photon.editor;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.ViewContainer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Tab;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.registry.translation.photon.animation.PhotonAnimationTranslationKeys;
import org.unitego.lobecorp.mixin.photon.editor.client.PhotonSceneAutoReleaseAccessor;

/// 动画标签借用原模型场景，切换标签时保留同一世界、相机和粒子管理器。
public class PhotonNamedAnimationScene extends View {
	private final FXEditor owner;
	private final Runnable onEnter;
	private final Runnable onExit;
	private @Nullable UIElement originalParent;
	private boolean autoReleased;
	private boolean borrowed;
	private boolean closed;

	public PhotonNamedAnimationScene(FXEditor owner, Runnable onEnter, Runnable onExit) {
		super(PhotonAnimationTranslationKeys.TITLE, owner.sceneView.getIcon());
		this.owner = owner;
		this.onEnter = onEnter;
		this.onExit = onExit;
		setId("lobecorp.named_animation_scene");
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
		var access = (PhotonSceneAutoReleaseAccessor) scene.sceneEditor.scene;

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
