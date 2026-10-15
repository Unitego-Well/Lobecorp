package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorAccess;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.client.photon.editor.PhotonSceneAxisView;
import org.unitego.lobecorp.client.photon.animation.editor.PhotonAnimationTab;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProject;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;
import org.unitego.lobecorp.client.photon.model.project.IPhotonModelEditorBridge;

import java.io.File;

@Mixin(FXEditor.class)
public abstract class FXEditorMixin extends Editor implements PhotonEditorAccess, IPhotonModelEditorBridge {
	@Unique
	private PhotonEditorTools lobecorp$editorTools;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void lobecorp$installTools(CallbackInfo ci) {
		var editor = (FXEditor) (Object) this;
		editor.sceneView.addChild(new PhotonSceneAxisView(editor.sceneView));
		if ((Object) this instanceof PhotonModelProjectEditor) {
			return;
		}

		lobecorp$editorTools = new PhotonEditorTools(editor);
	}

	@Inject(method = "initMenus", at = @At("RETURN"))
	// 运行时 this 是目标 FXEditor，保留 Mixin 的标准目标转换。
	@SuppressWarnings("DataFlowIssue")
	private void lobecorp$addMenus(CallbackInfo ci) {
		if ((Object) this instanceof PhotonModelProjectEditor) {
			return;
		}

		PhotonEditorTools.installMenus((FXEditor) (Object) this);
		PhotonModelProject.installFileTypes(getFileMenu());
	}

	@Inject(method = "closeCurrentProject", at = @At("HEAD"))
	private void lobecorp$clearTools(CallbackInfo ci) {
		if (getCurrentProject() instanceof FXProject && lobecorp$editorTools != null) {
			lobecorp$editorTools.clearPreview();
		}
	}

	@Inject(method = "loadNewProject", at = @At("HEAD"))
	private void lobecorp$clearPrevious(IProject project, File projectFile, CallbackInfo ci) {
		if (project instanceof FXProject && lobecorp$editorTools != null) {
			lobecorp$editorTools.clearPreview();
		}
	}

	@Inject(method = "loadNewProject", at = @At("RETURN"))
	private void lobecorp$bindPreview(IProject project, File projectFile, CallbackInfo ci) {
		if (project instanceof FXProject && lobecorp$editorTools != null) {
			lobecorp$editorTools.bindRuntime();
		}
	}

	@Override
	public PhotonEditorTools lobecorp$tools() {
		return lobecorp$editorTools;
	}

	@Inject(method = "reloadEffect", at = @At("RETURN"))
	private void lobecorp$syncAnimationScene(CallbackInfo ci) {
		if (getCurrentProject() instanceof FXProject) {
			PhotonAnimationTab.sceneChanged((FXEditor) (Object) this);
		}
	}

	@Override
	public void lobecorp$loadProjectBase(IProject project, @Nullable File projectFile) {
		super.loadNewProject(project, projectFile);
	}
}
