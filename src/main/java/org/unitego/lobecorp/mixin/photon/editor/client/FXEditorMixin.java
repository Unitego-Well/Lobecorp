package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.FXProject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorAccess;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.client.photon.editor.PhotonSceneAxisView;

import java.io.File;


@Mixin(FXEditor.class)
public abstract class FXEditorMixin extends Editor implements PhotonEditorAccess {
	@Unique
	private PhotonEditorTools lobecorp$editorTools;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void lobecorp$installTools(CallbackInfo ci) {
		if (!PhotonEditorTools.isPhotonEditor(this)) {
			return;
		}

		var editor = (FXEditor) (Object) this;
		editor.sceneView.addChild(new PhotonSceneAxisView(editor.sceneView));

		lobecorp$editorTools = new PhotonEditorTools(editor);
		PhotonEditorTools.installMenus(editor);
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
		if (getCurrentProject() instanceof FXProject && lobecorp$editorTools != null) {
			lobecorp$editorTools.bindRuntime();
		}
	}

}
