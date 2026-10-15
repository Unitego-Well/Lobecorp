package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.registry.photon.client.LcPhotonEditorSettings;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationMigrationGuardUtil;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;

import java.io.File;

@Mixin(Editor.class)
public abstract class EditorMixin {
	/// 跨类型工程使用对应编辑器创建新标签，保留当前工程及其未保存状态。
	@Inject(method = "loadProject(Lcom/lowdragmc/lowdraglib2/editor/project/IProject;Ljava/io/File;)V",
			at = @At("HEAD"), cancellable = true)
	@SuppressWarnings("ConstantValue")
	private void lobecorp$openModelProject(IProject project, File projectFile, CallbackInfo ci) {
		if (project instanceof PhotonModelProject modelProject && (Object) this instanceof FXEditor editor
				&& !(editor instanceof PhotonModelProjectEditor)) {
			PhotonModelProjectEditor.open(editor, modelProject, projectFile);
			ci.cancel();
		} else if ((Object) this instanceof PhotonModelProjectEditor editor && project instanceof FXProject) {
			var window = editor.getWindow();

			if (window != null) {
				window.createNewEditor(FXEditor::new).loadProject(project, projectFile);
				ci.cancel();
			}
		}
	}

	@Inject(method = "saveProject(Ljava/lang/Runnable;Z)V", at = @At("HEAD"), cancellable = true)
	private void lobecorp$guardSave(Runnable onFinish, boolean showNotification, CallbackInfo ci) {
		if (PhotonAnimationMigrationGuardUtil.rejectSave((Editor) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "saveAsProject(Ljava/io/File;Ljava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
	private void lobecorp$guardSaveAs(File defaultSaveFile, Runnable onFinish, CallbackInfo ci) {
		if (PhotonAnimationMigrationGuardUtil.rejectSave((Editor) (Object) this)) {
			ci.cancel();
		}
	}

	@Inject(method = "initEditorSettings", at = @At("RETURN"))
	// 运行时 this 是目标 Editor；静态分析只看到独立 Mixin 类型。
	@SuppressWarnings("ConstantValue")
	private void lobecorp$registerPhotonSettings(CallbackInfo ci) {
		if ((Object) this instanceof FXEditor) {
			LcPhotonEditorSettings.register((Editor) (Object) this);
		}
	}
}
