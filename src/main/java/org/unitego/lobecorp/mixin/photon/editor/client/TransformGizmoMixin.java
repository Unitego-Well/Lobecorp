package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.SceneEditor;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.SceneObject;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.utils.TransformGizmo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.model.project.PhotonModelProjectEditor;

/// 模型平移由模型单位吸附处理，保留原生旋转、缩放与特效工程的快捷键。
@Mixin(TransformGizmo.class)
public abstract class TransformGizmoMixin extends SceneObject {
	@WrapOperation(method = "applyDrag", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;isControlDown()Z"), require = 1)
	private boolean lobecorp$modelTranslationSnap(Operation<Boolean> original) {
		if (((TransformGizmo) (Object) this).getMode() == TransformGizmo.Mode.TRANSLATE
				&& getScene() instanceof SceneEditor editor
				&& editor.getFirstAncestorOfType(PhotonModelProjectEditor.class) != null) {
			return false;
		}

		return original.call();
	}
}
