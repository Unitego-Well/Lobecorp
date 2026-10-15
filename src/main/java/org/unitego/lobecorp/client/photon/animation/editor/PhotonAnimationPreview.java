package org.unitego.lobecorp.client.photon.animation.editor;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.scene.SceneView;
import org.jetbrains.annotations.Nullable;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationPreviewScope;
import org.unitego.lobecorp.client.photon.animation.adapter.PhotonAnimationRuntimeAccess;
import org.unitego.lobecorp.client.photon.animation.runtime.PhotonAnimationRuntime;
import org.unitego.lobecorp.mixin.photon.animation.client.PhotonAnimationSceneAccessor;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationUtil;

import java.util.UUID;
import java.util.function.Consumer;

/// 动画模式借用原场景；不创建第二个编辑器、渲染器或粒子管理器。
public class PhotonAnimationPreview implements AutoCloseable {
	public final FXEditor host;
	private @Nullable UIElement originalParent;
	private @Nullable PhotonAnimationRuntime observedRuntime;
	private @Nullable ConfiguratorGroup inspectedGroup;
	private @Nullable PhotonAnimationInspectorSession inspectorSession;
	private boolean autoReleased;
	private boolean inspectorActive;
	private boolean timelineActive;
	private boolean infoActive;
	private boolean gizmoEnabled;
	private boolean animationVisible;
	private boolean scenePlaying;
	private boolean borrowed;
	private boolean closed;

	public PhotonAnimationPreview(FXEditor source) {
		host = source;
	}

	public void show(UIElement destination, boolean value) {
		if (closed || value == borrowed) {
			return;
		}

		if (!value) {
			restoreScene();
			return;
		}

		var view = scene();
		originalParent = view.getParent();
		autoReleased = view.sceneEditor.scene.isAutoReleased();
		inspectorActive = host.inspectorView.inspector.isActive();
		timelineActive = host.timelineView.isActive();
		infoActive = view.fxObjectInfoView.inspector.isActive();
		gizmoEnabled = view.sceneEditor.getTransformGizmo().isEnabled();
		animationVisible = view.fxObjectAnimationView.isDisplayed();
		scenePlaying = view.particleManager.isPlaying();
		((PhotonAnimationSceneAccessor) view.sceneEditor.scene).lobecorp$setAutoReleased(false);
		host.inspectorView.clear();
		host.inspectorView.inspector.setActive(true);
		view.fxObjectAnimationView.setDisplay(false);
		view.sceneEditor.setTransformGizmoTarget(null);
		host.timelineView.setRecordingTrack(null);
		host.timelineView.setActive(true);
		borrowed = true;

		if (host.runtime instanceof PhotonAnimationRuntimeAccess access) {
			access.lobecorp$setScenePreview(false);
		}

		destination.addChild(view);
		view.setDisplay(true);
		reset();
	}

	private void restoreScene() {
		if (!borrowed) {
			return;
		}

		borrowed = false;
		clearTargetInspection();
		scene().sceneEditor.setTransformGizmoTarget(null);

		if (originalParent != null && scene().getParent() != originalParent) {
			originalParent.addChild(scene());
		}

		((PhotonAnimationSceneAccessor) scene().sceneEditor.scene).lobecorp$setAutoReleased(autoReleased);
		host.inspectorView.inspector.setActive(inspectorActive);
		host.timelineView.setActive(timelineActive);
		scene().fxObjectInfoView.inspector.setActive(infoActive);
		scene().fxObjectAnimationView.setDisplay(animationVisible);

		if (gizmoEnabled) {
			scene().sceneEditor.enableTransformGizmo();
		} else {
			scene().sceneEditor.disableTransformGizmo();
		}

		if (host.runtime instanceof PhotonAnimationRuntimeAccess access) {
			access.lobecorp$setScenePreview(true);
		}

		if (scenePlaying) {
			scene().particleManager.play();
		} else {
			scene().particleManager.pause();
		}

		originalParent = null;
		observedRuntime = null;
		scenePlaying = false;
	}

	public boolean editing() {
		return borrowed && !closed;
	}

	public void requestSync() {
		// 共用原实例和场景，定义及相机修改无需复制或延迟同步。
	}

	public void sync() {
		if (!editing()) {
			return;
		}

		scene().fxObjectAnimationView.setDisplay(false);
		var current = runtimeOrNull();

		if (current != null && current != observedRuntime) {
			observedRuntime = current;
			current.previewOnly();
		}
	}

	public void adoptRuntime() {
		observedRuntime = runtimeOrNull();
	}

	public void reset() {
		var workspace = PhotonAnimationTab.editing(host);

		if (closed || !borrowed || host.runtime == null || workspace != null && workspace.effectFinished()) {
			return;
		}

		if (workspace != null) {
			workspace.preserveTestPlayback();
		}

		if (!active()) {
			scene().reset();
			PhotonAnimationPreviewScope.enter();

			try {
				host.runtime.emit(scene().effect);
			} finally {
				PhotonAnimationPreviewScope.leave();
			}

			scene().particleManager.tickInternal();
		} else if (host.runtime instanceof PhotonAnimationRuntimeAccess access) {
			access.lobecorp$reloadAnimations();
		}

		scene().particleManager.pause();

		if (active()) {
			observedRuntime = runtime();
			observedRuntime.previewOnly();
		}
	}

	public void inspect(UUID id) {
		var object = host.runtime == null ? null : host.runtime.getObjects().get(id);
		scene().fxObjectInfoView.inspect(object);
		scene().fxObjectInfoView.inspector.setActive(false);
	}

	public void inspectTimeline(PhotonAnimationWorkspace workspace, IConfigurable configurable,
	                            @Nullable Runnable onClose) {
		inspectTimeline(workspace, configurable, null, onClose);
	}

	public ConfiguratorGroup inspectTimeline(PhotonAnimationWorkspace workspace, IConfigurable configurable,
	                                         @Nullable Consumer<Configurator> listener, @Nullable Runnable onClose) {
		if (configurable instanceof FXObject target) {
			return host.inspectorView.inspect(target, listener, onClose);
		}

		if (inspectorSession != null && timelineInspectionActive() && inspectorSession.matches(workspace, configurable)) {
			return inspectedGroup;
		}

		var session = new PhotonAnimationInspectorSession(workspace, configurable, listener, onClose);
		inspectedGroup = host.inspectorView.inspector.inspect(session, session::changed, session::close);
		showInspector();
		inspectorSession = session;
		return inspectedGroup;
	}

	public void showInspector() {
		var container = host.inspectorView.getViewContainer();

		if (container != null) {
			container.selectView(host.inspectorView);
		}
	}

	private boolean timelineInspectionActive() {
		return inspectorSession != null && inspectorSession.active() && inspectedGroup != null
				&& inspectedGroup.getParent() != null && host.inspectorView.inspector.getInspectedConfigurable() == inspectorSession;
	}

	public void clearTimelineInspection() {
		if (inspectorSession == null) {
			return;
		}

		if (host.inspectorView.inspector.getInspectedConfigurable() == inspectorSession) {
			host.inspectorView.clear();
		}

		inspectorSession.close();
		inspectorSession = null;
		inspectedGroup = null;
	}

	private void clearTargetInspection() {
		host.inspectorView.clear();
		inspectedGroup = null;
		inspectorSession = null;
	}

	public @Nullable PhotonAnimationRuntime runtimeOrNull() {
		var current = host.runtime == null ? null : PhotonAnimationUtil.of(host.runtime);
		return current == null || current.closed() ? null : current;
	}

	public PhotonAnimationRuntime runtime() {
		var current = runtimeOrNull();

		if (current == null) {
			throw new IllegalStateException();
		}

		return current;
	}

	@SuppressWarnings("resource") // 查询不转移原 FXRuntime 的控制器所有权。
	public boolean active() {
		return runtimeOrNull() != null;
	}

	public SceneView scene() {
		return host.sceneView;
	}

	@Override
	public void close() {
		restoreScene();
		closed = true;
	}
}
