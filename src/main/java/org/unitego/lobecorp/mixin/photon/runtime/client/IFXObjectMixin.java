package org.unitego.lobecorp.mixin.photon.runtime.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.editor.PhotonEditorTools;
import org.unitego.lobecorp.client.photon.runtime.PhotonActivationOverrideAccess;

@Mixin(IFXObject.class)
public interface IFXObjectMixin {
	@WrapOperation(method = "isActive", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/IFXObject;isActive()Z"))
	private boolean lobecorp$independentActivation(IFXObject parent, Operation<Boolean> original) {
		return this instanceof PhotonActivationOverrideAccess access && access.lobecorp$hasOwnActivation()
				|| original.call(parent);
	}

	@WrapOperation(method = "isVisible", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/photon/client/gameobject/IFXObject;isVisible()Z"))
	private boolean lobecorp$independentVisibility(IFXObject parent, Operation<Boolean> original) {
		if (!(this instanceof PhotonActivationOverrideAccess access) || !access.lobecorp$hasOwnActivation()) {
			return original.call(parent);
		}
		for (var transform = parent.transform(); transform != null; transform = transform.parent()) {
			if (transform.sceneObject() instanceof IFXObject ancestor && !ancestor.isSelfVisible())
				return false;
		}
		return true;
	}

	@ModifyReturnValue(method = "isVisible", at = @At("RETURN"))
	private boolean lobecorp$previewVisibility(boolean original) {
		return original && PhotonEditorTools.visible((IFXObject) this);
	}
}
