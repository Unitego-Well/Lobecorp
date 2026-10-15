package org.unitego.lobecorp.mixin.photon.animation.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.SceneEditor;
import com.lowdragmc.lowdraglib2.math.ITransform;
import org.spongepowered.asm.mixin.Mixin;
import org.unitego.lobecorp.util.photon.animation.PhotonAnimationEditingUtil;

@Mixin(SceneEditor.class)
public abstract class PhotonAnimationSceneEditorMixin {
	@WrapMethod(method = "setTransformGizmoTarget(Lcom/lowdragmc/lowdraglib2/math/ITransform;Ljava/lang/Runnable;)V")
	private void lobecorp$recordTransform(ITransform transform, Runnable onTransformUpdated, Operation<Void> original) {
		var workspace = PhotonAnimationEditingUtil.workspace((SceneEditor) (Object) this);
		if (workspace == null) {
			original.call(transform, onTransformUpdated);
			return;
		}

		workspace.registerSceneHistorySource(transform);
		original.call(transform, (Runnable) () -> {
			if (onTransformUpdated != null) {
				onTransformUpdated.run();
			}

			workspace.sceneBaseChanged(transform);
		});
	}
}
