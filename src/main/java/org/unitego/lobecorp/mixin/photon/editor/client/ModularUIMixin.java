package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.unitego.lobecorp.client.photon.editor.PhotonEffectEditor;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorSaveUtil;
@Mixin(ModularUI.class)
public abstract class ModularUIMixin {
	@WrapOperation(method = "refreshHoveredElement(FF)V", at = @At(value = "INVOKE",
			target = "Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;removeClass(Ljava/lang/String;)Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;"))
	private UIElement lobecorp$keepSharedHover(UIElement element, String clazz, Operation<UIElement> original,
	                                           @Local(name = "newHoveredElement") @Nullable UIElement newHoveredElement) {
		if (!"__hovered__".equals(clazz) || newHoveredElement == null
				|| !newHoveredElement.getStructurePath().contains(element)) {
			return original.call(element, clazz);
		}

		var editor = PhotonEditorSaveUtil.currentProjectEditor((ModularUI) (Object) this, newHoveredElement);
		if (!(editor instanceof PhotonEffectEditor)) {
			return original.call(element, clazz);
		}

		return element;
	}
}
