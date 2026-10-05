package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.photon.gui.editor.FXEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorAccess;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;

import java.io.File;

@Mixin(FXEditor.class)
public abstract class FXEditorMixin implements PhotonEditorAccess {
	@Unique
	private PhotonEditorTools lobecorp$editorTools;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void lobecorp$installTools(CallbackInfo ci) {
		lobecorp$editorTools = new PhotonEditorTools((FXEditor) (Object) this);
	}

	@Inject(method = "initMenus", at = @At("RETURN"))
	private void lobecorp$addMenus(CallbackInfo ci) {
		PhotonEditorTools.installMenus((FXEditor) (Object) this);
	}

	@Inject(method = "closeCurrentProject", at = @At("HEAD"))
	private void lobecorp$clearTools(CallbackInfo ci) {
		if (lobecorp$editorTools != null)
			lobecorp$editorTools.clearPreview();
	}

	@Inject(method = "loadNewProject", at = @At("HEAD"))
	private void lobecorp$clearPrevious(IProject project, File projectFile, CallbackInfo ci) {
		if (lobecorp$editorTools != null)
			lobecorp$editorTools.clearPreview();
	}

	@Inject(method = "loadNewProject", at = @At("RETURN"))
	private void lobecorp$bindPreview(IProject project, File projectFile, CallbackInfo ci) {
		if (lobecorp$editorTools != null)
			lobecorp$editorTools.bindRuntime();
	}

	@Override
	public PhotonEditorTools lobecorp$tools() {
		return lobecorp$editorTools;
	}
}
