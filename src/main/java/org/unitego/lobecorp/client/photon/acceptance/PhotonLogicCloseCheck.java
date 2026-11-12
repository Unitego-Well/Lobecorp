package org.unitego.lobecorp.client.photon.acceptance;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.client.photon.editor.automation.PhotonEffectCommandException;
import org.unitego.lobecorp.util.photon.editor.automation.PhotonEffectProjectUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/// 在现有游戏宿主内检查原生关闭确认；由runner在客户端线程逐步调用。
public class PhotonLogicCloseCheck implements AutoCloseable {
	private final BiConsumer<String, Boolean> check;
	private final FXEditor host;
	private final Map<String, RuntimeObservation> observations = new LinkedHashMap<>();
	private EditorWindow window;
	private FXEditor editor;
	private FXProject project;
	private Dialog dialog;
	private FXRuntime runtime;
	private int stage;

	public PhotonLogicCloseCheck(BiConsumer<String, Boolean> check, FXEditor host) {
		this.check = Objects.requireNonNull(check);
		this.host = Objects.requireNonNull(host);
	}

	public void begin() {
		if (window != null || host.getModularUI() == null) {
			throw new IllegalStateException("需要已嵌入游戏的宿主，且不得重复开始");
		}
		window = new EditorWindow(FXEditor::new);
		host.addChild(window);
		editor = (FXEditor) window.getCurrentEditor();
		project = new FXProject();
		editor.loadProject(project, null);
		runtime = editor.runtime;
		check.accept("close.native_dirty_project", editor.getClass() == FXEditor.class
				&& editor.isCurrentProjectDirty() && runtime != null);
		check.accept("close.embedded_host", window.getModularUI() == host.getModularUI()
				&& !window.isInOsWindow());
		checkNativePreparationBoundary();
		observeRuntime("before_close");
		editor.close();
		dialog = findDialog();
		check.accept("close.real_save_dialog", dialog != null && dialog.getModularUI() == host.getModularUI());
		if (dialog == null) {
			throw new IllegalStateException("原生关闭未生成保存确认框");
		}
	}

	public boolean step() {
		if (window == null) {
			throw new IllegalStateException("尚未开始");
		}
		switch (stage) {
			case 0 -> {
				checkNativePreparationBoundary();
				observeRuntime("before_cancel");
				press("__cancel-button__");
				observeRuntime("after_cancel");
				check.accept("close.cancel_dialog_removed", dialog.getParent() == null);
				check.accept("close.cancel_window_project_runtime_identity",
						window.getParent() == host && window.getCurrentEditor() == editor
								&& window.getEditors().containsKey(editor)
								&& editor.getCurrentProject() == project && editor.runtime == runtime
								&& editor.isCurrentProjectDirty());
				checkNativePreparationBoundary();
				check.accept("close.cancel_runtime_lifecycle_unchanged",
						observations.get("before_cancel").equals(observations.get("after_cancel")));
				editor.close();
				dialog = Objects.requireNonNull(findDialog(), "再次关闭没有生成真实对话框");
				stage = 1;
				return false;
			}
			case 1 -> {
				// 保存框的reject表示不保存并确认退出；confirm会进入另一个文件保存框。
				press("__reject-button__");
				observeRuntime("after_confirm");
				check.accept("close.confirm_dialog_removed", dialog.getParent() == null);
				check.accept("close.confirm_tab_removed", window.getCurrentEditor() == null
						&& !window.getEditors().containsKey(editor));
				stage = 2;
				return true;
			}
			default -> {
				return true;
			}
		}
	}

	private Dialog findDialog() {
		return host.getModularUI().ui.rootElement.selfAndAllChildren()
				.filter(Dialog.class::isInstance).map(Dialog.class::cast)
				.filter(value -> value.buttonContainer.getChildren().stream()
						.anyMatch(button -> button.hasClass("__cancel-button__")))
				.reduce((first, last) -> last).orElse(null);
	}

	private void press(String styleClass) {
		var button = dialog.buttonContainer.getChildren().stream()
				.filter(Button.class::isInstance).filter(value -> value.hasClass(styleClass))
				.findFirst().orElseThrow();
		var event = UIEvent.create(UIEvents.MOUSE_DOWN);
		event.target = button;
		event.button = 0;
		UIEventDispatcher.dispatchEvent(event, false, false, false);
	}

	/// 空原生工程不进入组合作者队列，也无命名资源来源；零项不能作为任务保持证据。
	private void checkNativePreparationBoundary() {
		check.accept("close.native_zero_owned_preparation_sources",
				editor.getClass() == FXEditor.class && PhotonEditorTools.of(editor) == null
						&& project.getKnownAs().isEmpty() && project.getFx().getFxData().objects().isEmpty());
	}

	private void observeRuntime(String phase) {
		observations.put(phase, new RuntimeObservation(runtime.isAlive(), runtime.isFinished(), runtime.isValid(),
				editor.runtime == runtime, editor.getCurrentProject() == project));
	}

	/// 原始生命周期值供runner写报告；这些值本身不是清理成功断言。
	public Map<String, RuntimeObservation> runtimeObservations() {
		return Map.copyOf(observations);
	}

	/// 仅接受runner独占的真实桥接工程；不创建假Handle，不与原生对话框覆盖合并。
	public void checkBridgeDiscardRefusal(PhotonEffectProjectUtil.Handle owned) {
		var current = PhotonEffectProjectUtil.require(owned.id());
		if (current.editor() != owned.editor() || current.project() != owned.project()
				|| !current.editor().isCurrentProjectDirty()) {
			throw new IllegalArgumentException("需要当前真实且未保存的独占桥接工程");
		}
		var beforeRuntime = current.editor().runtime;
		var revision = PhotonEffectProjectUtil.revision(current.project());
		boolean rejected = false;
		try {
			new PhotonEffectProjectUtil.PreparedClose(current, revision, false).commit();
		} catch (PhotonEffectCommandException failure) {
			if (!failure.code().equals(PhotonEffectCommandException.Code.INVALID_ARGUMENT.name())) {
				throw failure;
			}
			rejected = true;
		}
		check.accept("close.bridge_discard_false_rejected", rejected);
		var after = PhotonEffectProjectUtil.require(owned.id());
		check.accept("close.bridge_refusal_preserves_project_runtime_revision",
				after.editor() == current.editor() && after.project() == current.project()
						&& after.editor().runtime == beforeRuntime
						&& PhotonEffectProjectUtil.revision(after.project()).equals(revision));
	}

	@Override
	public void close() {
		if (dialog != null && dialog.getParent() != null) {
			dialog.close();
		}
		if (editor != null) {
			if (editor.runtime != null) {
				editor.runtime.destroy(true);
			}
			editor.closeCurrentProject(false, null);
			editor.removeSelf();
		}
		if (window != null) {
			window.removeSelf();
			window.clearAllChildren();
		}
	}

	public record RuntimeObservation(boolean alive, boolean finished, boolean valid,
	                                 boolean editorStillReferencesRuntime, boolean projectStillAttached) {
	}
}
