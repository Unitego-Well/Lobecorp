package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.registry.photon.client.LcPhotonEditorSettings;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;
import org.unitego.lobecorp.util.photon.editor.PhotonVisualQualityTextUtil;

import java.io.File;
import java.util.ArrayDeque;

@Mixin(Editor.class)
public abstract class EditorMixin {
	/// 跨类型工程使用对应编辑器创建新标签，保留当前工程及其未保存状态。
	@Inject(method = "loadProject(Lcom/lowdragmc/lowdraglib2/editor/project/IProject;Ljava/io/File;)V",
			at = @At("HEAD"), cancellable = true)
	private void lobecorp$openModelProject(IProject project, File projectFile, CallbackInfo ci) {
		if (PhotonEditorProjectGuardUtil.rejectOpen((Editor) (Object) this, project, projectFile)) {
			ci.cancel();
		}
	}

	@Inject(method = "saveProject(Ljava/lang/Runnable;Z)V", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("DataFlowIssue") // Mixin 合并后 this 是 Editor 实例。
	private void lobecorp$guardSave(Runnable onFinish, boolean showNotification, CallbackInfo ci) {
		if (PhotonEditorTools.isPhotonEditor((Editor) (Object) this) && PhotonEditorProjectGuardUtil.rejectSave((Editor) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "saveAsProject(Ljava/io/File;Ljava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("DataFlowIssue") // Mixin 合并后 this 是 Editor 实例。
	private void lobecorp$guardSaveAs(File defaultSaveFile, Runnable onFinish, CallbackInfo ci) {
		if (PhotonEditorTools.isPhotonEditor((Editor) (Object) this) && PhotonEditorProjectGuardUtil.rejectSave((Editor) (Object) this)) {
			ci.cancel();
		}
	}

	/// 只翻译扩展编辑器的导航显示，保留设置树路径和持久化 ID。
	@ModifyExpressionValue(method = "openSettingsPanel", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/editor/settings/EditorSettings;createSettingsPanel()Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;"))
	@SuppressWarnings("DataFlowIssue")
	private UIElement lobecorp$localizeSettingsNavigation(UIElement panel) {
		if (!PhotonEditorTools.isPhotonEditor((Editor) (Object) this)) {
			return panel;
		}

		var pending = new ArrayDeque<UIElement>();
		pending.add(panel);

		while (!pending.isEmpty()) {
			var element = pending.removeFirst();

			if (element instanceof TreeList<?> tree) {
				tree.setNodeUISupplier(TreeList.textTemplate(node -> PhotonVisualQualityTextUtil.navigation(node.toString())));
			} else {
				pending.addAll(element.getChildren());
			}
		}

		return panel;
	}

	@Inject(method = "initEditorSettings", at = @At("RETURN"))
	// 运行时 this 是目标 Editor；静态分析只看到独立 Mixin 类型。
	@SuppressWarnings("DataFlowIssue")
	private void lobecorp$registerPhotonSettings(CallbackInfo ci) {
		if (PhotonEditorTools.isPhotonEditor((Editor) (Object) this)) {
			LcPhotonEditorSettings.register((Editor) (Object) this);
		}
	}
}
