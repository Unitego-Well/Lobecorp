package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.File;

@Mixin(ProjectType.class)
public abstract class PhotonProjectLoadGuardMixin {
	@Inject(method = "loadProjectFromFile", at = @At("HEAD"))
	private void lobecorp$checkOriginalData(File file, CallbackInfoReturnable<IProject> cir) {
		PhotonEditorProjectGuardUtil.requireSupportedFile(file);
	}

	@Inject(method = "saveProjectToFile", at = @At("HEAD"))
	private void lobecorp$checkBeforeWriting(IProject project, File file, CallbackInfo ci) {
		PhotonEditorProjectGuardUtil.requireSupportedFile(file);
		if (PhotonEditorProjectGuardUtil.containsExtensions(project)) {
			throw new IllegalArgumentException(PhotonEditorProjectGuardUtil.message());
		}
	}
}
