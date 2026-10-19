package org.unitego.lobecorp.mixin.photon.editor.client;

import com.lowdragmc.lowdraglib2.editor.ui.menu.FileMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorProjectGuardUtil;

import java.io.File;

@Mixin(FileMenu.class)
public abstract class FileMenuMixin {
	@Inject(method = "openProject", at = @At("HEAD"), cancellable = true)
	private void lobecorp$rejectExtendedProject(File file, CallbackInfoReturnable<Boolean> cir) {
		if (PhotonEditorProjectGuardUtil.rejectFile(((FileMenu) (Object) this).editor, file)) {
			cir.setReturnValue(true);
		}
	}
}
