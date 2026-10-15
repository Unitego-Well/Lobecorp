package org.unitego.lobecorp.mixin.photon.editor.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.unitego.lobecorp.util.photon.editor.PhotonEditorNotificationUtil;

@Mixin(Dialog.class)
public abstract class DialogMixin {
	@Unique
	private boolean lobecorp$timedNotification;

	@Inject(method = "showNotification(Ljava/lang/String;F)Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;", at = @At("RETURN"))
	private static void lobecorp$markTimed(String info, float duration, CallbackInfoReturnable<Dialog> cir) {
		((DialogMixin) (Object) cir.getReturnValue()).lobecorp$timedNotification = true;
	}

	@WrapMethod(method = "show(Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;)Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;")
	private Dialog lobecorp$stackNotifications(UIElement parent, Operation<Dialog> original) {
		if (!lobecorp$timedNotification) {
			return original.call(parent);
		}

		var dialog = (Dialog) (Object) this;
		var actualParent = PhotonEditorNotificationUtil.parent(dialog, parent);
		if (actualParent == null) {
			return original.call(parent);
		}

		if (dialog.getParent() == actualParent) {
			return dialog;
		}

		return original.call(actualParent);
	}

	@WrapOperation(method = "show(Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;)Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;",
			at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib2/gui/ui/elements/Dialog;focus()V"))
	private void lobecorp$preserveNotificationFocus(Dialog dialog, Operation<Void> original) {
		if (lobecorp$timedNotification && PhotonEditorNotificationUtil.isNotificationStack(dialog.getParent())) {
			return;
		}

		original.call(dialog);
	}

	@WrapMethod(method = "close")
	private void lobecorp$removeEmptyStack(Operation<Void> original) {
		var parent = ((Dialog) (Object) this).getParent();
		original.call();
		PhotonEditorNotificationUtil.removeEmptyStack(parent);
	}
}
